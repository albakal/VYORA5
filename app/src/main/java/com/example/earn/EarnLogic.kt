package com.example.earn

import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * Pure deterministic domain rules for VYORA "Earn With Us" (اربح معنا) program.
 * All date calculations use Egypt time (Africa/Cairo) so daily thresholds align with server SQL.
 */
object EarnLogic {
    val CAIRO_ZONE: ZoneId = ZoneId.of("Africa/Cairo")

    fun cairoToday(now: Instant = Instant.now()): LocalDate =
        now.atZone(CAIRO_ZONE).toLocalDate()

    fun toCairoDate(instant: Instant): LocalDate =
        instant.atZone(CAIRO_ZONE).toLocalDate()

    fun sanitizeReferralCode(raw: String): String =
        raw.trim().uppercase().replace(Regex("[^A-Z0-9_-]"), "").take(16)

    fun isValidReferralCodeFormat(raw: String): Boolean {
        val clean = sanitizeReferralCode(raw)
        return clean.length in 5..16
    }

    /**
     * Checks whether a single Cairo calendar day qualifies:
     * >= requiredMinutes active usage AND >= requiredVideos valid non-duplicate active videos.
     */
    fun doesDayQualify(
        activeSeconds: Long,
        validVideosCount: Int,
        requiredMinutes: Int = 30,
        requiredVideos: Int = 2
    ): Boolean = activeSeconds >= requiredMinutes * 60L && validVideosCount >= requiredVideos

    /**
     * Computes consecutive qualifying Cairo days streak ending today (or yesterday if today is not yet completed).
     * Missed days strictly break the streak — no missed day can be compensated or backfilled.
     */
    fun calculateConsecutiveStreak(
        qualifyingDates: Set<LocalDate>,
        today: LocalDate = cairoToday()
    ): Int {
        if (qualifyingDates.isEmpty()) return 0
        var anchor = when {
            qualifyingDates.contains(today) -> today
            qualifyingDates.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        while (qualifyingDates.contains(anchor)) {
            streak++
            anchor = anchor.minusDays(1)
        }
        return streak
    }

    /**
     * Weighted overall completion percentage (0..100):
     * 25% personal videos (X/10) + 75% fully completed invited friends (X/10).
     */
    fun calculateOverallProgressPercent(
        personalVideosCount: Int,
        requiredPersonalVideos: Int,
        completedFriendsCount: Int,
        requiredFriends: Int
    ): Int {
        val vRatio = min(1.0, personalVideosCount.coerceAtLeast(0).toDouble() / requiredPersonalVideos.coerceAtLeast(1))
        val fRatio = min(1.0, completedFriendsCount.coerceAtLeast(0).toDouble() / requiredFriends.coerceAtLeast(1))
        return ((vRatio * 0.25 + fRatio * 0.75) * 100.0).toInt().coerceIn(0, 100)
    }

    /**
     * Validates a candidate friend account for referral attribution without banning shared family phones blindly:
     * Returns whether link can be auto-credited vs flagged for admin review.
     */
    fun evaluateReferralDeviceSafety(
        inviterUid: String,
        friendUid: String,
        inviterDeviceFingerprint: String,
        friendDeviceFingerprint: String,
        existingSiblingFingerprints: Set<String>,
        friendDeviceEligible: Boolean
    ): ReferralSafetyResult {
        if (inviterUid.isNotBlank() && inviterUid == friendUid) {
            return ReferralSafetyResult(
                allowedToCredit = false,
                shouldFlagForReview = true,
                reason = "self_referral_not_allowed"
            )
        }
        if (friendDeviceFingerprint.isNotBlank() && friendDeviceFingerprint == inviterDeviceFingerprint) {
            return ReferralSafetyResult(
                allowedToCredit = false,
                shouldFlagForReview = true,
                reason = "self_device_referral_attempt"
            )
        }
        if (friendDeviceFingerprint.isNotBlank() && existingSiblingFingerprints.contains(friendDeviceFingerprint)) {
            return ReferralSafetyResult(
                allowedToCredit = false,
                shouldFlagForReview = true,
                reason = "duplicate_friend_device"
            )
        }
        if (!friendDeviceEligible) {
            return ReferralSafetyResult(
                allowedToCredit = false,
                shouldFlagForReview = true,
                reason = "multiple_accounts_same_device"
            )
        }
        return ReferralSafetyResult(
            allowedToCredit = true,
            shouldFlagForReview = false,
            reason = ""
        )
    }

    /**
     * Computes SHA-256 content hash and total size from an InputStream in constant memory.
     */
    fun computeStreamSha256AndSize(stream: InputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        stream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
                total += read
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return hex to total
    }

    /**
     * Builds the idempotent wallet transaction reference for Earn With Us reward payout.
     */
    fun payoutTransactionRef(userId: String): String = "earn_reward:${userId.trim()}"
}

data class ReferralSafetyResult(
    val allowedToCredit: Boolean,
    val shouldFlagForReview: Boolean,
    val reason: String
)
