package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AdminSettings
import com.example.data.model.UserProfile
import com.example.data.model.VideoPost
import com.example.data.model.Wallet
import com.example.earn.EarnLogic
import com.example.media.ApkExportHelper
import com.example.media.formatCompactCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(context.getString(R.string.firebase_ledger_title).contains("Firebase"))
        assertTrue(context.getString(R.string.firebase_auth_ready).contains("Firebase"))
        assertTrue(context.getString(R.string.sign_in_google).contains("Google"))
        assertTrue(context.getString(R.string.save_apk_button).contains("APK"))
        assertFalse(context.getString(R.string.firestore_database_id).isBlank())
    }

    @Test
    fun `arabic localization includes firebase auth ledger and apk strings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val arLocale = Locale.forLanguageTag("ar")
        val config = Configuration(context.resources.configuration).apply {
            setLocale(arLocale)
            setLayoutDirection(arLocale)
        }
        val arRes = context.createConfigurationContext(config).resources
        assertTrue(arRes.getString(R.string.firebase_ledger_title).contains("Firebase"))
        assertTrue(arRes.getString(R.string.firebase_auth_ready).contains("Firebase"))
        assertTrue(arRes.getString(R.string.sign_in_google).contains("جوجل"))
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
    fun `firestore models instantiate with expected defaults`() {
        val profile = UserProfile(userId = "usr-123", username = "vyora_star", followersCount = 42L)
        assertEquals("usr-123", profile.userId)
        assertEquals("vyora_star", profile.username)
        assertEquals(42L, profile.followersCount)
        assertFalse(profile.isPrivate)
        assertFalse(profile.isBanned)

        val video = VideoPost(
            id = "vid-99",
            userId = "usr-123",
            creatorUsername = "vyora_star",
            videoUrl = "https://firebasestorage.googleapis.com/v0/b/demo/o/video.mp4",
            viewsCount = 500L,
            likesCount = 25L
        )
        assertEquals("vid-99", video.id)
        assertEquals("active", video.status)
        assertEquals(500L, video.viewsCount)
        assertEquals(25L, video.likesCount)

        val wallet = Wallet(userId = "usr-123", points = 1200L, balanceUsd = 12.0)
        assertEquals(1200L, wallet.points)
        assertEquals(12.0, wallet.balanceUsd, 0.001)

        val settings = AdminSettings(id = "global")
        assertTrue(settings.watchWheelEnabled)
        assertEquals(30L, settings.watchWheelCycleSec)
    }

    @Test
    fun `referral invite uri builder and extractor support https and custom scheme links`() {
        val httpsLink = EarnLogic.buildReferralInviteUri("vy12ab34")
        assertEquals("https://vyora.app/invite?ref=VY12AB34", httpsLink)
        assertEquals("VY12AB34", EarnLogic.extractReferralCodeFromUri(android.net.Uri.parse(httpsLink)))

        val schemeLink = EarnLogic.buildCustomSchemeInviteUri("vy998877")
        assertEquals("vyora://invite?ref=VY998877", schemeLink)
        assertEquals("VY998877", EarnLogic.extractReferralCodeFromUri(android.net.Uri.parse(schemeLink)))

        assertEquals("", EarnLogic.extractReferralCodeFromUri(android.net.Uri.parse("https://other.site/invite?ref=VY12AB34")))
    }
}
