package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.VyoraTimestamp
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SupabaseLedgerClient
import com.example.data.remote.toUserProfile
import com.example.data.remote.toVideoPost
import com.example.data.remote.toWallet
import com.example.media.ApkExportHelper
import com.example.media.formatCompactCount
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VYORA", appName)
        assertTrue(context.getString(R.string.supabase_ledger_title).contains("Supabase"))
        assertTrue(context.getString(R.string.supabase_auth_ready).contains("Supabase"))
        assertTrue(context.getString(R.string.sign_in_google).contains("Gmail"))
        assertTrue(context.getString(R.string.save_apk_button).contains("APK"))
    }

    @Test
    fun `arabic localization includes supabase auth ledger and apk strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val arLocale = Locale.forLanguageTag("ar")
        val config = Configuration(context.resources.configuration).apply {
            setLocale(arLocale)
            setLayoutDirection(arLocale)
        }
        val arRes = context.createConfigurationContext(config).resources
        assertTrue(arRes.getString(R.string.supabase_ledger_title).contains("Supabase"))
        assertTrue(arRes.getString(R.string.supabase_auth_ready).contains("Supabase"))
        assertTrue(arRes.getString(R.string.sign_in_google).contains("Gmail"))
        assertTrue(arRes.getString(R.string.save_apk_button).contains("APK"))
    }

    @Test
    fun `apk export filename and compact counts use stable format`() {
        assertTrue(ApkExportHelper.defaultApkFileName().endsWith(".apk"))
        assertEquals("1.5M", formatCompactCount(1_500_000L))
        assertEquals("2.3K", formatCompactCount(2_300L))
        assertEquals("999", formatCompactCount(999L))
    }

    @Test
    fun `supabase config validation rejects placeholders and accepts real credentials`() {
        assertFalse(
            SupabaseClient.isConfigValid(
                "https://YOUR_PROJECT_REF.supabase.co",
                "YOUR_SUPABASE_PUBLISHABLE_KEY"
            )
        )
        assertFalse(SupabaseClient.isConfigValid("http://insecure.supabase.co", "sb_publishable_123"))
        assertTrue(SupabaseClient.isConfigValid("https://abcdefg.supabase.co", "sb_publishable_valid_key"))
    }

    @Test
    fun `supabase json mappers parse profile video and wallet rows cleanly`() {
        val profileJson = JSONObject().apply {
            put("user_id", "usr-123")
            put("username", "vyora_star")
            put("display_name", "Vyora Star")
            put("followers_count", 42L)
            put("interests", JSONArray(listOf("music", "tech")))
            put("created_at", "2026-01-15T10:30:00Z")
        }
        val profile = profileJson.toUserProfile()
        assertEquals("usr-123", profile.userId)
        assertEquals("vyora_star", profile.username)
        assertEquals(42L, profile.followersCount)
        assertEquals(listOf("music", "tech"), profile.interests)
        assertNotNull(profile.createdAt)

        val videoJson = JSONObject().apply {
            put("video_id", "vid-99")
            put("creator_uid", "usr-123")
            put("creator_username", "vyora_star")
            put("video_url", "https://example.supabase.co/storage/v1/object/public/vyora-media/v.mp4")
            put("views_count", 500L)
            put("likes_count", 25L)
        }
        val video = videoJson.toVideoPost()
        assertEquals("vid-99", video.id)
        assertEquals(500L, video.viewsCount)
        assertEquals(25L, video.likesCount)

        val walletJson = JSONObject().apply {
            put("user_id", "usr-123")
            put("points", 1200L)
            put("balance_usd", 12.0)
            put("total_earned_usd", 18.5)
        }
        val wallet = walletJson.toWallet()
        assertEquals(1200L, wallet.points)
        assertEquals(12.0, wallet.balanceUsd, 0.001)
    }

    @Test
    fun `vyora timestamp parses iso timestamps and orders properly`() {
        val older = VyoraTimestamp.parse("2025-01-01T00:00:00Z")!!
        val newer = VyoraTimestamp.parse("2026-01-01T00:00:00Z")!!
        assertTrue(newer > older)
    }

    @Test
    fun `supabase ledger client safely handles placeholder credentials`() = runBlocking {
        val client = SupabaseLedgerClient()
        if (!client.isClientConfigured) {
            val diag = client.checkPublicCatalogConnectivity()
            assertFalse(diag.clientKeysConfigured)
            assertFalse(diag.restReachable)
        }
    }
}
