package com.example.data.remote

import android.content.Context
import com.example.BuildConfig
import com.example.R
import com.example.data.model.AdminSettings
import com.example.data.model.WatchWheelClaimResult
import com.example.data.model.WatchWheelStatus
import com.example.earn.EarnLogic
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AgoraLiveSession(
    val appId: String,
    val channelName: String,
    val token: String,
    val rtcUid: Int
)

data class FirebaseCloudSummary(
    val points: Long = 0L,
    val balanceUsd: Double = 0.0,
    val totalEarnedUsd: Double = 0.0,
    val transactionsCount: Int = 0,
    val videosCount: Int = 0,
    val synced: Boolean = true
)

class FirebaseBackendRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    suspend fun fetchAgoraLiveSession(streamId: String): Result<AgoraLiveSession> = runCatching {
        val uid = requireUid()
        val appId = BuildConfig.AGORA_APP_ID.trim()
        require(appId.isNotBlank() && !appId.contains("YOUR_", ignoreCase = true)) {
            "AGORA_APP_ID is not configured in Secrets"
        }
        val streamSnap = db.collection("live_streams").document(streamId).get().await()
        val channelName = streamSnap.getString("channelName")
            ?.takeIf { it.isNotBlank() }
            ?: "vyora_$streamId"
        val rtcUid = (uid.hashCode() and 0x7FFFFFFF).coerceAtLeast(1)
        AgoraLiveSession(
            appId = appId,
            channelName = channelName,
            token = "",
            rtcUid = rtcUid
        )
    }.onFailure { err ->
        if (err is CancellationException) throw err
        if (err !is IllegalArgumentException) {
            FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "live_streams/$streamId", auth)
        }
    }

    suspend fun fetchWalletAccountingSummary(): Result<FirebaseCloudSummary> = runCatching {
        val uid = requireUid()
        val walletSnap = db.collection("wallets").document(uid).get().await()
        val txSnap = db.collection("wallets").document(uid).collection("transactions").get().await()
        val videosSnap = db.collection("videos").whereEqualTo("status", "active").get().await()

        FirebaseCloudSummary(
            points = walletSnap.getLong("points") ?: 0L,
            balanceUsd = walletSnap.getDouble("balanceUsd") ?: 0.0,
            totalEarnedUsd = walletSnap.getDouble("totalEarnedUsd") ?: 0.0,
            transactionsCount = txSnap.size(),
            videosCount = videosSnap.size(),
            synced = true
        )
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "wallets/${auth.currentUser?.uid}", auth)
    }

    suspend fun recordVideoViewServer(videoId: String): Result<Unit> = runCatching {
        requireUid()
        val videoRef = db.collection("videos").document(videoId)

        db.runTransaction { tx ->
            val videoSnap = tx.get(videoRef)
            if (!videoSnap.exists() || videoSnap.getString("status") != "active") {
                return@runTransaction
            }
            val currentViews = videoSnap.getLong("viewsCount") ?: 0L
            tx.update(
                videoRef,
                mapOf(
                    "viewsCount" to currentViews + 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "videos/$videoId", auth)
    }

    suspend fun submitWithdrawalServer(
        amountUsd: Double,
        method: String,
        accountDetails: String
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val settingsSnap = runCatching { db.collection("admin_settings").document("global").get().await() }.getOrNull()
        val settings = settingsSnap?.toObject(
            AdminSettings::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: AdminSettings(id = "global")

        val cleanMethod = method.trim().take(60)
        val cleanDetails = accountDetails.trim().take(200)
        require(amountUsd >= settings.minWithdrawalUsd) {
            "Minimum withdrawal is $${settings.minWithdrawalUsd}"
        }
        require(cleanMethod.isNotEmpty() && cleanDetails.isNotEmpty()) {
            "Enter valid payout method and details"
        }

        val pointsSpent = (amountUsd / settings.pointValueUsd.coerceAtLeast(0.0001))
            .toLong()
            .coerceAtLeast(1L)

        val walletRef = db.collection("wallets").document(uid)
        val txRef = walletRef.collection("transactions").document(UUID.randomUUID().toString().replace("-", ""))
        val withdrawalRef = db.collection("withdrawals").document(UUID.randomUUID().toString().replace("-", ""))

        db.runTransaction { tx ->
            val wSnap = tx.get(walletRef)
            val currentBal = wSnap.getDouble("balanceUsd") ?: 0.0
            val currentPts = wSnap.getLong("points") ?: 0L
            val totalEarned = wSnap.getDouble("totalEarnedUsd") ?: 0.0
            require(currentBal >= amountUsd) { "Insufficient balance" }

            val nextBal = (currentBal - amountUsd).coerceAtLeast(0.0)
            val nextPts = (currentPts - pointsSpent).coerceAtLeast(0L)

            tx.update(
                walletRef,
                mapOf(
                    "userId" to uid,
                    "points" to nextPts,
                    "balanceUsd" to nextBal,
                    "totalEarnedUsd" to totalEarned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            tx.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "type" to "withdrawal_request",
                    "pointsDelta" to -pointsSpent,
                    "amountDelta" to -amountUsd,
                    "description" to "$cleanMethod • $cleanDetails",
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            tx.set(
                withdrawalRef,
                mapOf(
                    "userId" to uid,
                    "amountUsd" to amountUsd,
                    "pointsSpent" to pointsSpent,
                    "method" to cleanMethod,
                    "accountDetails" to cleanDetails,
                    "status" to "pending",
                    "reviewedBy" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "withdrawals", auth)
    }

    suspend fun adminReviewWithdrawalServer(
        requestId: String,
        approve: Boolean
    ): Result<Unit> = runCatching {
        val adminUid = requireUid()
        val reqRef = db.collection("withdrawals").document(requestId)

        db.runTransaction { tx ->
            val reqSnap = tx.get(reqRef)
            require(reqSnap.exists()) { "Withdrawal request not found" }
            require(reqSnap.getString("status") == "pending") { "Withdrawal already reviewed" }
            val targetUid = reqSnap.getString("userId").orEmpty()
            val amountUsd = reqSnap.getDouble("amountUsd") ?: 0.0
            val pointsSpent = reqSnap.getLong("pointsSpent") ?: 0L

            val walletRef = db.collection("wallets").document(targetUid)
            val wSnap = if (!approve) tx.get(walletRef) else null

            tx.update(
                reqRef,
                mapOf(
                    "status" to if (approve) "approved" else "rejected",
                    "reviewedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (!approve && wSnap != null && targetUid.isNotBlank()) {
                val curPts = wSnap.getLong("points") ?: 0L
                val curBal = wSnap.getDouble("balanceUsd") ?: 0.0
                val curTotal = wSnap.getDouble("totalEarnedUsd") ?: 0.0
                tx.set(
                    walletRef,
                    mapOf(
                        "userId" to targetUid,
                        "points" to curPts + pointsSpent,
                        "balanceUsd" to curBal + amountUsd,
                        "totalEarnedUsd" to curTotal,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val refundTxRef = walletRef.collection("transactions")
                    .document(UUID.randomUUID().toString().replace("-", ""))
                tx.set(
                    refundTxRef,
                    mapOf(
                        "userId" to targetUid,
                        "type" to "withdrawal_refund",
                        "pointsDelta" to pointsSpent,
                        "amountDelta" to amountUsd,
                        "description" to "Withdrawal request rejected & refunded",
                        "status" to "completed",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "withdrawals/$requestId", auth)
    }

    suspend fun adminGrantPointsServer(
        targetUid: String,
        pointsDelta: Long,
        reason: String
    ): Result<Unit> = runCatching {
        requireUid()
        val settingsSnap = runCatching { db.collection("admin_settings").document("global").get().await() }.getOrNull()
        val pointVal = settingsSnap?.getDouble("pointValueUsd") ?: 0.01
        val amountDelta = pointsDelta * pointVal

        val walletRef = db.collection("wallets").document(targetUid)
        val txRef = walletRef.collection("transactions").document(UUID.randomUUID().toString().replace("-", ""))

        db.runTransaction { tx ->
            val snap = tx.get(walletRef)
            val curPts = snap.getLong("points") ?: 0L
            val curBal = snap.getDouble("balanceUsd") ?: 0.0
            val curTot = snap.getDouble("totalEarnedUsd") ?: 0.0
            val nextPts = (curPts + pointsDelta).coerceAtLeast(0L)
            val nextBal = (curBal + amountDelta).coerceAtLeast(0.0)
            val nextTot = if (amountDelta > 0) curTot + amountDelta else curTot

            tx.set(
                walletRef,
                mapOf(
                    "userId" to targetUid,
                    "points" to nextPts,
                    "balanceUsd" to nextBal,
                    "totalEarnedUsd" to nextTot,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                txRef,
                mapOf(
                    "userId" to targetUid,
                    "type" to "admin_grant",
                    "pointsDelta" to pointsDelta,
                    "amountDelta" to amountDelta,
                    "description" to reason.ifBlank { "Admin adjustment" }.take(300),
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "wallets/$targetUid", auth)
    }

    suspend fun fetchWatchWheelStatus(): Result<WatchWheelStatus> = runCatching {
        val uid = requireUid()
        val settingsSnap = runCatching { db.collection("admin_settings").document("global").get().await() }.getOrNull()
        val settings = settingsSnap?.toObject(
            AdminSettings::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: AdminSettings(id = "global")

        val dateKey = EarnLogic.cairoToday().toString()
        val dailyId = "${uid}_$dateKey"
        val dailySnap = runCatching { db.collection("watch_wheel_daily").document(dailyId).get().await() }.getOrNull()
        val todayCycles = dailySnap?.getLong("cyclesUsed") ?: 0L

        WatchWheelStatus(
            enabled = settings.watchWheelEnabled,
            cycleSec = settings.watchWheelCycleSec,
            pointsPerCycle = settings.watchWheelPointsPerCycle,
            dailyCapCycles = settings.watchWheelDailyCapCycles,
            todayCycles = todayCycles
        )
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "watch_wheel_daily", auth)
    }

    suspend fun claimWatchWheelReward(
        videoId: String = "",
        watchedSec: Long = 30L
    ): Result<WatchWheelClaimResult> = runCatching {
        val uid = requireUid()
        val settingsSnap = runCatching { db.collection("admin_settings").document("global").get().await() }.getOrNull()
        val settings = settingsSnap?.toObject(
            AdminSettings::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ) ?: AdminSettings(id = "global")

        if (!settings.watchWheelEnabled) {
            return@runCatching WatchWheelClaimResult(
                awarded = false,
                reason = "disabled",
                pointsDelta = 0L,
                dailyCapCycles = settings.watchWheelDailyCapCycles
            )
        }

        val dateKey = EarnLogic.cairoToday().toString()
        val dailyRef = db.collection("watch_wheel_daily").document("${uid}_$dateKey")
        val walletRef = db.collection("wallets").document(uid)
        val txRef = walletRef.collection("transactions").document(UUID.randomUUID().toString().replace("-", ""))

        val pointsPerCycle = settings.watchWheelPointsPerCycle.coerceIn(1L, 500L)
        val amountDelta = (pointsPerCycle * settings.pointValueUsd).coerceAtMost(10.0)
        val capCycles = settings.watchWheelDailyCapCycles.coerceIn(1L, 500L)

        db.runTransaction { tx ->
            val dailySnap = tx.get(dailyRef)
            val currentCycles = dailySnap.getLong("cyclesUsed") ?: 0L
            val walletSnap = tx.get(walletRef)
            val currentPts = walletSnap.getLong("points") ?: 0L

            if (currentCycles >= capCycles) {
                return@runTransaction WatchWheelClaimResult(
                    awarded = false,
                    reason = "daily_cap_reached",
                    pointsDelta = 0L,
                    todayCycles = currentCycles,
                    dailyCapCycles = capCycles,
                    walletPoints = currentPts
                )
            }

            val nextCycles = currentCycles + 1L
            if (!dailySnap.exists()) {
                tx.set(
                    dailyRef,
                    mapOf(
                        "userId" to uid,
                        "dateKey" to dateKey,
                        "cyclesUsed" to 1L,
                        "lastClaimedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                tx.update(
                    dailyRef,
                    mapOf(
                        "cyclesUsed" to nextCycles,
                        "lastClaimedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val currentBal = walletSnap.getDouble("balanceUsd") ?: 0.0
            val currentTot = walletSnap.getDouble("totalEarnedUsd") ?: 0.0
            val nextPts = currentPts + pointsPerCycle

            tx.set(
                walletRef,
                mapOf(
                    "userId" to uid,
                    "points" to nextPts,
                    "balanceUsd" to currentBal + amountDelta,
                    "totalEarnedUsd" to currentTot + amountDelta,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            tx.set(
                txRef,
                mapOf(
                    "userId" to uid,
                    "type" to "watch_wheel",
                    "pointsDelta" to pointsPerCycle,
                    "amountDelta" to amountDelta,
                    "description" to "Watch wheel reward (${watchedSec}s${if (videoId.isNotBlank()) " • $videoId" else ""})",
                    "status" to "completed",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            WatchWheelClaimResult(
                awarded = true,
                reason = "awarded",
                pointsDelta = pointsPerCycle,
                todayCycles = nextCycles,
                dailyCapCycles = capCycles,
                walletPoints = nextPts
            )
        }.await()
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "watch_wheel_daily", auth)
    }
}
