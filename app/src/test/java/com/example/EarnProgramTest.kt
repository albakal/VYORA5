package com.example

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.EarnProgramRepository
import com.example.data.remote.StorageMediaRepository
import com.example.earn.EarnLogic
import com.example.security.DeviceIntegrityHelper
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EarnProgramTest {

    @Test
    fun `cairo timezone computes egypt local date properly`() {
        // 2026-05-01T22:30:00Z in Cairo (UTC+3 during DST or UTC+2 standard) is May 2 in Cairo
        val instant = Instant.parse("2026-05-01T22:30:00Z")
        val cairoDate = EarnLogic.toCairoDate(instant)
        assertEquals(LocalDate.of(2026, 5, 2), cairoDate)
    }

    @Test
    fun `daily qualification requires 30 active minutes and 2 valid videos`() {
        assertFalse(EarnLogic.doesDayQualify(activeSeconds = 1799L, validVideosCount = 2))
        assertFalse(EarnLogic.doesDayQualify(activeSeconds = 1800L, validVideosCount = 1))
        assertTrue(EarnLogic.doesDayQualify(activeSeconds = 1800L, validVideosCount = 2))
        assertTrue(EarnLogic.doesDayQualify(activeSeconds = 2400L, validVideosCount = 5))
    }

    @Test
    fun `consecutive streak counts 10 consecutive days and breaks on missed day without compensation`() {
        val today = LocalDate.of(2026, 6, 15)
        val fullTenDays = (0L..9L).map { today.minusDays(it) }.toSet()
        assertEquals(10, EarnLogic.calculateConsecutiveStreak(fullTenDays, today))

        // Removing a single day in the middle breaks streak (cannot compensate missed day with extra days before)
        val brokenWithExtraDays = fullTenDays - today.minusDays(4) + today.minusDays(10) + today.minusDays(11)
        assertEquals(4, EarnLogic.calculateConsecutiveStreak(brokenWithExtraDays, today))

        // If today hasn't finished yet, streak ending yesterday still holds
        val upToYesterday = (1L..10L).map { today.minusDays(it) }.toSet()
        assertEquals(10, EarnLogic.calculateConsecutiveStreak(upToYesterday, today))

        // Missing both today and yesterday resets current streak to 0
        val twoDaysAgo = (2L..11L).map { today.minusDays(it) }.toSet()
        assertEquals(0, EarnLogic.calculateConsecutiveStreak(twoDaysAgo, today))
    }

    @Test
    fun `progress percent weights personal videos 25 percent and completed friends 75 percent`() {
        assertEquals(0, EarnLogic.calculateOverallProgressPercent(0, 10, 0, 10))
        assertEquals(25, EarnLogic.calculateOverallProgressPercent(10, 10, 0, 10))
        assertEquals(100, EarnLogic.calculateOverallProgressPercent(10, 10, 10, 10))
        // Extra videos beyond 10 don't exceed 100%
        assertEquals(100, EarnLogic.calculateOverallProgressPercent(25, 10, 10, 10))
    }

    @Test
    fun `referral anti-fraud detects self referral, same device, duplicate friend device, and allows unique device`() {
        val self = EarnLogic.evaluateReferralDeviceSafety(
            inviterUid = "u1",
            friendUid = "u1",
            inviterDeviceFingerprint = "fp1",
            friendDeviceFingerprint = "fp2",
            existingSiblingFingerprints = emptySet(),
            friendDeviceEligible = true
        )
        assertFalse(self.allowedToCredit)
        assertTrue(self.shouldFlagForReview)

        val sameAsInviter = EarnLogic.evaluateReferralDeviceSafety(
            inviterUid = "u1",
            friendUid = "u2",
            inviterDeviceFingerprint = "fp_same",
            friendDeviceFingerprint = "fp_same",
            existingSiblingFingerprints = emptySet(),
            friendDeviceEligible = true
        )
        assertFalse(sameAsInviter.allowedToCredit)
        assertEquals("self_device_referral_attempt", sameAsInviter.reason)

        val duplicateFriend = EarnLogic.evaluateReferralDeviceSafety(
            inviterUid = "u1",
            friendUid = "u3",
            inviterDeviceFingerprint = "fp1",
            friendDeviceFingerprint = "fp_friend_dup",
            existingSiblingFingerprints = setOf("fp_friend_dup"),
            friendDeviceEligible = true
        )
        assertFalse(duplicateFriend.allowedToCredit)
        assertEquals("duplicate_friend_device", duplicateFriend.reason)

        val validUnique = EarnLogic.evaluateReferralDeviceSafety(
            inviterUid = "u1",
            friendUid = "u4",
            inviterDeviceFingerprint = "fp1",
            friendDeviceFingerprint = "fp_unique_4",
            existingSiblingFingerprints = setOf("fp_friend_dup"),
            friendDeviceEligible = true
        )
        assertTrue(validUnique.allowedToCredit)
        assertFalse(validUnique.shouldFlagForReview)
    }

    @Test
    fun `sha256 stream digest matches for duplicate content and differs for distinct videos`() {
        val bytesA = "vyora-video-frame-payload-alpha".toByteArray()
        val bytesB = "vyora-video-frame-payload-beta".toByteArray()
        val (hashA1, sizeA1) = EarnLogic.computeStreamSha256AndSize(ByteArrayInputStream(bytesA))
        val (hashA2, sizeA2) = EarnLogic.computeStreamSha256AndSize(ByteArrayInputStream(bytesA))
        val (hashB, _) = EarnLogic.computeStreamSha256AndSize(ByteArrayInputStream(bytesB))

        assertEquals(hashA1, hashA2)
        assertEquals(bytesA.size.toLong(), sizeA1)
        assertEquals(sizeA1, sizeA2)
        assertFalse(hashA1 == hashB)
        assertEquals(64, hashA1.length)
    }

    @Test
    fun `referral code sanitization and payout transaction ref are deterministic`() {
        assertEquals("VY12AB34", EarnLogic.sanitizeReferralCode("  vy12ab34!@# "))
        assertTrue(EarnLogic.isValidReferralCodeFormat("VY12AB34"))
        assertFalse(EarnLogic.isValidReferralCodeFormat("abc"))
        assertEquals("earn_reward:uid_99", EarnLogic.payoutTransactionRef(" uid_99 "))
    }

    @Test
    fun `earn repository parses dashboard and admin overview json including master toggle`() {
        val dashJson = JSONObject().apply {
            put("config", JSONObject().apply {
                put("isEnabled", false)
                put("rewardEgp", 1500.0)
                put("rewardPoints", 150000L)
                put("inviterVideosRequired", 10)
                put("requiredFriends", 10)
                put("friendDaysRequired", 10)
                put("friendDailyMinutesRequired", 30)
                put("friendDailyVideosRequired", 2)
            })
            put("referralCode", "VY998877")
            put("referredByCode", "")
            put("personalVideosCount", 7)
            put("invitedFriendsCount", 3)
            put("completedFriendsCount", 1)
            put("progressPercent", 25.0)
            put("status", "in_progress")
            put("cairoDate", "2026-06-15")
            put("friends", JSONArray().apply {
                put(JSONObject().apply {
                    put("userId", "f1")
                    put("username", "ahmed_dev")
                    put("displayName", "Ahmed")
                    put("completedDays", 10)
                    put("requiredDays", 10)
                    put("todayActiveMinutes", 32)
                    put("todayValidVideos", 3)
                    put("isCompleted", true)
                })
            })
        }
        val dash = EarnProgramRepository.parseDashboardJson(dashJson)
        assertFalse(dash.config.isEnabled)
        assertEquals(1500.0, dash.config.rewardEgp, 0.01)
        assertEquals("VY998877", dash.referralCode)
        assertEquals(7, dash.personalVideosCount)
        assertEquals(1, dash.friends.size)
        assertTrue(dash.friends.first().isCompleted)

        val adminJson = JSONObject().apply {
            put("config", JSONObject().apply { put("isEnabled", true) })
            put("participants", JSONArray().apply {
                put(JSONObject().apply {
                    put("userId", "u1")
                    put("username", "creator1")
                    put("referralCode", "VY112233")
                    put("status", "eligible")
                })
            })
            put("flaggedDevices", JSONArray().apply {
                put(JSONObject().apply {
                    put("deviceFingerprint", "fp_123")
                    put("linkedUserIds", JSONArray(listOf("u1", "u2")))
                    put("isFlagged", true)
                    put("adminDecision", "pending")
                })
            })
            put("payoutLogs", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "log1")
                    put("userId", "u1")
                    put("username", "creator1")
                    put("decision", "approved")
                    put("rewardEgp", 1500.0)
                    put("rewardPoints", 150000L)
                })
            })
        }
        val admin = EarnProgramRepository.parseAdminOverviewJson(adminJson)
        assertTrue(admin.config.isEnabled)
        assertEquals(1, admin.participants.size)
        assertEquals(2, admin.flaggedDevices.first().linkedUserIds.size)
        assertEquals("approved", admin.payoutLogs.first().decision)
    }

    @Test
    fun `avatar image encoder produces valid non-empty jpeg and handles empty bytes`() {
        val bmp = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val pngBytes = out.toByteArray()
        bmp.recycle()

        val compressed = StorageMediaRepository.encodeCompressedImageBytes(pngBytes, maxEdgePx = 720)
        assertNotNull(compressed)
        assertTrue(compressed!!.isNotEmpty())

        assertNull(StorageMediaRepository.encodeCompressedImageBytes(ByteArray(0)))
    }

    @Test
    fun `device integrity helper collects stable non-empty fingerprint cleanly in jvm environment`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = DeviceIntegrityHelper(context)
        val info = helper.collectDeviceIntegrity("test-seed")
        assertTrue(info.deviceFingerprint.isNotBlank())
        assertEquals(64, info.deviceFingerprint.length)
        assertTrue(info.playIntegrityStatus.isNotBlank())
    }

    @Test
    fun `arabic and english earn strings are present`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.getString(R.string.earn_program_title).contains("Earn"))
        assertTrue(context.getString(R.string.earn_admin_enable_toggle).contains("Enable"))

        val arLocale = Locale.forLanguageTag("ar")
        val config = Configuration(context.resources.configuration).apply {
            setLocale(arLocale)
            setLayoutDirection(arLocale)
        }
        val arRes = context.createConfigurationContext(config).resources
        assertTrue(arRes.getString(R.string.earn_program_title).contains("اربح معنا"))
        assertTrue(arRes.getString(R.string.earn_admin_enable_toggle).contains("تفعيل"))
    }
}
