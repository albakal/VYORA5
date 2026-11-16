package com.example

import android.net.Uri
import com.example.data.remote.SupabaseClient
import com.example.ui.auth.DEFAULT_GOOGLE_WEB_CLIENT_ID
import com.example.ui.auth.isGoogleSignInConfigured
import com.example.ui.auth.resolveGoogleWebClientId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OAuthConfigAndPkceTest {

    @Test
    fun `google web client id resolves configured value or falls back to project client id`() {
        assertEquals(
            "10645768320-m2fq6i9v657hk9kgv0of5icpst3b2mkd.apps.googleusercontent.com",
            DEFAULT_GOOGLE_WEB_CLIENT_ID
        )
        assertEquals(DEFAULT_GOOGLE_WEB_CLIENT_ID, resolveGoogleWebClientId(""))
        assertEquals(DEFAULT_GOOGLE_WEB_CLIENT_ID, resolveGoogleWebClientId("YOUR_GOOGLE_WEB_CLIENT_ID"))
        assertEquals("custom-id.apps.googleusercontent.com", resolveGoogleWebClientId("  custom-id.apps.googleusercontent.com  "))
        assertTrue(isGoogleSignInConfigured())
    }

    @Test
    fun `pkce verifier and s256 challenge conform to rfc7636`() {
        val verifier1 = SupabaseClient.generatePkceCodeVerifier()
        val verifier2 = SupabaseClient.generatePkceCodeVerifier()
        assertTrue(verifier1.length in 43..128)
        assertFalse(verifier1.contains("="))
        assertFalse(verifier1.contains("+"))
        assertFalse(verifier1.contains("/"))
        assertNotEquals(verifier1, verifier2)

        val challenge = SupabaseClient.computePkceCodeChallenge(verifier1)
        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest(verifier1.toByteArray(Charsets.US_ASCII))
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedDigest)
        assertEquals(expected, challenge)
    }

    @Test
    fun `google oauth authorize url uses supabase callback base and vyora deep link`() {
        val url = SupabaseClient.buildGoogleOAuthAuthorizeUrl(
            baseUrl = "https://rqvngettikcgwigngdlo.supabase.co/",
            codeChallenge = "abcChallenge123"
        )
        assertTrue(url.startsWith("https://rqvngettikcgwigngdlo.supabase.co/auth/v1/authorize?"))
        assertTrue(url.contains("provider=google"))
        assertTrue(url.contains("redirect_to=vyora%3A%2F%2Fauth-callback"))
        assertTrue(url.contains("code_challenge=abcChallenge123"))
        assertTrue(url.contains("code_challenge_method=s256"))
    }

    @Test
    fun `oauth callback uri detection and param parsing handle pkce implicit and error callbacks`() {
        assertTrue(SupabaseClient.isOAuthCallbackUri(Uri.parse("vyora://auth-callback?code=auth_123")))
        assertFalse(SupabaseClient.isOAuthCallbackUri(Uri.parse("https://rqvngettikcgwigngdlo.supabase.co/auth/v1/callback")))
        assertFalse(SupabaseClient.isOAuthCallbackUri(null))

        val pkceUri = Uri.parse("vyora://auth-callback?code=pkce_code_999&state=xyz")
        val pkceParams = SupabaseClient.parseOAuthCallbackParams(pkceUri.encodedQuery, pkceUri.encodedFragment)
        assertEquals("pkce_code_999", pkceParams["code"])

        val errUri = Uri.parse("vyora://auth-callback?error=access_denied&error_description=User+cancelled+login")
        val errParams = SupabaseClient.parseOAuthCallbackParams(errUri.encodedQuery, errUri.encodedFragment)
        assertEquals("access_denied", errParams["error"])
        assertEquals("User cancelled login", errParams["error_description"])

        val implicitUri = Uri.parse("vyora://auth-callback#access_token=jwt.abc.def&refresh_token=ref123&expires_in=3600")
        val implicitParams = SupabaseClient.parseOAuthCallbackParams(implicitUri.encodedQuery, implicitUri.encodedFragment)
        assertEquals("jwt.abc.def", implicitParams["access_token"])
        assertEquals("ref123", implicitParams["refresh_token"])
        assertEquals("3600", implicitParams["expires_in"])
    }
}
