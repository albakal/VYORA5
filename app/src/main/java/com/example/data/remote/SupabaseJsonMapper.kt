package com.example.data.remote

import com.example.data.model.AdminRole
import com.example.data.model.AdminSettings
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.FollowEdge
import com.example.data.model.FollowRequest
import com.example.data.model.HashtagItem
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.ModerationReport
import com.example.data.model.SoundTrack
import com.example.data.model.UserProfile
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.data.model.VyoraTimestamp
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.toObjectList(): List<JSONObject> =
    List(length()) { idx -> getJSONObject(idx) }

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { idx -> optString(idx) }.filter { it.isNotBlank() }
}

internal fun JSONObject.toUserProfile(): UserProfile {
    val uid = optString("user_id")
    return UserProfile(
        id = uid,
        userId = uid,
        username = optString("username"),
        displayName = optString("display_name"),
        bio = optString("bio"),
        avatarUrl = optString("avatar_url"),
        isPrivate = optBoolean("is_private", false),
        isBanned = optBoolean("is_banned", false),
        followersCount = optLong("followers_count", 0L),
        followingCount = optLong("following_count", 0L),
        likesCount = optLong("likes_count", 0L),
        interests = optJSONArray("interests").toStringList(),
        language = optString("language").ifBlank { "ar" },
        themeMode = optString("theme_mode").ifBlank { "dark" },
        createdAt = VyoraTimestamp.parse(optString("created_at")),
        updatedAt = VyoraTimestamp.parse(optString("updated_at"))
    )
}

internal fun JSONObject.toFollowEdge(isFollowingEdge: Boolean): FollowEdge {
    val targetUid = if (isFollowingEdge) optString("following_id") else optString("follower_id")
    val username = if (isFollowingEdge) optString("following_username") else optString("follower_username")
    val avatar = if (isFollowingEdge) optString("following_avatar_url") else optString("follower_avatar_url")
    return FollowEdge(
        id = targetUid,
        userId = targetUid,
        username = username,
        avatarUrl = avatar,
        createdAt = VyoraTimestamp.parse(optString("created_at"))
    )
}

internal fun JSONObject.toFollowRequest(): FollowRequest = FollowRequest(
    id = optString("id"),
    requesterId = optString("requester_id"),
    requesterUsername = optString("requester_username"),
    requesterAvatarUrl = optString("requester_avatar_url"),
    targetId = optString("target_id"),
    status = optString("status").ifBlank { "pending" },
    createdAt = VyoraTimestamp.parse(optString("created_at")),
    updatedAt = VyoraTimestamp.parse(optString("updated_at"))
)

internal fun JSONObject.toVideoPost(): VideoPost {
    val vid = optString("video_id").ifBlank { optString("id") }
    return VideoPost(
        id = vid,
        userId = optString("creator_uid"),
        creatorUsername = optString("creator_username"),
        creatorAvatarUrl = optString("creator_avatar_url"),
        videoUrl = optString("video_url"),
        thumbnailUrl = optString("thumbnail_url"),
        caption = optString("caption"),
        hashtags = optJSONArray("hashtags").toStringList(),
        soundId = optString("sound_id"),
        soundTitle = optString("sound_title"),
        durationSec = optLong("duration_sec", 15L),
        viewsCount = optLong("views_count", 0L),
        likesCount = optLong("likes_count", 0L),
        commentsCount = optLong("comments_count", 0L),
        sharesCount = optLong("shares_count", 0L),
        bookmarksCount = optLong("bookmarks_count", 0L),
        status = optString("status").ifBlank { "active" },
        createdAt = VyoraTimestamp.parse(optString("created_at")),
        updatedAt = VyoraTimestamp.parse(optString("updated_at"))
    )
}

internal fun JSONObject.toVideoComment(): VideoComment = VideoComment(
    id = optString("id"),
    videoId = optString("video_id"),
    userId = optString("user_id"),
    username = optString("username"),
    avatarUrl = optString("avatar_url"),
    text = optString("text"),
    createdAt = VyoraTimestamp.parse(optString("created_at"))
)

internal fun JSONObject.toHashtagItem(): HashtagItem = HashtagItem(
    id = optString("id"),
    tag = optString("tag"),
    videosCount = optLong("videos_count", 0L),
    updatedAt = VyoraTimestamp.parse(optString("updated_at"))
)

internal fun JSONObject.toSoundTrack(): SoundTrack = SoundTrack(
    id = optString("id"),
    title = optString("title"),
    artist = optString("artist"),
    audioUrl = optString("audio_url"),
    coverUrl = optString("cover_url"),
    durationSec = optLong("duration_sec", 30L),
    isLicensed = optBoolean("is_licensed", true),
    creatorId = optString("creator_id"),
    usesCount = optLong("uses_count", 0L),
    createdAt = VyoraTimestamp.parse(optString("created_at"))
)

internal fun JSONObject.toConversation(): Conversation = Conversation(
    id = optString("id"),
    participantIds = optJSONArray("participant_ids").toStringList(),
    lastMessage = optString("last_message"),
    lastMessageType = optString("last_message_type").ifBlank { "text" },
    lastSenderId = optString("last_sender_id"),
    lastMessageAt = VyoraTimestamp.parse(optString("last_message_at")),
    createdAt = VyoraTimestamp.parse(optString("created_at")),
    updatedAt = VyoraTimestamp.parse(optString("updated_at"))
)

