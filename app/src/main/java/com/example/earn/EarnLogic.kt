package com.example.earn

import android.net.Uri
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

/**
 * Pure deterministic domain rules for VYORA "Earn With Us" (اربح معنا) program.
 * All date calculations use Egypt time (Africa/Cairo) so daily thresholds stay consistent across devices.
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

    fun buildReferralInviteUri(code: String): String {
        val clean = sanitizeReferralCode(code)
        return "https://vyora.app/invite?ref=$clean"
    }

    fun buildCustomSchemeInviteUri(code: String): String {
        val clean = sanitizeReferralCode(code)
        return "vyora://invite?ref=$clean"
    }

    fun extractReferralCodeFromUri(uri: Uri?): String {
        if (uri == null) return ""
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()
        val isInviteUri = (scheme == "vyora" && host == "invite") ||
            ((scheme == "https" || scheme == "http") &&
                (host == "vyora.app" || host == "www.vyora.app") &&
                path.startsWith("/invite"))
        if (!isInviteUri) return ""

        val queryCandidate = uri.getQueryParameter("ref")
            ?: uri.getQueryParameter("code")
        if (!queryCandidate.isNullOrBlank()) {
            val clean = sanitizeReferralCode(queryCandidate)
            if (isValidReferralCodeFormat(clean)) return clean
        }

        val lastSegment = uri.pathSegments.lastOrNull()?.takeIf { !it.equals("invite", ignoreCase = true) }
        if (!lastSegment.isNullOrBlank()) {
            val clean = sanitizeReferralCode(lastSegment)
            if (isValidReferralCodeFormat(clean)) return clean
        }
        return ""
    }

    fun doesDayQualify(
        activeSeconds: Long,
        validVideosCount: Int,
        requiredMinutes: Int = 30,
        requiredVideos: Int = 2
    ): Boolean = activeSeconds >= requiredMinutes * 60L && validVideosCount >= requiredVideos

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

    fun stableSha256Id(input: String, length: Int = 32): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(length)
    }

    fun payoutTransactionRef(userId: String): String = "earn_reward:${userId.trim()}"
}

data class ReferralSafetyResult(
    val allowedToCredit: Boolean,
    val shouldFlagForReview: Boolean,
    val reason: String
)
