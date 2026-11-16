package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminRole
import com.example.data.model.AdminSettings
import com.example.data.model.LiveStream
import com.example.data.model.ModerationReport
import com.example.data.model.UserProfile
import com.example.data.model.VideoPost
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
import com.example.data.remote.FirestoreErrorHandler
import com.example.data.remote.OperationType
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class WalletAdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeWallet(userId: String): Flow<Wallet?> =
        db.collection("wallets").document(userId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(Wallet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "wallets/$userId", auth)
                emit(null)
            }

    fun observeTransactions(userId: String): Flow<List<WalletTransaction>> =
        db.collection("wallets").document(userId).collection("transactions")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(WalletTransaction::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "wallets/$userId/transactions", auth)
                emit(emptyList())
            }

    fun observeMyWithdrawals(userId: String): Flow<List<WithdrawalRequest>> =
        db.collection("withdrawals")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(WithdrawalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "withdrawals", auth)
                emit(emptyList())
            }

    fun observeAdminSettings(): Flow<AdminSettings> =
        db.collection("admin_settings").document("global")
            .snapshots()
            .map { snap ->
                if (!snap.exists()) AdminSettings(id = "global")
                else snap.toObject(AdminSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: AdminSettings(id = "global")
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "admin_settings/global", auth)
                emit(AdminSettings(id = "global"))
            }

    suspend fun checkIsAdmin(uid: String, email: String?): Boolean {
        val verified = auth.currentUser?.isEmailVerified == true
        val isPrimaryAdmin = verified && (
            email.equals("mahmoudomran324@gmail.com", ignoreCase = true) ||
                email.equals("omranyehya210@gmail.com", ignoreCase = true)
            )
        if (isPrimaryAdmin) return true

        return runCatching {
            db.collection("admins").document(uid).get().await().exists()
        }.getOrDefault(false)
    }

    suspend fun submitReport(
        targetType: String,
        targetId: String,
        reason: String,
        details: String
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val reportId = UUID.randomUUID().toString().replace("-", "")
        db.collection("reports").document(reportId).set(
            mapOf(
                "reporterId" to uid,
                "targetType" to targetType.trim().take(40),
                "targetId" to targetId.trim().take(128),
                "reason" to reason.trim().take(150),
                "details" to details.trim().take(1000),
                "status" to "pending",
                "reviewedBy" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.CREATE, "reports", auth)
    }

    fun observeAllUsersAdmin(): Flow<List<UserProfile>> =
        db.collection("profiles")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "profiles", auth)
                emit(emptyList())
            }

    fun observeAllVideosAdmin(): Flow<List<VideoPost>> =
        db.collection("videos")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "videos", auth)
                emit(emptyList())
            }

    fun observeReportsAdmin(): Flow<List<ModerationReport>> =
        db.collection("reports")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(ModerationReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "reports", auth)
                emit(emptyList())
            }

    fun observeWithdrawalsAdmin(): Flow<List<WithdrawalRequest>> =
        db.collection("withdrawals")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(WithdrawalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "withdrawals", auth)
                emit(emptyList())
            }

    fun observeAdmins(): Flow<List<AdminRole>> =
        db.collection("admins")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(AdminRole::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "admins", auth)
                emit(emptyList())
            }

    fun observeLiveStreamsAdmin(): Flow<List<LiveStream>> =
        db.collection("live_streams")
            .limit(50)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(LiveStream::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.startedAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "live_streams", auth)
                emit(emptyList())
            }

    suspend fun setUserBanned(targetUid: String, banned: Boolean): Result<Unit> = runCatching {
        requireUid()
        db.collection("profiles").document(targetUid)
            .update(
                mapOf(
                    "isBanned" to banned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "profiles/$targetUid", auth)
    }

    suspend fun removeVideoAdmin(videoId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("videos").document(videoId)
            .update(
                mapOf(
                    "status" to "removed",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "videos/$videoId", auth)
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> = runCatching {
        val adminUid = requireUid()
        val resolvedStatus = if (status in listOf("approved", "dismissed")) status else "dismissed"
        db.collection("reports").document(reportId)
            .update(
                mapOf(
                    "status" to resolvedStatus,
                    "reviewedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "reports/$reportId", auth)
    }

    suspend fun saveAdminSettings(
        pointValueUsd: Double,
        minWithdrawalUsd: Double,
        rewardsEnabled: Boolean,
        maxVideoDurationSec: Long
    ): Result<Unit> = runCatching {
        val adminUid = requireUid()
        val settingsRef = db.collection("admin_settings").document("global")
        val current = runCatching {
            settingsRef.get().await().toObject(AdminSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrNull() ?: AdminSettings(id = "global")

        settingsRef.set(
            mapOf(
                "pointValueUsd" to pointValueUsd.coerceAtLeast(0.0001),
                "minWithdrawalUsd" to minWithdrawalUsd.coerceAtLeast(1.0),
                "rewardsEnabled" to rewardsEnabled,
                "maxVideoDurationSec" to maxVideoDurationSec.coerceIn(15L, 300L),
                "watchWheelEnabled" to current.watchWheelEnabled,
                "watchWheelCycleSec" to current.watchWheelCycleSec.coerceIn(10L, 180L),
                "watchWheelPointsPerCycle" to current.watchWheelPointsPerCycle.coerceIn(1L, 500L),
                "watchWheelDailyCapCycles" to current.watchWheelDailyCapCycles.coerceIn(1L, 500L),
                "updatedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "admin_settings/global", auth)
    }

    suspend fun updateWatchWheelSettings(
        enabled: Boolean,
        cycleSec: Long,
        pointsPerCycle: Long,
        dailyCapCycles: Long
    ): Result<Unit> = runCatching {
        val adminUid = requireUid()
        val settingsRef = db.collection("admin_settings").document("global")
        val current = runCatching {
            settingsRef.get().await().toObject(AdminSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }.getOrNull() ?: AdminSettings(id = "global")

        settingsRef.set(
            mapOf(
                "pointValueUsd" to current.pointValueUsd.coerceAtLeast(0.0001),
                "minWithdrawalUsd" to current.minWithdrawalUsd.coerceAtLeast(1.0),
                "rewardsEnabled" to current.rewardsEnabled,
                "maxVideoDurationSec" to current.maxVideoDurationSec.coerceIn(15L, 300L),
                "watchWheelEnabled" to enabled,
                "watchWheelCycleSec" to cycleSec.coerceIn(10L, 180L),
                "watchWheelPointsPerCycle" to pointsPerCycle.coerceIn(1L, 500L),
                "watchWheelDailyCapCycles" to dailyCapCycles.coerceIn(1L, 500L),
                "updatedBy" to adminUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "admin_settings/global", auth)
    }

    suspend fun assignAdminRole(targetUid: String, email: String, role: String): Result<Unit> = runCatching {
        val adminUid = requireUid()
        db.collection("admins").document(targetUid).set(
            mapOf(
                "userId" to targetUid,
                "email" to email.trim().take(200),
                "role" to role.ifBlank { "moderator" }.take(40),
                "assignedBy" to adminUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "admins/$targetUid", auth)
    }

    suspend fun removeAdminRole(targetUid: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("admins").document(targetUid).delete().await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.DELETE, "admins/$targetUid", auth)
    }
}
