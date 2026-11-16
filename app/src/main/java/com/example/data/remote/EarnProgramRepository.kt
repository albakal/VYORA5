package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.EarnAdminOverview
import com.example.data.model.EarnAdminParticipant
import com.example.data.model.EarnDashboardState
import com.example.data.model.EarnDeviceFlag
import com.example.data.model.EarnFriendProgress
import com.example.data.model.EarnPayoutLog
import com.example.data.model.EarnProgramConfig
import com.example.earn.EarnLogic
import com.example.security.DeviceIntegrityInfo
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EarnProgramRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    private suspend fun loadConfig(): EarnProgramConfig {
        val snap = runCatching { db.collection("admin_settings").document("earn_program").get().await() }.getOrNull()
        if (snap == null || !snap.exists()) return EarnProgramConfig()
        return EarnProgramConfig(
            isEnabled = snap.getBoolean("isEnabled") ?: true,
            rewardEgp = snap.getDouble("rewardEgp") ?: 1500.0,
            rewardPoints = snap.getLong("rewardPoints") ?: 150_000L,
            inviterVideosRequired = (snap.getLong("inviterVideosRequired") ?: 10L).toInt(),
            requiredFriends = (snap.getLong("requiredFriends") ?: 10L).toInt(),
            friendDaysRequired = (snap.getLong("friendDaysRequired") ?: 10L).toInt(),
            friendDailyMinutesRequired = (snap.getLong("friendDailyMinutesRequired") ?: 30L).toInt(),
            friendDailyVideosRequired = (snap.getLong("friendDailyVideosRequired") ?: 2L).toInt()
        )
    }

    suspend fun registerDeviceAndCode(device: DeviceIntegrityInfo): Result<Unit> = runCatching {
        val uid = requireUid()
        val fp = device.deviceFingerprint.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(128)
            .ifBlank { "dev_${uid.take(16)}" }

        val deviceRef = db.collection("earn_devices").document(fp)
        val deviceSnap = runCatching { deviceRef.get().await() }.getOrNull()

        var adminDecision = "pending"
        var deviceEligible = true
        var deviceFlagged = false
        var deviceReason = ""

        if (deviceSnap == null || !deviceSnap.exists()) {
            deviceRef.set(
                mapOf(
                    "deviceFingerprint" to fp,
                    "appSetId" to device.appSetId.take(128),
                    "playIntegrityStatus" to device.playIntegrityStatus.take(80),
                    "firstUserId" to uid,
                    "linkedUserIds" to listOf(uid),
                    "isFlagged" to false,
                    "flagReason" to "",
                    "adminDecision" to "pending",
                    "adminNote" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            val existingLinked = (deviceSnap.get("linkedUserIds") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            val updatedLinked = (existingLinked + uid).distinct().take(20)
            val firstUid = deviceSnap.getString("firstUserId").orEmpty()
            adminDecision = deviceSnap.getString("adminDecision") ?: "pending"

            val multipleAccounts = updatedLinked.size > 1 && adminDecision != "approved_family"
            deviceFlagged = multipleAccounts || (deviceSnap.getBoolean("isFlagged") == true && adminDecision != "approved_family") || adminDecision == "blocked"
            deviceEligible = when (adminDecision) {
                "blocked" -> false
                "approved_family" -> true
                else -> firstUid == uid || updatedLinked.size <= 1
            }
            deviceReason = when {
                adminDecision == "blocked" -> "blocked_by_admin"
                multipleAccounts -> "multiple_accounts_same_device"
                else -> deviceSnap.getString("flagReason").orEmpty()
            }

            deviceRef.update(
                mapOf(
                    "appSetId" to device.appSetId.take(128),
                    "playIntegrityStatus" to device.playIntegrityStatus.take(80),
                    "linkedUserIds" to updatedLinked,
                    "isFlagged" to deviceFlagged,
                    "flagReason" to deviceReason.take(120),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val earnRef = db.collection("earn_program").document(uid)
        val earnSnap = earnRef.get().await()
        if (!earnSnap.exists()) {
            val profileSnap = runCatching { db.collection("profiles").document(uid).get().await() }.getOrNull()
            val username = profileSnap?.getString("username").orEmpty().ifBlank { "creator_${uid.take(6)}" }
            val displayName = profileSnap?.getString("displayName").orEmpty().ifBlank { username }
            val avatarUrl = profileSnap?.getString("avatarUrl").orEmpty()
            val referralCode = generateUniqueCode(uid)

            val codeRef = db.collection("earn_referral_codes").document(referralCode)
            if (!codeRef.get().await().exists()) {
                codeRef.set(
                    mapOf(
                        "code" to referralCode,
                        "userId" to uid,
                        "username" to username.take(50),
                        "deviceFingerprint" to fp,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val todayStr = EarnLogic.cairoToday().toString()
            earnRef.set(
                mapOf(
                    "userId" to uid,
                    "referralCode" to referralCode,
                    "username" to username.take(50),
                    "displayName" to displayName.take(80),
                    "avatarUrl" to avatarUrl.take(2000),
                    "referredByCode" to "",
                    "referredByUid" to "",
                    "personalVideosCount" to 0L,
                    "invitedFriendsCount" to 0L,
                    "completedFriendsCount" to 0L,
                    "status" to if (deviceFlagged || !deviceEligible) "under_review" else "in_progress",
                    "reviewNote" to "",
                    "isDeviceEligible" to deviceEligible,
                    "suspiciousFlag" to deviceFlagged,
                    "suspiciousReason" to deviceReason.take(120),
                    "deviceFingerprint" to fp,
                    "playIntegrityStatus" to device.playIntegrityStatus.take(80),
                    "paidOutAt" to "",
                    "myTodayDate" to todayStr,
                    "myTodaySeconds" to 0L,
                    "myTodayVideos" to 0L,
                    "myConsecutiveDays" to 0L,
                    "qualifyingDates" to emptyList<String>(),
                    "registeredAt" to Instant.now().toString(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            val curStatus = earnSnap.getString("status") ?: "in_progress"
            val nextStatus = if ((deviceFlagged || !deviceEligible) && curStatus == "in_progress") "under_review" else curStatus
            if (curStatus != "approved" && curStatus != "rejected") {
                earnRef.update(
                    mapOf(
                        "isDeviceEligible" to deviceEligible,
                        "suspiciousFlag" to deviceFlagged,
                        "suspiciousReason" to deviceReason.take(120),
                        "deviceFingerprint" to fp,
                        "playIntegrityStatus" to device.playIntegrityStatus.take(80),
                        "status" to nextStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "earn_program", auth)
    }

    suspend fun redeemReferralCode(
        rawCode: String,
        device: DeviceIntegrityInfo
    ): Result<Boolean> = runCatching {
        val uid = requireUid()
        val clean = EarnLogic.sanitizeReferralCode(rawCode)
        require(EarnLogic.isValidReferralCodeFormat(clean)) { "رابط أو كود إحالة غير صالح" }

        registerDeviceAndCode(device).getOrThrow()

        val earnRef = db.collection("earn_program").document(uid)
        val mySnap = earnRef.get().await()
        val existingInviter = mySnap.getString("referredByUid").orEmpty()
        if (existingInviter.isNotBlank()) {
            return@runCatching mySnap.getBoolean("suspiciousFlag") == true
        }

        val codeSnap = db.collection("earn_referral_codes").document(clean).get().await()
        require(codeSnap.exists()) { "كود الدعوة غير موجود" }

        val inviterUid = codeSnap.getString("userId").orEmpty()
        val inviterFp = codeSnap.getString("deviceFingerprint").orEmpty()
        val myFp = device.deviceFingerprint.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(128)
        val deviceEligible = mySnap.getBoolean("isDeviceEligible") ?: true

        val safety = EarnLogic.evaluateReferralDeviceSafety(
            inviterUid = inviterUid,
            friendUid = uid,
            inviterDeviceFingerprint = inviterFp,
            friendDeviceFingerprint = myFp,
            existingSiblingFingerprints = emptySet(),
            friendDeviceEligible = deviceEligible
        )

        require(inviterUid != uid) { "لا يمكنك استخدام رابط الإحالة الخاص بك" }

        val shouldReview = safety.shouldFlagForReview || !safety.allowedToCredit
        earnRef.update(
            mapOf(
                "referredByCode" to clean,
                "referredByUid" to inviterUid,
                "isDeviceEligible" to (deviceEligible && safety.allowedToCredit),
                "suspiciousFlag" to shouldReview,
                "suspiciousReason" to safety.reason.take(120),
                "status" to if (shouldReview) "under_review" else "in_progress",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        shouldReview
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "earn_program", auth)
    }

    suspend fun sendHeartbeat(
        deltaSeconds: Long,
        device: DeviceIntegrityInfo
    ): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        val earnRef = db.collection("earn_program").document(uid)
        val earnSnap = earnRef.get().await()
        if (!earnSnap.exists()) {
            registerDeviceAndCode(device).getOrThrow()
        }
        val latest = earnRef.get().await()
        if (latest.getString("status") == "approved") return@runCatching

        val config = loadConfig()
        val today = EarnLogic.cairoToday()
        val todayStr = today.toString()

        val prevDate = latest.getString("myTodayDate").orEmpty()
        val prevSecs = if (prevDate == todayStr) (latest.getLong("myTodaySeconds") ?: 0L) else 0L
        val nextSecs = (prevSecs + deltaSeconds.coerceIn(1L, 90L)).coerceAtMost(86_400L)

        val videosSnap = runCatching {
            db.collection("videos")
                .whereEqualTo("status", "active")
                .whereEqualTo("userId", uid)
                .get()
                .await()
        }.getOrNull()

        val todayVideosCount = videosSnap?.documents?.count { doc ->
            val ts = doc.getTimestamp("createdAt")
            ts != null && EarnLogic.toCairoDate(ts.toDate().toInstant()) == today
        } ?: 0

        val existingQualifying = (latest.get("qualifyingDates") as? List<*>)
            ?.mapNotNull { runCatching { LocalDate.parse(it as String) }.getOrNull() }
            ?.toMutableSet()
            ?: mutableSetOf()

        if (EarnLogic.doesDayQualify(
                activeSeconds = nextSecs,
                validVideosCount = todayVideosCount,
                requiredMinutes = config.friendDailyMinutesRequired,
                requiredVideos = config.friendDailyVideosRequired
            )
        ) {
            existingQualifying.add(today)
        }

        val sortedQualifyingStrs = existingQualifying.sorted().takeLast(60).map { it.toString() }
        val streak = EarnLogic.calculateConsecutiveStreak(existingQualifying, today)

        earnRef.update(
            mapOf(
                "myTodayDate" to todayStr,
                "myTodaySeconds" to nextSecs,
                "myTodayVideos" to todayVideosCount.toLong(),
                "myConsecutiveDays" to streak.toLong(),
                "qualifyingDates" to sortedQualifyingStrs,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "earn_program", auth)
    }

    suspend fun fetchDashboard(device: DeviceIntegrityInfo): Result<EarnDashboardState> = runCatching {
        val uid = requireUid()
        val earnRef = db.collection("earn_program").document(uid)
        if (!earnRef.get().await().exists()) {
            registerDeviceAndCode(device).getOrThrow()
        }

        val config = loadConfig()
        val today = EarnLogic.cairoToday()
        val todayStr = today.toString()

        val mySnap = earnRef.get().await()
        val activeVideosSnap = db.collection("videos")
            .whereEqualTo("status", "active")
            .whereEqualTo("userId", uid)
            .get()
            .await()

        val personalVideosCount = activeVideosSnap.size()
        val todayVideosCount = activeVideosSnap.documents.count { doc ->
            val ts = doc.getTimestamp("createdAt")
            ts != null && EarnLogic.toCairoDate(ts.toDate().toInstant()) == today
        }

        val friendsSnap = db.collection("earn_program")
            .whereEqualTo("referredByUid", uid)
            .get()
            .await()

        val friendsList = friendsSnap.documents.map { doc ->
            val friendTodayDate = doc.getString("myTodayDate").orEmpty()
            val todaySec = if (friendTodayDate == todayStr) (doc.getLong("myTodaySeconds") ?: 0L) else 0L
            val friendTodayVids = if (friendTodayDate == todayStr) (doc.getLong("myTodayVideos") ?: 0L).toInt() else 0
            val completedDays = (doc.getLong("myConsecutiveDays") ?: 0L).toInt()
            val friendEligible = doc.getBoolean("isDeviceEligible") ?: true
            val friendSuspicious = doc.getBoolean("suspiciousFlag") ?: false
            val isCompleted = completedDays >= config.friendDaysRequired && friendEligible && !friendSuspicious

            EarnFriendProgress(
                userId = doc.getString("userId") ?: doc.id,
                username = doc.getString("username").orEmpty(),
                displayName = doc.getString("displayName").orEmpty(),
                avatarUrl = doc.getString("avatarUrl").orEmpty(),
                registeredAt = doc.getString("registeredAt").orEmpty(),
                completedDays = completedDays,
                requiredDays = config.friendDaysRequired,
                todayActiveMinutes = (todaySec / 60L).toInt(),
                todayActiveSeconds = todaySec,
                requiredDailyMinutes = config.friendDailyMinutesRequired,
                todayValidVideos = friendTodayVids,
                requiredDailyVideos = config.friendDailyVideosRequired,
                isCompleted = isCompleted,
                isDeviceEligible = friendEligible,
                suspiciousFlag = friendSuspicious,
                suspiciousReason = doc.getString("suspiciousReason").orEmpty()
            )
        }

        val completedFriendsCount = friendsList.count { it.isCompleted }
        val invitedFriendsCount = friendsList.size
        val pct = EarnLogic.calculateOverallProgressPercent(
            personalVideosCount = personalVideosCount,
            requiredPersonalVideos = config.inviterVideosRequired,
            completedFriendsCount = completedFriendsCount,
            requiredFriends = config.requiredFriends
        ).toDouble()

        val curStatus = mySnap.getString("status") ?: "in_progress"
        val isEligibleDevice = mySnap.getBoolean("isDeviceEligible") ?: true
        val suspicious = mySnap.getBoolean("suspiciousFlag") ?: false

        val resolvedStatus = when {
            curStatus == "approved" || curStatus == "rejected" -> curStatus
            suspicious || !isEligibleDevice -> "under_review"
            personalVideosCount >= config.inviterVideosRequired && completedFriendsCount >= config.requiredFriends -> "eligible"
            else -> "in_progress"
        }

        if (curStatus != "approved") {
            runCatching {
                earnRef.update(
                    mapOf(
                        "personalVideosCount" to personalVideosCount.toLong(),
                        "invitedFriendsCount" to invitedFriendsCount.toLong(),
                        "completedFriendsCount" to completedFriendsCount.toLong(),
                        "status" to resolvedStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        val myTodayDate = mySnap.getString("myTodayDate").orEmpty()
        val myTodaySeconds = if (myTodayDate == todayStr) (mySnap.getLong("myTodaySeconds") ?: 0L) else 0L
        val myConsecutiveDays = (mySnap.getLong("myConsecutiveDays") ?: 0L).toInt()

        EarnDashboardState(
            config = config,
            referralCode = mySnap.getString("referralCode").orEmpty(),
            referredByCode = mySnap.getString("referredByCode").orEmpty(),
            referredByUid = mySnap.getString("referredByUid").orEmpty(),
            personalVideosCount = personalVideosCount,
            invitedFriendsCount = invitedFriendsCount,
            completedFriendsCount = completedFriendsCount,
            myTodayMinutes = (myTodaySeconds / 60L).toInt(),
            myTodayVideos = todayVideosCount,
            myConsecutiveDays = myConsecutiveDays,
            progressPercent = pct,
            status = resolvedStatus,
            reviewNote = mySnap.getString("reviewNote").orEmpty(),
            isDeviceEligible = isEligibleDevice,
            suspiciousFlag = suspicious,
            suspiciousReason = mySnap.getString("suspiciousReason").orEmpty(),
            cairoDate = todayStr,
            friends = friendsList
        )
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "earn_program/${auth.currentUser?.uid}", auth)
    }

    suspend fun fetchAdminOverview(): Result<EarnAdminOverview> = runCatching {
        requireUid()
        val config = loadConfig()
        val participantsSnap = db.collection("earn_program").limit(100).get().await()
        val devicesSnap = db.collection("earn_devices").limit(100).get().await()
        val logsSnap = db.collection("earn_payout_logs").limit(100).get().await()

        val allDocs = participantsSnap.documents
        val participants = allDocs.map { doc ->
            val uid = doc.getString("userId") ?: doc.id
            val email = runCatching {
                db.collection("users").document(uid).collection("private").document("info").get().await()
                    .getString("email").orEmpty()
            }.getOrDefault("")
            EarnAdminParticipant(
                userId = uid,
                username = doc.getString("username").orEmpty(),
                displayName = doc.getString("displayName").orEmpty(),
                email = email,
                avatarUrl = doc.getString("avatarUrl").orEmpty(),
                referralCode = doc.getString("referralCode").orEmpty(),
                referredByCode = doc.getString("referredByCode").orEmpty(),
                personalVideosCount = (doc.getLong("personalVideosCount") ?: 0L).toInt(),
                invitedFriendsCount = (doc.getLong("invitedFriendsCount") ?: 0L).toInt(),
                completedFriendsCount = (doc.getLong("completedFriendsCount") ?: 0L).toInt(),
                status = doc.getString("status") ?: "in_progress",
                reviewNote = doc.getString("reviewNote").orEmpty(),
                isDeviceEligible = doc.getBoolean("isDeviceEligible") ?: true,
                suspiciousFlag = doc.getBoolean("suspiciousFlag") ?: false,
                suspiciousReason = doc.getString("suspiciousReason").orEmpty(),
                deviceFingerprint = doc.getString("deviceFingerprint").orEmpty(),
                playIntegrityStatus = doc.getString("playIntegrityStatus").orEmpty(),
                paidOutAt = doc.getString("paidOutAt").orEmpty(),
                friends = emptyList()
            )
        }

        val devices = devicesSnap.documents.map { doc ->
            val linked = (doc.get("linkedUserIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
            val ts = doc.getTimestamp("updatedAt")
            EarnDeviceFlag(
                deviceFingerprint = doc.getString("deviceFingerprint") ?: doc.id,
                appSetId = doc.getString("appSetId").orEmpty(),
                playIntegrityStatus = doc.getString("playIntegrityStatus").orEmpty(),
                firstUserId = doc.getString("firstUserId").orEmpty(),
                linkedUserIds = linked,
                isFlagged = doc.getBoolean("isFlagged") ?: false,
                flagReason = doc.getString("flagReason").orEmpty(),
                adminDecision = doc.getString("adminDecision") ?: "pending",
                adminNote = doc.getString("adminNote").orEmpty(),
                updatedAt = ts?.toDate()?.toInstant()?.toString().orEmpty()
            )
        }

        val logs = logsSnap.documents.sortedByDescending {
            it.getTimestamp("createdAt") ?: Timestamp(0, 0)
        }.map { doc ->
            val ts = doc.getTimestamp("createdAt")
            EarnPayoutLog(
                id = doc.id,
                userId = doc.getString("userId").orEmpty(),
                username = doc.getString("username").orEmpty(),
                rewardEgp = doc.getDouble("rewardEgp") ?: config.rewardEgp,
                rewardPoints = doc.getLong("rewardPoints") ?: config.rewardPoints,
                decision = doc.getString("decision").orEmpty(),
                note = doc.getString("note").orEmpty(),
                reviewedBy = doc.getString("reviewedBy").orEmpty(),
                createdAt = ts?.toDate()?.toInstant()?.toString().orEmpty()
            )
        }

        EarnAdminOverview(
            config = config,
            participants = participants,
            flaggedDevices = devices,
            payoutLogs = logs
        )
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "earn_program", auth)
    }

    suspend fun adminUpdateConfig(config: EarnProgramConfig): Result<Unit> = runCatching {
        requireUid()
        db.collection("admin_settings").document("earn_program").set(
            mapOf(
                "isEnabled" to config.isEnabled,
                "rewardEgp" to config.rewardEgp.coerceAtLeast(1.0),
                "rewardPoints" to config.rewardPoints.coerceAtLeast(1L),
                "inviterVideosRequired" to config.inviterVideosRequired.coerceAtLeast(1).toLong(),
                "requiredFriends" to config.requiredFriends.coerceAtLeast(1).toLong(),
                "friendDaysRequired" to config.friendDaysRequired.coerceAtLeast(1).toLong(),
                "friendDailyMinutesRequired" to config.friendDailyMinutesRequired.coerceAtLeast(1).toLong(),
                "friendDailyVideosRequired" to config.friendDailyVideosRequired.coerceAtLeast(1).toLong(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "admin_settings/earn_program", auth)
    }

    suspend fun adminReviewDevice(
        deviceFingerprint: String,
        decision: String,
        note: String
    ): Result<Unit> = runCatching {
        requireUid()
        val resolvedDecision = if (decision in listOf("pending", "approved_family", "blocked")) decision else "pending"
        val devRef = db.collection("earn_devices").document(deviceFingerprint)
        val devSnap = devRef.get().await()
        require(devSnap.exists()) { "Device record not found" }
        val linked = (devSnap.get("linkedUserIds") as? List<*>)?.mapNotNull { it as? String }.orEmpty()

        devRef.update(
            mapOf(
                "adminDecision" to resolvedDecision,
                "adminNote" to note.trim().take(300),
                "isFlagged" to (resolvedDecision == "blocked"),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        val eligible = resolvedDecision == "approved_family"
        val suspicious = resolvedDecision != "approved_family"
        linked.forEach { targetUid ->
            runCatching {
                val eRef = db.collection("earn_program").document(targetUid)
                val eSnap = eRef.get().await()
                if (eSnap.exists() && eSnap.getString("status") != "approved") {
                    eRef.update(
                        mapOf(
                            "isDeviceEligible" to eligible,
                            "suspiciousFlag" to suspicious,
                            "reviewNote" to note.trim().take(400),
                            "status" to if (eligible) "in_progress" else "under_review",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "earn_devices/$deviceFingerprint", auth)
    }

    suspend fun adminReviewPayout(
        targetUid: String,
        approve: Boolean,
        note: String
    ): Result<Unit> = runCatching {
        val adminUid = requireUid()
        val config = loadConfig()
        val settingsSnap = runCatching { db.collection("admin_settings").document("global").get().await() }.getOrNull()
        val pointVal = settingsSnap?.getDouble("pointValueUsd") ?: 0.01
        val rewardPoints = config.rewardPoints.coerceAtLeast(1L)
        val rewardUsd = rewardPoints * pointVal

        val earnRef = db.collection("earn_program").document(targetUid)
        val walletRef = db.collection("wallets").document(targetUid)
        val logRef = db.collection("earn_payout_logs").document(UUID.randomUUID().toString().replace("-", ""))
        val txRef = walletRef.collection("transactions").document(EarnLogic.stableSha256Id(EarnLogic.payoutTransactionRef(targetUid)))

        db.runTransaction { tx ->
            val earnSnap = tx.get(earnRef)
            require(earnSnap.exists()) { "Earn record not found" }
            require(earnSnap.getString("paidOutAt").isNullOrBlank()) { "Reward already paid out" }

            val walletSnap = if (approve) tx.get(walletRef) else null
            val existingTx = if (approve) tx.get(txRef) else null
            val username = earnSnap.getString("username").orEmpty()
            val paidAt = if (approve) Instant.now().toString() else ""

            tx.update(
                earnRef,
                mapOf(
                    "status" to if (approve) "approved" else "rejected",
                    "reviewNote" to note.trim().take(400),
                    "paidOutAt" to paidAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            if (approve && walletSnap != null && (existingTx == null || !existingTx.exists())) {
                val curPts = walletSnap.getLong("points") ?: 0L
                val curBal = walletSnap.getDouble("balanceUsd") ?: 0.0
                val curTot = walletSnap.getDouble("totalEarnedUsd") ?: 0.0
                tx.set(
                    walletRef,
                    mapOf(
                        "userId" to targetUid,
                        "points" to curPts + rewardPoints,
                        "balanceUsd" to curBal + rewardUsd,
                        "totalEarnedUsd" to curTot + rewardUsd,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    txRef,
                    mapOf(
                        "userId" to targetUid,
                        "type" to "earn_program_reward",
                        "pointsDelta" to rewardPoints,
                        "amountDelta" to rewardUsd,
                        "description" to "Earn With Us reward (${config.rewardEgp.toInt()} EGP)",
                        "status" to "completed",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            tx.set(
                logRef,
                mapOf(
                    "userId" to targetUid,
                    "username" to username.take(50),
                    "rewardEgp" to config.rewardEgp,
                    "rewardPoints" to rewardPoints,
                    "decision" to if (approve) "approved" else "rejected",
                    "note" to note.trim().take(300),
                    "reviewedBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()

        runCatching {
            val notifId = UUID.randomUUID().toString().replace("-", "")
            db.collection("notifications").document(notifId).set(
                mapOf(
                    "recipientId" to targetUid,
                    "actorId" to adminUid,
                    "actorUsername" to "vyora_admin",
                    "actorAvatarUrl" to "",
                    "type" to "reward",
                    "targetId" to targetUid,
                    "message" to if (approve) "تم اعتماد مكافأتك وإضافة $rewardPoints نقطة إلى محفظتك!" else "تمت مراجعة طلب المكافأة: ${note.take(120)}",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "earn_program/$targetUid", auth)
    }

    private fun generateUniqueCode(uid: String): String {
        val raw = "VY${uid.uppercase().replace(Regex("[^A-Z0-9]"), "").take(8)}"
        return if (raw.length >= 6) raw else raw.padEnd(6, '9')
    }

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
