package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.data.remote.SupabaseClient
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

fun isGoogleSignInConfigured(): Boolean =
    BuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID.trim().isNotBlank() &&
        !BuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID.contains("YOUR_GOOGLE_WEB_CLIENT_ID")

fun restoreSupabaseSession(
    context: Context,
    scope: CoroutineScope,
    onRestored: () -> Unit = {},
    onUnauthenticated: () -> Unit = {}
) {
    val supabase = SupabaseClient.getInstance(context)
    scope.launch {
        val user = supabase.restoreSession()
        if (user != null) {
            onRestored()
        } else {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit,
    scope: CoroutineScope
) {
    val supabase = SupabaseClient.getInstance(context)
    if (!supabase.isConfigured) {
        onAuthError(context.getString(com.example.R.string.supabase_keys_required_error))
        return
    }
    if (!isGoogleSignInConfigured()) {
        onAuthError(context.getString(com.example.R.string.gmail_client_id_missing))
        return
    }
    val clientId = BuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID.trim()
    val rawNonce = UUID.randomUUID().toString()
    val hashedNonce = MessageDigest.getInstance("SHA-256")
        .digest(rawNonce.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
        .setNonce(hashedNonce)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val activityContext = (context as? Activity) ?: context
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                supabase.signInWithGoogleIdToken(googleIdToken, rawNonce)
                    .onSuccess { onAuthSuccess() }
                    .onFailure { e -> onAuthError(e.localizedMessage ?: "Supabase sign-in failed") }
            } else {
                onAuthError("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w("Auth", "No Google credentials available: ${e.message}", e)
            onAuthError("No Google account found on this device.")
        } catch (e: GetCredentialException) {
            Log.e("Auth", "Credential Manager error", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

fun signOutVyora(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                SupabaseClient.currentInstanceOrNull()?.signOut()
                onSignOutComplete()
            }
        }
    }
}
