package com.example.data.remote

import android.content.Context
import com.example.data.model.EarnAdminOverview
import com.example.data.model.EarnAdminParticipant
import com.example.data.model.EarnDashboardState
import com.example.data.model.EarnDeviceFlag
import com.example.data.model.EarnFriendProgress
import com.example.data.model.EarnPayoutLog
import com.example.data.model.EarnProgramConfig
import com.example.earn.EarnLogic
import com.example.security.DeviceIntegrityInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class EarnProgramRepository(
    private val supabase: SupabaseClient
) {
    constructor(context: Context) : this(SupabaseClient.getInstance(context))

    private fun requireUid(): String =
        supabase.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeDashboard(deviceProvider: suspend () -> DeviceIntegrityInfo): Flow<EarnDashboardState?> =
        supabase.observeTopic("earn", intervalMs = 12_000L).map {
            if (supabase.currentUser == null) return@map null
            runCatching {
                val device = deviceProvider()
                fetchDashboard(device).getOrNull()
            }.getOrNull()
        }

    suspend fun registerDeviceAndCode(device: DeviceIntegrityInfo): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_earn_register_device_and_code",
            JSONObject().apply {
                put("p_device_fingerprint", device.deviceFingerprint)
                put("p_app_set_id", device.appSetId)
                put("p_hardware_id", device.hardwareId)
                put("p_play_integrity_status", device.playIntegrityStatus)
                put("p_play_integrity_token_hash", device.playIntegrityTokenHash)
            }
        )
        supabase.notifyChange("earn")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun redeemReferralCode(
        rawCode: String,
        device: DeviceIntegrityInfo
    ): Result<Boolean> = runCatching {
        requireUid()
        val clean = EarnLogic.sanitizeReferralCode(rawCode)
        require(EarnLogic.isValidReferralCodeFormat(clean)) { "أدخل كود إحالة صحيح" }
        val raw = supabase.rpc(
            "vyora_earn_redeem_referral",
            JSONObject().apply {
                put("p_code", clean)
                put("p_device_fingerprint", device.deviceFingerprint)
                put("p_app_set_id", device.appSetId)
                put("p_hardware_id", device.hardwareId)
                put("p_play_integrity_status", device.playIntegrityStatus)
            }
        ).trim()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        supabase.notifyChange("earn")
        obj.optBoolean("underReview", false)
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun sendHeartbeat(
        deltaSeconds: Long,
        device: DeviceIntegrityInfo
    ): Result<Unit> = runCatching {
        if (supabase.currentUser == null) return@runCatching
        supabase.rpc(
            "vyora_earn_heartbeat",
            JSONObject().apply {
                put("p_delta_seconds", deltaSeconds.coerceIn(1L, 90L))
                put("p_device_fingerprint", device.deviceFingerprint)
                put("p_app_set_id", device.appSetId)
                put("p_hardware_id", device.hardwareId)
                put("p_play_integrity_status", device.playIntegrityStatus)
            }
        )
        supabase.notifyChange("earn")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun fetchDashboard(device: DeviceIntegrityInfo): Result<EarnDashboardState> = runCatching {
        requireUid()
        val raw = supabase.rpc(
            "vyora_earn_get_dashboard",
            JSONObject().apply {
                put("p_device_fingerprint", device.deviceFingerprint)
                put("p_app_set_id", device.appSetId)
                put("p_hardware_id", device.hardwareId)
                put("p_play_integrity_status", device.playIntegrityStatus)
            }
        ).trim()
        parseDashboardJson(JSONObject(raw))
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun fetchAdminOverview(): Result<EarnAdminOverview> = runCatching {
        requireUid()
        val raw = supabase.rpc("vyora_admin_get_earn_overview", JSONObject()).trim()
        parseAdminOverviewJson(JSONObject(raw))
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun adminUpdateConfig(config: EarnProgramConfig): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_admin_update_earn_config",
            JSONObject().apply {
                put("p_is_enabled", config.isEnabled)
                put("p_reward_egp", config.rewardEgp)
                put("p_reward_points", config.rewardPoints)
                put("p_inviter_videos_required", config.inviterVideosRequired.toLong())
                put("p_required_friends", config.requiredFriends.toLong())
                put("p_friend_days_required", config.friendDaysRequired.toLong())
                put("p_friend_daily_minutes_required", config.friendDailyMinutesRequired.toLong())
                put("p_friend_daily_videos_required", config.friendDailyVideosRequired.toLong())
            }
        )
        supabase.notifyChange("earn")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun adminReviewDevice(
        deviceFingerprint: String,
        decision: String,
        note: String
    ): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_admin_review_device",
            JSONObject().apply {
                put("p_device_fingerprint", deviceFingerprint)
                put("p_decision", decision)
                put("p_note", note.take(300))
            }
        )
        supabase.notifyChange("earn")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun adminReviewPayout(
        targetUid: String,
        approve: Boolean,
        note: String
    ): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_admin_review_earn_payout",
            JSONObject().apply {
                put("p_target_uid", targetUid)
                put("p_approve", approve)
                put("p_note", note.take(300))
            }
        )
        supabase.notifyChange("earn", "wallet", "notifications")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    companion object {
        fun parseConfigJson(obj: JSONObject?): EarnProgramConfig {
            if (obj == null) return EarnProgramConfig()
            return EarnProgramConfig(
                isEnabled = obj.optBoolean("isEnabled", true),
                rewardEgp = obj.optDouble("rewardEgp", 1500.0),
                rewardPoints = obj.optLong("rewardPoints", 150_000L),
                inviterVideosRequired = obj.optInt("inviterVideosRequired", 10),
                requiredFriends = obj.optInt("requiredFriends", 10),
                friendDaysRequired = obj.optInt("friendDaysRequired", 10),
                friendDailyMinutesRequired = obj.optInt("friendDailyMinutesRequired", 30),
                friendDailyVideosRequired = obj.optInt("friendDailyVideosRequired", 2)
            )
        }

        fun parseFriendsArray(arr: JSONArray?): List<EarnFriendProgress> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        EarnFriendProgress(
                            userId = o.optString("userId"),
                            username = o.optString("username"),
                            displayName = o.optString("displayName"),
                            avatarUrl = o.optString("avatarUrl"),
                            registeredAt = o.optString("registeredAt"),
                            completedDays = o.optInt("completedDays", 0),
                            requiredDays = o.optInt("requiredDays", 10),
                            todayActiveMinutes = o.optInt("todayActiveMinutes", 0),
                            todayActiveSeconds = o.optLong("todayActiveSeconds", 0L),
                            requiredDailyMinutes = o.optInt("requiredDailyMinutes", 30),
                            todayValidVideos = o.optInt("todayValidVideos", 0),
                            requiredDailyVideos = o.optInt("requiredDailyVideos", 2),
                            isCompleted = o.optBoolean("isCompleted", false),
                            isDeviceEligible = o.optBoolean("isDeviceEligible", true),
                            suspiciousFlag = o.optBoolean("suspiciousFlag", false),
                            suspiciousReason = o.optString("suspiciousReason")
                        )
                    )
                }
            }
        }

        fun parseDashboardJson(obj: JSONObject): EarnDashboardState = EarnDashboardState(
            config = parseConfigJson(obj.optJSONObject("config")),
            referralCode = obj.optString("referralCode"),
            referredByCode = obj.optString("referredByCode"),
            referredByUid = obj.optString("referredByUid"),
            personalVideosCount = obj.optInt("personalVideosCount", 0),
            invitedFriendsCount = obj.optInt("invitedFriendsCount", 0),
            completedFriendsCount = obj.optInt("completedFriendsCount", 0),
            myTodayMinutes = obj.optInt("myTodayMinutes", 0),
            myTodayVideos = obj.optInt("myTodayVideos", 0),
            myConsecutiveDays = obj.optInt("myConsecutiveDays", 0),
            progressPercent = obj.optDouble("progressPercent", 0.0),
            status = obj.optString("status", "in_progress"),
            reviewNote = obj.optString("reviewNote"),
            isDeviceEligible = obj.optBoolean("isDeviceEligible", true),
            suspiciousFlag = obj.optBoolean("suspiciousFlag", false),
            suspiciousReason = obj.optString("suspiciousReason"),
            cairoDate = obj.optString("cairoDate"),
            friends = parseFriendsArray(obj.optJSONArray("friends"))
        )

        fun parseAdminOverviewJson(obj: JSONObject): EarnAdminOverview {
            val participantsArr = obj.optJSONArray("participants") ?: JSONArray()
            val participants = buildList {
                for (i in 0 until participantsArr.length()) {
                    val p = participantsArr.optJSONObject(i) ?: continue
                    add(
                        EarnAdminParticipant(
                            userId = p.optString("userId"),
                            username = p.optString("username"),
                            displayName = p.optString("displayName"),
                            email = p.optString("email"),
                            avatarUrl = p.optString("avatarUrl"),
                            referralCode = p.optString("referralCode"),
                            referredByCode = p.optString("referredByCode"),
                            personalVideosCount = p.optInt("personalVideosCount", 0),
                            invitedFriendsCount = p.optInt("invitedFriendsCount", 0),
                            completedFriendsCount = p.optInt("completedFriendsCount", 0),
                            status = p.optString("status", "in_progress"),
                            reviewNote = p.optString("reviewNote"),
                            isDeviceEligible = p.optBoolean("isDeviceEligible", true),
                            suspiciousFlag = p.optBoolean("suspiciousFlag", false),
                            suspiciousReason = p.optString("suspiciousReason"),
                            deviceFingerprint = p.optString("deviceFingerprint"),
                            playIntegrityStatus = p.optString("playIntegrityStatus"),
                            paidOutAt = p.optString("paidOutAt"),
                            friends = parseFriendsArray(p.optJSONArray("friends"))
                        )
                    )
                }
            }

            val devicesArr = obj.optJSONArray("flaggedDevices") ?: JSONArray()
            val devices = buildList {
                for (i in 0 until devicesArr.length()) {
                    val d = devicesArr.optJSONObject(i) ?: continue
                    val linkedArr = d.optJSONArray("linkedUserIds") ?: JSONArray()
                    val linkedIds = buildList {
                        for (j in 0 until linkedArr.length()) {
                            linkedArr.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                    add(
                        EarnDeviceFlag(
                            deviceFingerprint = d.optString("deviceFingerprint"),
                            appSetId = d.optString("appSetId"),
                            playIntegrityStatus = d.optString("playIntegrityStatus"),
                            firstUserId = d.optString("firstUserId"),
                            linkedUserIds = linkedIds,
                            isFlagged = d.optBoolean("isFlagged", false),
                            flagReason = d.optString("flagReason"),
                            adminDecision = d.optString("adminDecision", "pending"),
                            adminNote = d.optString("adminNote"),
                            updatedAt = d.optString("updatedAt")
                        )
                    )
                }
            }

            val logsArr = obj.optJSONArray("payoutLogs") ?: JSONArray()
            val logs = buildList {
                for (i in 0 until logsArr.length()) {
                    val l = logsArr.optJSONObject(i) ?: continue
                    add(
                        EarnPayoutLog(
                            id = l.optString("id"),
                            userId = l.optString("userId"),
                            username = l.optString("username"),
                            rewardEgp = l.optDouble("rewardEgp", 1500.0),
                            rewardPoints = l.optLong("rewardPoints", 150_000L),
                            decision = l.optString("decision"),
                            note = l.optString("note"),
                            reviewedBy = l.optString("reviewedBy"),
                            createdAt = l.optString("createdAt")
                        )
                    )
                }
            }

            return EarnAdminOverview(
                config = parseConfigJson(obj.optJSONObject("config")),
                participants = participants,
                flaggedDevices = devices,
                payoutLogs = logs
            )
        }
    }
}