internal fun JSONObject.toChatMessage(): ChatMessage = ChatMessage(
    id = optString("id"),
    senderId = optString("sender_id"),
    participantIds = optJSONArray("participant_ids").toStringList(),
    type = optString("type").ifBlank { "text" },
    text = optString("text"),
    mediaUrl = optString("media_url"),
    audioDurationMs = optLong("audio_duration_ms", 0L),
    readBy = optJSONArray("read_by").toStringList(),
    createdAt = VyoraTimestamp.parse(optString("created_at"))
)

internal fun JSONObject.toAppNotification(): AppNotification = AppNotification(
    id = optString("id"),
    recipientId = optString("recipient_id"),
    actorId = optString("actor_id"),
    actorUsername = optString("actor_username"),
    actorAvatarUrl = optString("actor_avatar_url"),
    type = optString("type").ifBlank { "like" },
    targetId = optString("target_id"),
    message = optString("message"),
    isRead = optBoolean("is_read", false),
    createdAt = VyoraTimestamp.parse(optString("created_at"))
)

internal fun JSONObject.toLiveStream(): LiveStream = LiveStream(
    id = optString("id"),
    hostId = optString("host_id"),
    hostUsername = optString("host_username"),
    hostAvatarUrl = optString("host_avatar_url"),
    channelName = optString("channel_name"),
    title = optString("title"),
    isActive = optBoolean("is_active", true),
    viewerCount = optLong("viewer_count", 1L),
    likesCount = optLong("likes_count", 0L),
    startedAt = VyoraTimestamp.parse(optString("started_at")),
    endedAt = VyoraTimestamp.parse(optString("ended_at"))
)

internal fun JSONObject.toLiveComment(): LiveComment = LiveComment(
    id = optString("id"),
    streamId = optString("stream_id"),
    userId = optString("user_id"),
    username = optString("username"),
    text = optString("text"),
    createdAt = VyoraTimestamp.parse(optString("created_at"))
)

internal fun JSONObject.toModerationReport(): ModerationReport = ModerationReport(
    id = optString("id"),
    reporterId = optString("reporter_id"),
    targetType = optString("target_type").ifBlank { "video" },
    targetId = optString("target_id"),
    reason = optString("reason"),
    details = optString("details"),
    status = optString("status").ifBlank { "pending" },
    reviewedBy = optString("reviewed_by"),
    createdAt = VyoraTimestamp.parse(optString("created_at")),
    updatedAt = VyoraTimestamp.parse(optString("updated_at"))
)

internal fun JSONObject.toWallet(): Wallet {
    val uid = optString("user_id")
    return Wallet(
        id = uid,
        userId = uid,
        points = optLong("points", 0L),
        balanceUsd = optDouble("balance_usd", 0.0),
        totalEarnedUsd = optDouble("total_earned_usd", 0.0),
        updatedAt = VyoraTimestamp.parse(optString("updated_at"))
    )
}

internal fun JSONObject.toWalletTransaction(): WalletTransaction = WalletTransaction(
    id = optString("id"),
    userId = optString("user_id"),
    type = optString("tx_type").ifBlank { "view_reward" },
    pointsDelta = optLong("points_delta", 0L),
    amountDelta = optDouble("amount_delta", 0.0),
    description = optString("description"),
    status = optString("status").ifBlank { "completed" },
    createdAt = VyoraTimestamp.parse(optString("created_at"))
)

internal fun JSONObject.toWithdrawalRequest(): WithdrawalRequest = WithdrawalRequest(
    id = optString("id"),
    userId = optString("user_id"),
    amountUsd = optDouble("amount_usd", 0.0),
    pointsSpent = optLong("points_spent", 0L),
    method = optString("method"),
    accountDetails = optString("account_details"),
    status = optString("status").ifBlank { "pending" },
    reviewedBy = optString("reviewed_by"),
    createdAt = VyoraTimestamp.parse(optString("created_at")),
    updatedAt = VyoraTimestamp.parse(optString("updated_at"))
)

internal fun JSONObject.toAdminSettings(): AdminSettings = AdminSettings(
    id = optString("id").ifBlank { "global" },
    pointValueUsd = optDouble("point_value_usd", 0.01),
    minWithdrawalUsd = optDouble("min_withdrawal_usd", 10.0),
    rewardsEnabled = optBoolean("rewards_enabled", true),
    maxVideoDurationSec = optLong("max_video_duration_sec", 180L),
    updatedBy = optString("updated_by"),
    updatedAt = VyoraTimestamp.parse(optString("updated_at"))
)

internal fun JSONObject.toAdminRole(): AdminRole {
    val uid = optString("user_id")
    return AdminRole(
        id = uid,
        userId = uid,
        email = optString("email"),
        role = optString("role").ifBlank { "moderator" },
        assignedBy = optString("assigned_by"),
        createdAt = VyoraTimestamp.parse(optString("created_at"))
    )
}
