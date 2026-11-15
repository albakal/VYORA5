package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.SupabaseClient
import com.example.data.remote.toAdminSettings
import com.example.ui.feed.WatchWheelLogic
import org.json.JSONObject
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
class WatchWheelAndUploadTest {

    @Test
    fun `sanitizeBucketName falls back to vyora-media on blank quotes or placeholders`() {
        assertEquals("vyora-media", SupabaseClient.sanitizeBucketName(null))
        assertEquals("vyora-media", SupabaseClient.sanitizeBucketName(""))
        assertEquals("vyora-media", SupabaseClient.sanitizeBucketName("   "))
        assertEquals("vyora-media", SupabaseClient.sanitizeBucketName("\"vyora-media\""))
        assertEquals("vyora-media", SupabaseClient.sanitizeBucketName("/vyora-media/"))
        assertEquals("vyora-media", SupabaseClient.sanitizeBucketName("YOUR_STORAGE_BUCKET"))
        assertEquals("custom-media", SupabaseClient.sanitizeBucketName("  'custom-media/' "))
    }

    @Test
    fun `toAdminSettings parses watch wheel admin toggle and parameters accurately`() {
        val disabledJson = JSONObject(
            """
            {
              "id": "global",
              "point_value_usd": 0.02,
              "min_withdrawal_usd": 5.0,
              "rewards_enabled": true,
              "max_video_duration_sec": 120,
              "watch_wheel_enabled": false,
              "watch_wheel_cycle_sec": 45,
              "watch_wheel_points_per_cycle": 10,
              "watch_wheel_daily_cap_cycles": 40
            }
            """.trimIndent()
        )
        val disabled = disabledJson.toAdminSettings()
        assertFalse(disabled.watchWheelEnabled)
        assertEquals(45L, disabled.watchWheelCycleSec)
        assertEquals(10L, disabled.watchWheelPointsPerCycle)
        assertEquals(40L, disabled.watchWheelDailyCapCycles)

        val defaultJson = JSONObject("""{"id":"global"}""")
        val defaults = defaultJson.toAdminSettings()
        assertTrue(defaults.watchWheelEnabled)
        assertEquals(30L, defaults.watchWheelCycleSec)
        assertEquals(5L, defaults.watchWheelPointsPerCycle)
        assertEquals(60L, defaults.watchWheelDailyCapCycles)
    }

    @Test
    fun `watch wheel pauses when admin disables it or video is paused or screen inactive`() {
        val pausedVideo = WatchWheelLogic.advanceTick(
            currentElapsedMillis = 12_000L,
            deltaMillis = 1_000L,
            cycleSec = 30L,
            isEnabled = true,
            isScreenActive = true,
            isVideoPlaying = false,
            isClaiming = false,
            isDailyCapReached = false
        )
        assertEquals(12_000L, pausedVideo.nextElapsedMillis)
        assertFalse(pausedVideo.cycleCompleted)

        val adminOff = WatchWheelLogic.advanceTick(
            currentElapsedMillis = 12_000L,
            deltaMillis = 1_000L,
            cycleSec = 30L,
            isEnabled = false,
            isScreenActive = true,
            isVideoPlaying = true,
            isClaiming = false,
            isDailyCapReached = false
        )
        assertEquals(12_000L, adminOff.nextElapsedMillis)
        assertFalse(adminOff.cycleCompleted)

        val inactiveScreen = WatchWheelLogic.advanceTick(
            currentElapsedMillis = 12_000L,
            deltaMillis = 1_000L,
            cycleSec = 30L,
            isEnabled = true,
            isScreenActive = false,
            isVideoPlaying = true,
            isClaiming = false,
            isDailyCapReached = false
        )
        assertEquals(12_000L, inactiveScreen.nextElapsedMillis)
        assertFalse(inactiveScreen.cycleCompleted)

        val capped = WatchWheelLogic.advanceTick(
            currentElapsedMillis = 0L,
            deltaMillis = 500L,
            cycleSec = 30L,
            isEnabled = true,
            isScreenActive = true,
            isVideoPlaying = true,
            isClaiming = false,
            isDailyCapReached = true
        )
        assertEquals(0L, capped.nextElapsedMillis)
        assertFalse(capped.cycleCompleted)
    }

    @Test
    fun `watch wheel advances and completes cycle when playback reaches target seconds`() {
        val mid = WatchWheelLogic.advanceTick(
            currentElapsedMillis = 14_500L,
            deltaMillis = 500L,
            cycleSec = 30L,
            isEnabled = true,
            isScreenActive = true,
            isVideoPlaying = true,
            isClaiming = false,
            isDailyCapReached = false
        )
        assertEquals(15_000L, mid.nextElapsedMillis)
        assertFalse(mid.cycleCompleted)
        assertEquals(0.5f, WatchWheelLogic.computeProgress(mid.nextElapsedMillis, 30L, false), 0.001f)
        assertEquals(15L, WatchWheelLogic.computeRemainingSeconds(mid.nextElapsedMillis, 30L))

        val completed = WatchWheelLogic.advanceTick(
            currentElapsedMillis = 29_750L,
            deltaMillis = 250L,
            cycleSec = 30L,
            isEnabled = true,
            isScreenActive = true,
            isVideoPlaying = true,
            isClaiming = false,
            isDailyCapReached = false
        )
        assertTrue(completed.cycleCompleted)
        assertEquals(0L, completed.nextElapsedMillis)
        assertEquals(30L, completed.completedWatchSeconds)
    }

    @Test
    fun `arabic and english watch wheel strings are present`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val arCfg = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("ar"))
        }
        val enCfg = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("en"))
        }
        val arRes = base.createConfigurationContext(arCfg).resources
        val enRes = base.createConfigurationContext(enCfg).resources

        assertEquals("عجلة الربح من المشاهدات", arRes.getString(R.string.watch_wheel_title))
        assertEquals("تشغيل عجلة الربح للمستخدمين", arRes.getString(R.string.watch_wheel_admin_toggle))
        assertEquals("Watch Earnings Wheel", enRes.getString(R.string.watch_wheel_title))
        assertEquals("Enable Watch Wheel on Home Feed", enRes.getString(R.string.watch_wheel_admin_toggle))
    }
}
