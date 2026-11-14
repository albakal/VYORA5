package com.example.data.model

import java.time.Instant

data class VyoraTimestamp(
    val epochMillis: Long = System.currentTimeMillis()
) : Comparable<VyoraTimestamp> {
    override fun compareTo(other: VyoraTimestamp): Int =
        epochMillis.compareTo(other.epochMillis)

    companion object {
        val ZERO = VyoraTimestamp(0L)

        fun parse(isoOrNull: String?): VyoraTimestamp? {
            val raw = isoOrNull?.trim().orEmpty()
            if (raw.isEmpty() || raw == "null") return null
            return try {
                VyoraTimestamp(Instant.parse(raw).toEpochMilli())
            } catch (_: Exception) {
                raw.toLongOrNull()?.let { VyoraTimestamp(it) }
            }
        }
    }
}

data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val isPrivate: Boolean = false,
    val isBanned: Boolean = false,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val likesCount: Long = 0L,
    val interests: List<String> = emptyList(),
    val language: String = "ar",
    val themeMode: String = "dark",
    val createdAt: VyoraTimestamp? = null,
    val updatedAt: VyoraTimestamp? = null
)

data class FollowEdge(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val createdAt: VyoraTimestamp? = null
)

data class FollowRequest(
    val id: String = "",
    val requesterId: String = "",
    val requesterUsername: String = "",
    val requesterAvatarUrl: String = "",
    val targetId: String = "",
    val status: String = "pending",
    val createdAt: VyoraTimestamp? = null,
    val updatedAt: VyoraTimestamp? = null
)

data class VideoPost(
    val id: String = "",
    val userId: String = "",
    val creatorUsername: String = "",
    val creatorAvatarUrl: String = "",
    val videoUrl: String = "",
    val thumbnailUrl: String = "",
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val soundId: String = "",
    val soundTitle: String = "",
    val durationSec: Long = 15L,
    val viewsCount: Long = 0L,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val sharesCount: Long = 0L,
    val bookmarksCount: Long = 0L,
    val status: String = "active",
    val createdAt: VyoraTimestamp? = null,
    val updatedAt: VyoraTimestamp? = null
)

data class VideoComment(
    val id: String = "",
    val videoId: String = "",
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val text: String = "",
    val createdAt: VyoraTimestamp? = null
)

data class HashtagItem(
    val id: String = "",
    val tag: String = "",
    val videosCount: Long = 0L,
    val updatedAt: VyoraTimestamp? = null
)

data class SoundTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val audioUrl: String = "",
    val coverUrl: String = "",
    val durationSec: Long = 30L,
    val isLicensed: Boolean = true,
    val creatorId: String = "",
    val usesCount: Long = 0L,
    val createdAt: VyoraTimestamp? = null
)

data class Conversation(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastSenderId: String = "",
    val lastMessageAt: VyoraTimestamp? = null,
    val createdAt: VyoraTimestamp? = null,
    val updatedAt: VyoraTimestamp? = null
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val participantIds: List<String> = emptyList(),
    val type: String = "text",
    val text: String = "",
    val mediaUrl: String = "",
    val audioDurationMs: Long = 0L,
    val readBy: List<String> = emptyList(),
    val createdAt: VyoraTimestamp? = null
)

data class AppNotification(
    val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val actorUsername: String = "",
    val actorAvatarUrl: String = "",
    val type: String = "like",
    val targetId: String = "",
    val message: String = "",
    val isRead: Boolean = false,
    val createdAt: VyoraTimestamp? = null
)

data class LiveStream(
    val id: String = "",
    val hostId: String = "",
    val hostUsername: String = "",
    val hostAvatarUrl: String = "",
    val channelName: String = "",
    val title: String = "",
    val isActive: Boolean = true,
    val viewerCount: Long = 1L,
    val likesCount: Long = 0L,
    val startedAt: VyoraTimestamp? = null,
    val endedAt: VyoraTimestamp? = null
)

data class LiveComment(
    val id: String = "",
    val streamId: String = "",
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    val createdAt: VyoraTimestamp? = null
)

data class ModerationReport(
    val id: String = "",
    val reporterId: String = "",
    val targetType: String = "video",
    val targetId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "pending",
    val reviewedBy: String = "",
    val createdAt: VyoraTimestamp? = null,
    val updatedAt: VyoraTimestamp? = null
)

data class Wallet(
    val id: String = "",
    val userId: String = "",
    val points: Long = 0L,
    val balanceUsd: Double = 0.0,
    val totalEarnedUsd: Double = 0.0,
    val updatedAt: VyoraTimestamp? = null
)

data class WalletTransaction(
    val id: String = "",
    val userId: String = "",
    val type: String = "view_reward",
    val pointsDelta: Long = 0L,
    val amountDelta: Double = 0.0,
    val description: String = "",
    val status: String = "completed",
    val createdAt: VyoraTimestamp? = null
)

data class WithdrawalRequest(
    val id: String = "",
    val userId: String = "",
    val amountUsd: Double = 0.0,
    val pointsSpent: Long = 0L,
    val method: String = "",
    val accountDetails: String = "",
    val status: String = "pending",
    val reviewedBy: String = "",
    val createdAt: VyoraTimestamp? = null,
    val updatedAt: VyoraTimestamp? = null
)

data class AdminSettings(
    val id: String = "",
    val pointValueUsd: Double = 0.01,
    val minWithdrawalUsd: Double = 10.0,
    val rewardsEnabled: Boolean = true,
    val maxVideoDurationSec: Long = 180L,
    val updatedBy: String = "",
    val updatedAt: VyoraTimestamp? = null
)

data class AdminRole(
    val id: String = "",
    val userId: String = "",
    val email: String = "",
    val role: String = "moderator",
    val assignedBy: String = "",
    val createdAt: VyoraTimestamp? = null
)
