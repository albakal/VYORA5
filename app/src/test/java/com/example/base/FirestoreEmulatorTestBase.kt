package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.HttpURLConnection
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

    protected lateinit var firestore: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String

    @Before
    open fun setUpFirebase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        databaseId = if (resId != 0) context.getString(resId) else DEFAULT_NAMED_DB_ID

        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            val newApp = FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.vyora.kxpvqr")
                    .setProjectId(PROJECT_ID)
                    .setApiKey(EMULATOR_API_KEY)
                    .build()
            )
            val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
            newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            newFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()

            val newAuth = FirebaseAuth.getInstance(newApp)
            newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
            newApp
        } else {
            FirebaseApp.getInstance()
        }

        firestore = FirebaseFirestore.getInstance(app, databaseId)
        auth = FirebaseAuth.getInstance(app)
    }

    @After
    open fun tearDownFirebase() {
        auth.signOut()
    }

    protected suspend fun signInTestUser(email: String, markVerified: Boolean = false): String =
        withContext(Dispatchers.IO) {
            withTimeout(AUTH_TIMEOUT_MS) {
                val result = try {
                    auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
                } catch (_: FirebaseAuthInvalidUserException) {
                    auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
                }
                val user = checkNotNull(result.user) { "User auth failed" }
                if (markVerified) {
                    verifyUserInAuthEmulator(user)
                }
                user.uid
            }
        }

    private suspend fun verifyUserInAuthEmulator(user: FirebaseUser) {
        val endpoint = URL("http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/accounts:update?key=$EMULATOR_API_KEY")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 3000
            readTimeout = 3000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer owner")
        }
        val body = JSONObject().apply {
            put("localId", user.uid)
            put("emailVerified", true)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = conn.responseCode
        conn.disconnect()
        check(status in 200..299) { "Failed to mark user emailVerified in Auth emulator: HTTP $status" }
        user.reload().await()
        user.getIdToken(true).await()
    }

    protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is FirebaseFirestoreException) return current
            current = current.cause
        }
        return null
    }

    private companion object {
        const val EMULATOR_HOST = "127.0.0.1"
        const val FIRESTORE_PORT = 8085
        const val AUTH_PORT = 9099
        const val PROJECT_ID = "demo-no-project"
        const val EMULATOR_API_KEY = "fake-api-key-for-emulator"
        const val DEFAULT_PASSWORD = "password123"
        const val AUTH_TIMEOUT_MS = 5000L
        const val DEFAULT_NAMED_DB_ID = "ai-studio-android-vyora-09b7e2be-39f1-49bc-910d-92eb9b4daa3f"
    }
}
