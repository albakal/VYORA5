package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

data class AuthUserInfo(
    val userId: String?,
    val email: String?,
    val emailVerified: Boolean?,
    val isAnonymous: Boolean?,
    val tenantId: String?,
    val providerInfo: List<Map<String, String?>>
)

data class FirestoreErrorInfo(
    val error: String,
    val operationType: OperationType,
    val path: String?,
    val authInfo: AuthUserInfo
) {
    fun toJsonString(): String {
        val providerArray = JSONArray().apply {
            authInfo.providerInfo.forEach { info ->
                put(JSONObject().apply {
                    put("providerId", info["providerId"] ?: JSONObject.NULL)
                    put("displayName", info["displayName"] ?: JSONObject.NULL)
                    put("email", info["email"] ?: JSONObject.NULL)
                    put("photoUrl", info["photoUrl"] ?: JSONObject.NULL)
                })
            }
        }
        val authJson = JSONObject().apply {
            put("userId", authInfo.userId ?: JSONObject.NULL)
            put("email", authInfo.email ?: JSONObject.NULL)
            put("emailVerified", authInfo.emailVerified ?: JSONObject.NULL)
            put("isAnonymous", authInfo.isAnonymous ?: JSONObject.NULL)
            put("tenantId", authInfo.tenantId ?: JSONObject.NULL)
            put("providerInfo", providerArray)
        }
        return JSONObject().apply {
            put("error", error)
            put("operationType", operationType.name.lowercase())
            put("path", path ?: JSONObject.NULL)
            put("authInfo", authJson)
        }.toString()
    }
}

object FirestoreErrorHandler {
    private const val TAG = "FirestoreError"

    fun handleFirestoreError(
        exception: Exception,
        operationType: OperationType,
        path: String?,
        auth: FirebaseAuth? = null
    ) {
        val resolvedAuth = auth ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
            ?: return logWithoutAuth(exception, operationType, path)
        val user = resolvedAuth.currentUser
        val providers = user?.providerData?.map { profile ->
            mapOf(
                "providerId" to profile.providerId,
                "displayName" to profile.displayName,
                "email" to profile.email,
                "photoUrl" to profile.photoUrl?.toString()
            )
        } ?: emptyList()

        val errInfo = FirestoreErrorInfo(
            error = exception.message ?: exception.toString(),
            operationType = operationType,
            path = path,
            authInfo = AuthUserInfo(
                userId = user?.uid,
                email = user?.email,
                emailVerified = user?.isEmailVerified,
                isAnonymous = user?.isAnonymous,
                tenantId = user?.tenantId,
                providerInfo = providers
            )
        )
        Log.e(TAG, "Firestore Error: ${errInfo.toJsonString()}", exception)
    }

    private fun logWithoutAuth(
        exception: Exception,
        operationType: OperationType,
        path: String?
    ) {
        val errInfo = FirestoreErrorInfo(
            error = exception.message ?: exception.toString(),
            operationType = operationType,
            path = path,
            authInfo = AuthUserInfo(
                userId = null,
                email = null,
                emailVerified = null,
                isAnonymous = null,
                tenantId = null,
                providerInfo = emptyList()
            )
        )
        Log.e(TAG, "Firestore Error: ${errInfo.toJsonString()}", exception)
    }
}
