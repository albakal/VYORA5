package com.example.data.remote

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val Context.supabaseSessionStore by preferencesDataStore(name = "vyora_supabase_session")

data class SupabaseUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String,
    val isEmailVerified: Boolean
)

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val user: SupabaseUser
)

class SupabaseException(
    val statusCode: Int,
    override val message: String
) : IllegalStateException(message)

class SupabaseClient private constructor(
    private val appContext: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()
) {
    private val keyAccessToken = stringPreferencesKey("access_token")
    private val keyRefreshToken = stringPreferencesKey("refresh_token")
    private val keyExpiresAt = longPreferencesKey("expires_at")
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyEmail = stringPreferencesKey("email")
    private val keyDisplayName = stringPreferencesKey("display_name")
    private val keyAvatarUrl = stringPreferencesKey("avatar_url")

    private val sessionMutex = Mutex()
    private var cachedSession: SupabaseSession? = null

    private val _authState = MutableStateFlow<SupabaseUser?>(null)
    val authState: StateFlow<SupabaseUser?> = _authState.asStateFlow()

    private val invalidationBus = MutableSharedFlow<String>(extraBufferCapacity = 64)

    val currentUser: SupabaseUser?
        get() = _authState.value

    val baseUrl: String
        get() = BuildConfig.SUPABASE_URL.trim().trimEnd('/')

    val publishableKey: String
        get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    val storageBucket: String
        get() = BuildConfig.SUPABASE_STORAGE_BUCKET.trim().ifBlank { "vyora-media" }

    val isConfigured: Boolean
        get() = isConfigValid(baseUrl, publishableKey)

    fun notifyChange(vararg topics: String) {
        if (topics.isEmpty()) {
            invalidationBus.tryEmit("*")
        } else {
            topics.forEach { invalidationBus.tryEmit(it) }
        }
    }

    fun observeTopic(topic: String, intervalMs: Long = 4500L): Flow<Unit> = flow {
        emit(Unit)
        val tickerFlow = flow {
            while (true) {
                delay(intervalMs)
                emit(Unit)
            }
        }
        val eventFlow = invalidationBus
            .filter { it == "*" || it == topic }
            .let { filtered ->
                kotlinx.coroutines.flow.flow {
                    filtered.collect { emit(Unit) }
                }
            }
        merge(tickerFlow, eventFlow).collect {
            emit(Unit)
        }
    }

    suspend fun restoreSession(): SupabaseUser? = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            if (cachedSession != null) {
                return@withLock cachedSession?.user
            }
            val prefs = appContext.supabaseSessionStore.data.first()
            val token = prefs[keyAccessToken].orEmpty()
            val refresh = prefs[keyRefreshToken].orEmpty()
            val expiresAt = prefs[keyExpiresAt] ?: 0L
            val uid = prefs[keyUserId].orEmpty()
            if (token.isBlank() || uid.isBlank()) {
                _authState.value = null
                return@withLock null
            }
            val user = SupabaseUser(
                uid = uid,
                email = prefs[keyEmail].orEmpty(),
                displayName = prefs[keyDisplayName].orEmpty(),
                avatarUrl = prefs[keyAvatarUrl].orEmpty(),
                isEmailVerified = true
            )
            cachedSession = SupabaseSession(
                accessToken = token,
                refreshToken = refresh,
                expiresAtEpochSec = expiresAt,
                user = user
            )
            _authState.value = user
            val nowSec = System.currentTimeMillis() / 1000L
            if (expiresAt > 0L && nowSec >= expiresAt - 60L && refresh.isNotBlank() && isConfigured) {
                runCatching { refreshSessionInternal(refresh) }
            }
            cachedSession?.user
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String = ""
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                if (displayName.isNotBlank()) {
                    put("data", JSONObject().put("full_name", displayName.trim()))
                }
            }
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/signup")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
                }
                val json = JSONObject(raw)
                val accessToken = json.optString("access_token")
                if (accessToken.isBlank()) {
                    throw SupabaseException(
                        202,
                        "Account created! Please confirm your email in your inbox, then sign in."
                    )
                }
                val session = parseSessionFromAuthJson(json, fallbackDisplayName = displayName)
                saveSession(session)
                session.user
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun signInWithEmail(
        email: String,
        password: String
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val bodyJson = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=password")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
                }
                val session = parseSessionFromAuthJson(JSONObject(raw))
                saveSession(session)
                session.user
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun signInWithGoogleIdToken(
        idToken: String,
        rawNonce: String? = null
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val bodyJson = JSONObject().apply {
                put("provider", "google")
                put("id_token", idToken)
                if (!rawNonce.isNullOrBlank()) {
                    put("nonce", rawNonce)
                }
            }
            val req = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=id_token")
                .addHeader("apikey", publishableKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
                }
                val session = parseSessionFromAuthJson(JSONObject(raw))
                saveSession(session)
                session.user
            }
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val token = cachedSession?.accessToken
        if (isConfigured && !token.isNullOrBlank()) {
            runCatching {
                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/logout")
                    .addHeader("apikey", publishableKey)
                    .addHeader("Authorization", "Bearer $token")
                    .post(ByteArray(0).toRequestBody(null))
                    .build()
                httpClient.newCall(req).execute().close()
            }
        }
        clearSession()
    }

    suspend fun select(table: String, query: String): JSONArray = withContext(Dispatchers.IO) {
        requireConfigured()
        val authToken = resolveBearerToken()
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$table?$query")
            .addHeader("apikey", publishableKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Accept", "application/json")
            .get()
            .build()

        executeForJsonArray(req)
    }

    suspend fun insert(
        table: String,
        body: JSONObject,
        upsert: Boolean = false,
        onConflict: String? = null
    ): JSONArray = withContext(Dispatchers.IO) {
        requireConfigured()
        val authToken = requireUserBearerToken()
        val url = buildString {
            append("$baseUrl/rest/v1/$table")
            if (upsert && !onConflict.isNullOrBlank()) {
                append("?on_conflict=${URLEncoder.encode(onConflict, "UTF-8")}")
            }
        }
        val prefer = buildString {
            append("return=representation")
            if (upsert) append(",resolution=merge-duplicates")
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("apikey", publishableKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", prefer)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        executeForJsonArray(req)
    }

    suspend fun update(
        table: String,
        filterQuery: String,
        body: JSONObject
    ): JSONArray = withContext(Dispatchers.IO) {
        requireConfigured()
        val authToken = requireUserBearerToken()
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$table?$filterQuery")
            .addHeader("apikey", publishableKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .patch(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        executeForJsonArray(req)
    }

    suspend fun delete(table: String, filterQuery: String) = withContext(Dispatchers.IO) {
        requireConfigured()
        val authToken = requireUserBearerToken()
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/$table?$filterQuery")
            .addHeader("apikey", publishableKey)
            .addHeader("Authorization", "Bearer $authToken")
            .delete()
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
            }
        }
    }

    suspend fun rpc(functionName: String, params: JSONObject = JSONObject()): String = withContext(Dispatchers.IO) {
        requireConfigured()
        val authToken = requireUserBearerToken()
        val req = Request.Builder()
            .url("$baseUrl/rest/v1/rpc/$functionName")
            .addHeader("apikey", publishableKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(params.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
            }
            raw
        }
    }

    suspend fun invokeEdgeFunction(functionName: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        requireConfigured()
        val authToken = requireUserBearerToken()
        val req = Request.Builder()
            .url("$baseUrl/functions/v1/$functionName")
            .addHeader("apikey", publishableKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
            }
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    suspend fun uploadStorageObject(
        objectPath: String,
        bytes: ByteArray,
        contentType: String
    ): String = withContext(Dispatchers.IO) {
        requireConfigured()
        val authToken = requireUserBearerToken()
        val cleanPath = encodeStoragePath(objectPath)
        val req = Request.Builder()
            .url("$baseUrl/storage/v1/object/$storageBucket/$cleanPath")
            .addHeader("apikey", publishableKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("x-upsert", "true")
            .addHeader("Content-Type", contentType)
            .post(bytes.toRequestBody(contentType.toMediaType()))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
            }
            "$baseUrl/storage/v1/object/public/$storageBucket/$cleanPath"
        }
    }

    suspend fun uploadStorageStream(
        objectPath: String,
        contentLength: Long,
        contentType: String,
        openStream: () -> InputStream?
    ): String = withContext(Dispatchers.IO) {
        requireConfigured()
        val authToken = requireUserBearerToken()
        val cleanPath = encodeStoragePath(objectPath)
        val streamBody = object : RequestBody() {
            override fun contentType() = contentType.toMediaType()
            override fun contentLength(): Long = if (contentLength > 0L) contentLength else -1L
            override fun writeTo(sink: BufferedSink) {
                val input = openStream() ?: throw IOException("Unable to open media stream for upload")
                input.use { stream ->
                    val out = sink.outputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
            }
        }
        val req = Request.Builder()
            .url("$baseUrl/storage/v1/object/$storageBucket/$cleanPath")
            .addHeader("apikey", publishableKey)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("x-upsert", "true")
            .addHeader("Content-Type", contentType)
            .post(streamBody)
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
            }
            "$baseUrl/storage/v1/object/public/$storageBucket/$cleanPath"
        }
    }

    private fun encodeStoragePath(objectPath: String): String =
        objectPath.trimStart('/')
            .split("/")
            .filter { it.isNotBlank() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }

    private suspend fun resolveBearerToken(): String {
        val current = ensureFreshSession()
        return current?.accessToken?.takeIf { it.isNotBlank() } ?: publishableKey
    }

    private suspend fun requireUserBearerToken(): String {
        val current = ensureFreshSession()
            ?: throw SupabaseException(401, "User must be signed in.")
        return current.accessToken
    }

    private suspend fun ensureFreshSession(): SupabaseSession? = sessionMutex.withLock {
        if (cachedSession == null) {
            restoreSessionUnlocked()
        }
        val current = cachedSession ?: return@withLock null
        val nowSec = System.currentTimeMillis() / 1000L
        if (current.expiresAtEpochSec > 0L && nowSec >= current.expiresAtEpochSec - 45L && current.refreshToken.isNotBlank()) {
            runCatching { refreshSessionInternal(current.refreshToken) }
        }
        cachedSession
    }

    private suspend fun restoreSessionUnlocked() {
        val prefs = appContext.supabaseSessionStore.data.first()
        val token = prefs[keyAccessToken].orEmpty()
        val refresh = prefs[keyRefreshToken].orEmpty()
        val expiresAt = prefs[keyExpiresAt] ?: 0L
        val uid = prefs[keyUserId].orEmpty()
        if (token.isNotBlank() && uid.isNotBlank()) {
            val user = SupabaseUser(
                uid = uid,
                email = prefs[keyEmail].orEmpty(),
                displayName = prefs[keyDisplayName].orEmpty(),
                avatarUrl = prefs[keyAvatarUrl].orEmpty(),
                isEmailVerified = true
            )
            cachedSession = SupabaseSession(token, refresh, expiresAt, user)
            _authState.value = user
        }
    }

    private suspend fun refreshSessionInternal(refreshToken: String) {
        val bodyJson = JSONObject().put("refresh_token", refreshToken)
        val req = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
            .addHeader("apikey", publishableKey)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val updated = parseSessionFromAuthJson(JSONObject(raw))
                saveSessionUnlocked(updated)
            } else if (resp.code == 400 || resp.code == 401) {
                clearSessionUnlocked()
            }
        }
    }

    private suspend fun saveSession(session: SupabaseSession) = sessionMutex.withLock {
        saveSessionUnlocked(session)
    }

    private suspend fun saveSessionUnlocked(session: SupabaseSession) {
        cachedSession = session
        _authState.value = session.user
        appContext.supabaseSessionStore.edit { prefs ->
            prefs[keyAccessToken] = session.accessToken
            prefs[keyRefreshToken] = session.refreshToken
            prefs[keyExpiresAt] = session.expiresAtEpochSec
            prefs[keyUserId] = session.user.uid
            prefs[keyEmail] = session.user.email
            prefs[keyDisplayName] = session.user.displayName
            prefs[keyAvatarUrl] = session.user.avatarUrl
        }
    }

    private suspend fun clearSession() = sessionMutex.withLock {
        clearSessionUnlocked()
    }

    private suspend fun clearSessionUnlocked() {
        cachedSession = null
        _authState.value = null
        appContext.supabaseSessionStore.edit { it.clear() }
    }

    private fun executeForJsonArray(req: Request): JSONArray {
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw SupabaseException(resp.code, parseSupabaseError(raw, resp.code))
            }
            val trimmed = raw.trim()
            return if (trimmed.isEmpty()) JSONArray() else JSONArray(trimmed)
        }
    }

    private fun parseSessionFromAuthJson(
        json: JSONObject,
        fallbackDisplayName: String = ""
    ): SupabaseSession {
        val accessToken = json.optString("access_token")
        val refreshToken = json.optString("refresh_token")
        val expiresIn = json.optLong("expires_in", 3600L)
        val expiresAt = json.optLong("expires_at", (System.currentTimeMillis() / 1000L) + expiresIn)
        val userObj = json.optJSONObject("user") ?: JSONObject()
        val metadata = userObj.optJSONObject("user_metadata") ?: JSONObject()
        val email = userObj.optString("email")
        val displayName = metadata.optString("full_name")
            .ifBlank { metadata.optString("name") }
            .ifBlank { fallbackDisplayName }
            .ifBlank { email.substringBefore("@") }
        val avatarUrl = metadata.optString("avatar_url")
            .ifBlank { metadata.optString("picture") }
        val verified = userObj.optString("email_confirmed_at").isNotBlank() ||
            userObj.optString("confirmed_at").isNotBlank()
        val user = SupabaseUser(
            uid = userObj.optString("id"),
            email = email,
            displayName = displayName,
            avatarUrl = avatarUrl,
            isEmailVerified = verified
        )
        return SupabaseSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSec = expiresAt,
            user = user
        )
    }

    private fun requireConfigured() {
        if (!isConfigured) {
            throw SupabaseException(
                412,
                "Supabase is not configured. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in AI Studio Secrets."
            )
        }
    }

    private fun parseSupabaseError(raw: String, code: Int): String {
        if (raw.isBlank()) return "HTTP $code"
        return try {
            val obj = JSONObject(raw)
            obj.optString("msg")
                .ifBlank { obj.optString("error_description") }
                .ifBlank { obj.optString("message") }
                .ifBlank { obj.optString("error") }
                .ifBlank { "HTTP $code" }
        } catch (_: Exception) {
            raw.take(200)
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: SupabaseClient? = null

        fun getInstance(context: Context): SupabaseClient {
            return instance ?: synchronized(this) {
                instance ?: SupabaseClient(context.applicationContext).also { instance = it }
            }
        }

        fun currentInstanceOrNull(): SupabaseClient? = instance

        fun isConfigValid(url: String, key: String): Boolean {
            val cleanUrl = url.trim().trimEnd('/')
            val cleanKey = key.trim()
            return cleanUrl.startsWith("https://") &&
                !cleanUrl.contains("YOUR_PROJECT_REF") &&
                cleanKey.isNotBlank() &&
                !cleanKey.contains("YOUR_SUPABASE")
        }

        fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
