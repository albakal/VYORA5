package com.example.ui.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.BuildConfig
import com.example.R
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

const val DEFAULT_GOOGLE_WEB_CLIENT_ID =
    "10645768320-m2fq6i9v657hk9kgv0of5icpst3b2mkd.apps.googleusercontent.com"

fun resolveGoogleWebClientId(
    configuredValue: String = BuildConfig.SUPABASE_GOOGLE_WEB_CLIENT_ID
): String {
    val cleaned = configuredValue.trim()
    return if (cleaned.isBlank() || cleaned.contains("YOUR_GOOGLE_WEB_CLIENT_ID", ignoreCase = true)) {
        DEFAULT_GOOGLE_WEB_CLIENT_ID
    } else {
        cleaned
    }
}

fun isGoogleSignInConfigured(): Boolean = resolveGoogleWebClientId().isNotBlank()

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

fun launchGoogleBrowserOAuth(
    context: Context,
    scope: CoroutineScope,
    onLaunched: () -> Unit,
    onAuthError: (String) -> Unit
) {
    val supabase = SupabaseClient.getInstance(context)
    if (!supabase.isConfigured) {
        onAuthError(context.getString(R.string.supabase_keys_required_error))
        return
    }
    scope.launch {
        launchGoogleBrowserOAuthSuspend(
            context = context,
            supabase = supabase,
            onLaunched = onLaunched,
            onAuthError = onAuthError
        )
    }
}

private suspend fun launchGoogleBrowserOAuthSuspend(
    context: Context,
    supabase: SupabaseClient,
    onLaunched: () -> Unit,
    onAuthError: (String) -> Unit
) {
    try {
        val authUrl = supabase.prepareGoogleOAuthUrl()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        onLaunched()
    } catch (e: ActivityNotFoundException) {
        Log.e("Auth", "No browser app found for OAuth", e)
        onAuthError(context.getString(R.string.auth_no_browser_error))
    } catch (e: Exception) {
        Log.e("Auth", "Browser Google OAuth launch failed", e)
        onAuthError(e.localizedMessage ?: context.getString(R.string.oauth_callback_failed))
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit,
    onBrowserFlowStarted: () -> Unit = {},
    scope: CoroutineScope
) {
    val supabase = SupabaseClient.getInstance(context)
    if (!supabase.isConfigured) {
        onAuthError(context.getString(R.string.supabase_keys_required_error))
        return
    }
    val clientId = resolveGoogleWebClientId()
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
                launchGoogleBrowserOAuthSuspend(
                    context = context,
                    supabase = supabase,
                    onLaunched = onBrowserFlowStarted,
                    onAuthError = onAuthError
                )
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w("Auth", "Credential Manager has no credential; falling back to Browser OAuth", e)
            launchGoogleBrowserOAuthSuspend(
                context = context,
                supabase = supabase,
                onLaunched = onBrowserFlowStarted,
                onAuthError = onAuthError
            )
        } catch (e: GetCredentialException) {
            Log.w("Auth", "Credential Manager unavailable (${e.type}); falling back to Browser OAuth", e)
            launchGoogleBrowserOAuthSuspend(
                context = context,
                supabase = supabase,
                onLaunched = onBrowserFlowStarted,
                onAuthError = onAuthError
            )
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
