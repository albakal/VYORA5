package com.example.data.model

data class EarnProgramConfig(
    val isEnabled: Boolean = true,
    val rewardEgp: Double = 1500.0,
    val rewardPoints: Long = 150_000L,
    val inviterVideosRequired: Int = 10,
    val requiredFriends: Int = 10,
    val friendDaysRequired: Int = 10,
    val friendDailyMinutesRequired: Int = 30,
    val friendDailyVideosRequired: Int = 2
)

data class EarnFriendProgress(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val registeredAt: String = "",
    val completedDays: Int = 0,
    val requiredDays: Int = 10,
    val todayActiveMinutes: Int = 0,
    val todayActiveSeconds: Long = 0L,
    val requiredDailyMinutes: Int = 30,
    val todayValidVideos: Int = 0,
    val requiredDailyVideos: Int = 2,
    val isCompleted: Boolean = false,
    val isDeviceEligible: Boolean = true,
    val suspiciousFlag: Boolean = false,
    val suspiciousReason: String = ""
)

data class EarnDashboardState(
    val config: EarnProgramConfig = EarnProgramConfig(),
    val referralCode: String = "",
    val referredByCode: String = "",
    val referredByUid: String = "",
    val personalVideosCount: Int = 0,
    val invitedFriendsCount: Int = 0,
    val completedFriendsCount: Int = 0,
    val myTodayMinutes: Int = 0,
    val myTodayVideos: Int = 0,
    val myConsecutiveDays: Int = 0,
    val progressPercent: Double = 0.0,
    val status: String = "in_progress",
    val reviewNote: String = "",
    val isDeviceEligible: Boolean = true,
    val suspiciousFlag: Boolean = false,
    val suspiciousReason: String = "",
    val cairoDate: String = "",
    val friends: List<EarnFriendProgress> = emptyList()
)

data class EarnAdminParticipant(
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val referralCode: String = "",
    val referredByCode: String = "",
    val personalVideosCount: Int = 0,
    val invitedFriendsCount: Int = 0,
    val completedFriendsCount: Int = 0,
    val status: String = "in_progress",
    val reviewNote: String = "",
    val isDeviceEligible: Boolean = true,
    val suspiciousFlag: Boolean = false,
    val suspiciousReason: String = "",
    val deviceFingerprint: String = "",
    val playIntegrityStatus: String = "",
    val paidOutAt: String = "",
    val friends: List<EarnFriendProgress> = emptyList()
)

data class EarnDeviceFlag(
    val deviceFingerprint: String = "",
    val appSetId: String = "",
    val playIntegrityStatus: String = "",
    val firstUserId: String = "",
    val linkedUserIds: List<String> = emptyList(),
    val isFlagged: Boolean = false,
    val flagReason: String = "",
    val adminDecision: String = "pending",
    val adminNote: String = "",
    val updatedAt: String = ""
)

data class EarnPayoutLog(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val rewardEgp: Double = 1500.0,
    val rewardPoints: Long = 150_000L,
    val decision: String = "",
    val note: String = "",
    val reviewedBy: String = "",
    val createdAt: String = ""
)

data class EarnAdminOverview(
    val config: EarnProgramConfig = EarnProgramConfig(),
    val participants: List<EarnAdminParticipant> = emptyList(),
    val flaggedDevices: List<EarnDeviceFlag> = emptyList(),
    val payoutLogs: List<EarnPayoutLog> = emptyList()
)
