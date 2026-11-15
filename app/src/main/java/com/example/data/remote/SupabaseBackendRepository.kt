package com.example.data.remote

import android.content.Context
import com.example.data.model.WatchWheelClaimResult
import com.example.data.model.WatchWheelStatus
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

data class AgoraLiveSession(
    val appId: String,
    val token: String,
    val channelName: String,
    val rtcUid: Int = 0,
    val role: String = "subscriber"
)

data class SupabaseWalletSummary(
    val supabaseConfigured: Boolean,
    val synced: Boolean,
    val points: Long,
    val balanceUsd: Double,
    val totalEarnedUsd: Double,
    val transactionsCount: Int,
    val videosCount: Int
)

/**
 * Invokes atomic Supabase PL/pgSQL RPCs and Edge Functions directly.
 */
class SupabaseBackendRepository(
    private val supabase: SupabaseClient
) {
    constructor(context: Context) : this(SupabaseClient.getInstance(context))

    suspend fun fetchAgoraLiveSession(streamId: String): Result<AgoraLiveSession> = runCatching {
        val data = supabase.invokeEdgeFunction(
            "get-agora-token",
            JSONObject().put("streamId", streamId)
        )
        val appId = data.optString("appId").trim()
        val token = data.optString("token").trim()
        val channelName = data.optString("channelName").trim()
        val rtcUid = data.optInt("rtcUid", 0)
        val role = data.optString("role").ifBlank { "subscriber" }
        if (appId.isBlank() || token.isBlank() || channelName.isBlank()) {
            throw IllegalStateException("Incomplete Agora session payload")
        }
        AgoraLiveSession(
            appId = appId,
            token = token,
            channelName = channelName,
            rtcUid = rtcUid,
            role = role
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun fetchWalletAccountingSummary(): Result<SupabaseWalletSummary> = runCatching {
        val raw = supabase.rpc("vyora_get_wallet_summary")
        val data = JSONObject(raw)
        SupabaseWalletSummary(
            supabaseConfigured = data.optBoolean("supabaseConfigured", true),
            synced = data.optBoolean("synced", true),
            points = data.optLong("points", 0L),
            balanceUsd = data.optDouble("balanceUsd", 0.0),
            totalEarnedUsd = data.optDouble("totalEarnedUsd", 0.0),
            transactionsCount = data.optInt("transactionsCount", 0),
            videosCount = data.optInt("videosCount", 0)
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun recordVideoViewServer(videoId: String): Result<Boolean> = runCatching {
        val raw = supabase.rpc(
            "vyora_record_view_reward",
            JSONObject().put("p_video_id", videoId)
        ).trim()
        supabase.notifyChange("videos", "wallet")
        raw == "true"
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun submitWithdrawalServer(
        amountUsd: Double,
        method: String,
        accountDetails: String
    ): Result<String> = runCatching {
        val raw = supabase.rpc(
            "vyora_request_withdrawal",
            JSONObject().apply {
                put("p_amount_usd", amountUsd)
                put("p_method", method)
                put("p_account_details", accountDetails)
            }
        ).trim().trim('"')
        supabase.notifyChange("withdrawals", "wallet")
        raw
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun adminGrantPointsServer(
        targetUid: String,
        pointsDelta: Long,
        reason: String
    ): Result<Boolean> = runCatching {
        supabase.rpc(
            "vyora_admin_grant_points",
            JSONObject().apply {
                put("p_target_uid", targetUid)
                put("p_points_delta", pointsDelta)
                put("p_reason", reason)
            }
        )
        supabase.notifyChange("wallet")
        true
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun adminReviewWithdrawalServer(
        requestId: String,
        approve: Boolean
    ): Result<Boolean> = runCatching {
        supabase.rpc(
            "vyora_admin_review_withdrawal",
            JSONObject().apply {
                put("p_request_id", requestId)
                put("p_approve", approve)
            }
        )
        supabase.notifyChange("withdrawals", "wallet")
        true
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun fetchWatchWheelStatus(): Result<WatchWheelStatus> = runCatching {
        val raw = supabase.rpc("vyora_get_watch_wheel_status")
        val data = if (raw.isBlank()) JSONObject() else JSONObject(raw)
        WatchWheelStatus(
            enabled = data.optBoolean("enabled", true),
            cycleSec = data.optLong("cycleSec", 30L).coerceIn(10L, 180L),
            pointsPerCycle = data.optLong("pointsPerCycle", 5L).coerceIn(1L, 500L),
            dailyCapCycles = data.optLong("dailyCapCycles", 60L).coerceIn(1L, 500L),
            todayCycles = data.optLong("todayCycles", 0L).coerceAtLeast(0L)
        )
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }

    suspend fun claimWatchWheelReward(
        videoId: String,
        watchedSec: Long
    ): Result<WatchWheelClaimResult> = runCatching {
        val raw = supabase.rpc(
            "vyora_claim_watch_wheel_reward",
            JSONObject().apply {
                put("p_video_id", videoId)
                put("p_watched_sec", watchedSec.coerceAtLeast(1L))
            }
        )
        val data = if (raw.isBlank()) JSONObject() else JSONObject(raw)
        val result = WatchWheelClaimResult(
            awarded = data.optBoolean("awarded", false),
            reason = data.optString("reason"),
            pointsDelta = data.optLong("pointsDelta", 0L),
            todayCycles = data.optLong("todayCycles", 0L),
            dailyCapCycles = data.optLong("dailyCapCycles", 60L),
            walletPoints = data.optLong("walletPoints", 0L)
        )
        if (result.awarded) {
            supabase.notifyChange("wallet")
        }
        result
    }.onFailure { e ->
        if (e is CancellationException) throw e
    }
}
