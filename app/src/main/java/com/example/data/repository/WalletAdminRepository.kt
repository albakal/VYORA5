package com.example.data.repository

import android.content.Context
import com.example.data.model.AdminRole
import com.example.data.model.AdminSettings
import com.example.data.model.LiveStream
import com.example.data.model.ModerationReport
import com.example.data.model.UserProfile
import com.example.data.model.VideoPost
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
import com.example.data.remote.SupabaseClient
import com.example.data.remote.toAdminRole
import com.example.data.remote.toAdminSettings
import com.example.data.remote.toLiveStream
import com.example.data.remote.toModerationReport
import com.example.data.remote.toObjectList
import com.example.data.remote.toUserProfile
import com.example.data.remote.toVideoPost
import com.example.data.remote.toWallet
import com.example.data.remote.toWalletTransaction
import com.example.data.remote.toWithdrawalRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.util.UUID

class WalletAdminRepository(
    private val supabase: SupabaseClient
) {
    constructor(context: Context) : this(SupabaseClient.getInstance(context))

    private fun requireUid(): String =
        supabase.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeWallet(userId: String): Flow<Wallet?> =
        supabase.observeTopic("wallet").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_wallets",
                    "user_id=eq.${SupabaseClient.urlEncode(userId)}&limit=1"
                )
                arr.toObjectList().firstOrNull()?.toWallet()
            }.getOrNull()
        }

    fun observeTransactions(userId: String): Flow<List<WalletTransaction>> =
        supabase.observeTopic("wallet").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_wallet_transactions",
                    "user_id=eq.${SupabaseClient.urlEncode(userId)}&order=created_at.desc"
                )
                arr.toObjectList().map { it.toWalletTransaction() }
            }.getOrDefault(emptyList())
        }

    fun observeMyWithdrawals(userId: String): Flow<List<WithdrawalRequest>> =
        supabase.observeTopic("withdrawals").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_withdrawal_requests",
                    "user_id=eq.${SupabaseClient.urlEncode(userId)}&order=created_at.desc"
                )
                arr.toObjectList().map { it.toWithdrawalRequest() }
            }.getOrDefault(emptyList())
        }

    fun observeAdminSettings(): Flow<AdminSettings> =
        supabase.observeTopic("admin_settings").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_admin_settings",
                    "id=eq.global&limit=1"
                )
                arr.toObjectList().firstOrNull()?.toAdminSettings() ?: AdminSettings(id = "global")
            }.getOrDefault(AdminSettings(id = "global"))
        }

    suspend fun checkIsAdmin(uid: String, email: String?): Boolean {
        if (email.equals("mahmoudomran324@gmail.com", ignoreCase = true) &&
            supabase.currentUser?.isEmailVerified == true
        ) {
            return true
        }
        return runCatching {
            val arr = supabase.select(
                "vyora_admin_roles",
                "select=user_id&user_id=eq.${SupabaseClient.urlEncode(uid)}&limit=1"
            )
            arr.length() > 0
        }.getOrDefault(false)
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        reason: String,
        details: String
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        supabase.insert(
            "vyora_reports",
            JSONObject().apply {
                put("id", UUID.randomUUID().toString().replace("-", ""))
                put("reporter_id", uid)
                put("target_type", targetType)
                put("target_id", targetId)
                put("reason", reason.take(120))
                put("details", details.take(1000))
                put("status", "pending")
            }
        )
        supabase.notifyChange("reports")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun submitWithdrawalRequest(
        amountUsd: Double,
        pointsSpent: Long,
        method: String,
        accountDetails: String
    ): Result<Unit> = runCatching {
        requireUid()
        require(amountUsd > 0.0 && pointsSpent > 0L)
        supabase.rpc(
            "vyora_request_withdrawal",
            JSONObject().apply {
                put("p_amount_usd", amountUsd)
                put("p_method", method.take(60))
                put("p_account_details", accountDetails.take(200))
            }
        )
        supabase.notifyChange("withdrawals", "wallet")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    // --- Admin Dashboard Operations ---
    fun observeAllUsersAdmin(): Flow<List<UserProfile>> =
        supabase.observeTopic("profiles").map {
            runCatching {
                val arr = supabase.select("vyora_profiles", "order=created_at.desc&limit=100")
                arr.toObjectList().map { it.toUserProfile() }
            }.getOrDefault(emptyList())
        }

    fun observeAllVideosAdmin(): Flow<List<VideoPost>> =
        supabase.observeTopic("videos").map {
            runCatching {
                val arr = supabase.select("vyora_videos_ledger", "order=created_at.desc&limit=100")
                arr.toObjectList().map { it.toVideoPost() }
            }.getOrDefault(emptyList())
        }

    fun observeReportsAdmin(): Flow<List<ModerationReport>> =
        supabase.observeTopic("reports").map {
            runCatching {
                val arr = supabase.select("vyora_reports", "order=created_at.desc&limit=100")
                arr.toObjectList().map { it.toModerationReport() }
            }.getOrDefault(emptyList())
        }

    fun observeWithdrawalsAdmin(): Flow<List<WithdrawalRequest>> =
        supabase.observeTopic("withdrawals").map {
            runCatching {
                val arr = supabase.select("vyora_withdrawal_requests", "order=created_at.desc&limit=100")
                arr.toObjectList().map { it.toWithdrawalRequest() }
            }.getOrDefault(emptyList())
        }

    fun observeAdmins(): Flow<List<AdminRole>> =
        supabase.observeTopic("admins").map {
            runCatching {
                val arr = supabase.select("vyora_admin_roles", "order=created_at.desc")
                arr.toObjectList().map { it.toAdminRole() }
            }.getOrDefault(emptyList())
        }

    fun observeLiveStreamsAdmin(): Flow<List<LiveStream>> =
        supabase.observeTopic("live").map {
            runCatching {
                val arr = supabase.select("vyora_live_streams", "order=started_at.desc&limit=50")
                arr.toObjectList().map { it.toLiveStream() }
            }.getOrDefault(emptyList())
        }

    suspend fun setUserBanned(targetUid: String, banned: Boolean): Result<Unit> = runCatching {
        requireUid()
        supabase.update(
            "vyora_profiles",
            "user_id=eq.${SupabaseClient.urlEncode(targetUid)}",
            JSONObject().put("is_banned", banned)
        )
        supabase.notifyChange("profiles")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun removeVideoAdmin(videoId: String): Result<Unit> = runCatching {
        requireUid()
        supabase.update(
            "vyora_videos_ledger",
            "video_id=eq.${SupabaseClient.urlEncode(videoId)}",
            JSONObject().put("status", "removed")
        )
        supabase.notifyChange("videos")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> = runCatching {
        val adminUid = requireUid()
        supabase.update(
            "vyora_reports",
            "id=eq.${SupabaseClient.urlEncode(reportId)}",
            JSONObject().apply {
                put("status", status)
                put("reviewed_by", adminUid)
            }
        )
        supabase.notifyChange("reports")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun grantCreatorPointsAdmin(
        targetUid: String,
        pointsDelta: Long,
        pointValueUsd: Double,
        reason: String
    ): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_admin_grant_points",
            JSONObject().apply {
                put("p_target_uid", targetUid)
                put("p_points_delta", pointsDelta)
                put("p_reason", reason.take(200))
            }
        )
        supabase.notifyChange("wallet")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun reviewWithdrawalAdmin(request: WithdrawalRequest, approve: Boolean): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_admin_review_withdrawal",
            JSONObject().apply {
                put("p_request_id", request.id)
                put("p_approve", approve)
            }
        )
        supabase.notifyChange("withdrawals", "wallet")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun saveAdminSettings(
        pointValueUsd: Double,
        minWithdrawalUsd: Double,
        rewardsEnabled: Boolean,
        maxVideoDurationSec: Long
    ): Result<Unit> = runCatching {
        val adminUid = requireUid()
        supabase.insert(
            "vyora_admin_settings",
            JSONObject().apply {
                put("id", "global")
                put("point_value_usd", pointValueUsd.coerceAtLeast(0.0001))
                put("min_withdrawal_usd", minWithdrawalUsd.coerceAtLeast(1.0))
                put("rewards_enabled", rewardsEnabled)
                put("max_video_duration_sec", maxVideoDurationSec.coerceIn(15L, 300L))
                put("updated_by", adminUid)
            },
            upsert = true,
            onConflict = "id"
        )
        supabase.notifyChange("admin_settings")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun assignAdminRole(targetUid: String, email: String, role: String): Result<Unit> = runCatching {
        val adminUid = requireUid()
        supabase.insert(
            "vyora_admin_roles",
            JSONObject().apply {
                put("user_id", targetUid)
                put("email", email.trim().take(200))
                put("role", role)
                put("assigned_by", adminUid)
            },
            upsert = true,
            onConflict = "user_id"
        )
        supabase.notifyChange("admins")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun removeAdminRole(targetUid: String): Result<Unit> = runCatching {
        requireUid()
        supabase.delete("vyora_admin_roles", "user_id=eq.${SupabaseClient.urlEncode(targetUid)}")
        supabase.notifyChange("admins")
        Unit
    }.onFailure { if (it is CancellationException) throw it }
}
