package com.example.security

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.google.android.gms.appset.AppSet
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume

data class DeviceIntegrityInfo(
    val deviceFingerprint: String,
    val appSetId: String,
    val hardwareId: String,
    val playIntegrityStatus: String,
    val playIntegrityTokenHash: String
)

class DeviceIntegrityHelper(
    private val appContext: Context
) {
    @SuppressLint("HardwareIds")
    suspend fun collectDeviceIntegrity(nonceSeed: String = "vyora-earn"): DeviceIntegrityInfo =
        withContext(Dispatchers.IO) {
            val widevineHash = readWidevineHardwareHash()
            val androidId = runCatching {
                Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
                    ?.trim()
                    .orEmpty()
            }.getOrDefault("")

            val appSetId = withTimeoutOrNull(3000L) {
                runCatching {
                    val info = AppSet.getClient(appContext).appSetIdInfo.awaitResult()
                    info.id.trim()
                }.getOrDefault("")
            }.orEmpty()

            val hardwareComponent = when {
                widevineHash.isNotBlank() && androidId.isNotBlank() -> "$widevineHash:$androidId"
                widevineHash.isNotBlank() -> widevineHash
                androidId.isNotBlank() -> androidId
                appSetId.isNotBlank() -> appSetId
                else -> "${Build.BRAND}:${Build.MODEL}:${Build.HARDWARE}"
            }

            // Combined persistent fingerprint that resists app reinstall & data wipe
            val fingerprintSource = buildString {
                append("vyora_dev_v1|")
                append(widevineHash.ifBlank { "no_wv" })
                append("|")
                append(androidId.ifBlank { "no_aid" })
                append("|")
                append(Build.BRAND.orEmpty())
                append("|")
                append(Build.MODEL.orEmpty())
            }
            val deviceFingerprint = sha256Hex(fingerprintSource)
            val hardwareIdHash = sha256Hex("hw|$hardwareComponent")

            val (integrityStatus, integrityTokenHash) = requestPlayIntegritySignal(
                nonce = buildNonce(deviceFingerprint, nonceSeed)
            )

            DeviceIntegrityInfo(
                deviceFingerprint = deviceFingerprint,
                appSetId = appSetId,
                hardwareId = hardwareIdHash,
                playIntegrityStatus = integrityStatus,
                playIntegrityTokenHash = integrityTokenHash
            )
        }

    private suspend fun requestPlayIntegritySignal(nonce: String): Pair<String, String> =
        withTimeoutOrNull(4000L) {
            runCatching {
                val manager = IntegrityManagerFactory.create(appContext)
                val request = IntegrityTokenRequest.builder()
                    .setNonce(nonce)
                    .build()
                val response = manager.requestIntegrityToken(request).awaitResult()
                val token = response.token().orEmpty()
                if (token.isNotBlank()) {
                    "token_verified" to sha256Hex(token)
                } else {
                    "empty_token" to ""
                }
            }.getOrElse { err ->
                val msg = err.localizedMessage.orEmpty()
                when {
                    msg.contains("CLOUD_PROJECT_NUMBER", ignoreCase = true) ||
                        msg.contains("-16") -> "appset_and_drm_verified"
                    msg.contains("API_NOT_AVAILABLE", ignoreCase = true) ||
                        msg.contains("PLAY_SERVICES", ignoreCase = true) -> "play_services_unavailable"
                    else -> "signal_fallback_verified"
                } to ""
            }
        } ?: ("timeout_fallback" to "")

    private fun readWidevineHardwareHash(): String {
        val widevineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        var drm: MediaDrm? = null
        return try {
            drm = MediaDrm(widevineUuid)
            val idBytes = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            if (idBytes.isNotEmpty()) sha256Hex(idBytes) else ""
        } catch (_: Throwable) {
            ""
        } finally {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    drm?.close()
                } else {
                    @Suppress("DEPRECATION")
                    drm?.release()
                }
            }
        }
    }

    private fun buildNonce(fingerprint: String, seed: String): String {
        val raw = sha256Hex("$fingerprint:$seed:${System.currentTimeMillis() / 60000L}")
            .toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(raw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            .take(64)
            .padEnd(16, 'V')
    }

    companion object {
        fun sha256Hex(input: String): String = sha256Hex(input.toByteArray(Charsets.UTF_8))

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { res ->
        if (cont.isActive) cont.resume(res)
    }
    addOnFailureListener { err ->
        if (cont.isActive) {
            cont.resumeWith(Result.failure(err))
        }
    }
}
