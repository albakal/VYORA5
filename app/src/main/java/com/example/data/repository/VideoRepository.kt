package com.example.data.repository

import android.content.Context
import com.example.data.model.HashtagItem
import com.example.data.model.SoundTrack
import com.example.data.model.UserProfile
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.data.remote.SupabaseClient
import com.example.data.remote.toHashtagItem
import com.example.data.remote.toObjectList
import com.example.data.remote.toSoundTrack
import com.example.data.remote.toVideoComment
import com.example.data.remote.toVideoPost
import com.example.ui.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class VideoRepository(
    private val supabase: SupabaseClient
) {
    constructor(context: Context) : this(SupabaseClient.getInstance(context))

    private fun requireUid(): String =
        supabase.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeActiveVideosState(): Flow<UiState<List<VideoPost>>> =
        supabase.observeTopic("videos").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_videos_ledger",
                    "status=eq.active&order=created_at.desc&limit=60"
                )
                UiState.Success(arr.toObjectList().map { it.toVideoPost() }) as UiState<List<VideoPost>>
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                UiState.Error(e.localizedMessage ?: "Failed to load feed")
            }
        }

    fun observeActiveVideos(): Flow<List<VideoPost>> =
        supabase.observeTopic("videos").map {
            val arr = supabase.select(
                "vyora_videos_ledger",
                "status=eq.active&order=created_at.desc&limit=60"
            )
            arr.toObjectList().map { it.toVideoPost() }
        }

    fun observeUserVideos(userId: String): Flow<List<VideoPost>> =
        supabase.observeTopic("videos").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_videos_ledger",
                    "creator_uid=eq.${SupabaseClient.urlEncode(userId)}&status=eq.active&order=created_at.desc"
                )
                arr.toObjectList().map { it.toVideoPost() }
            }.getOrDefault(emptyList())
        }

    fun observeBookmarkedIds(userId: String): Flow<Set<String>> =
        supabase.observeTopic("bookmarks").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_video_bookmarks",
                    "select=video_id&user_id=eq.${SupabaseClient.urlEncode(userId)}"
                )
                arr.toObjectList().mapNotNull { it.optString("video_id").takeIf(String::isNotBlank) }.toSet()
            }.getOrDefault(emptySet())
        }

    fun observeComments(videoId: String): Flow<List<VideoComment>> =
        supabase.observeTopic("comments").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_video_comments",
                    "video_id=eq.${SupabaseClient.urlEncode(videoId)}&order=created_at.desc"
                )
                arr.toObjectList().map { it.toVideoComment() }
            }.getOrDefault(emptyList())
        }

    fun observeSounds(): Flow<List<SoundTrack>> =
        supabase.observeTopic("sounds").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_sounds",
                    "is_licensed=eq.true&order=uses_count.desc"
                )
                arr.toObjectList().map { it.toSoundTrack() }
            }.getOrDefault(emptyList())
        }

    fun observeHashtags(): Flow<List<HashtagItem>> =
        supabase.observeTopic("hashtags").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_hashtags",
                    "videos_count=gte.0&order=videos_count.desc&limit=30"
                )
                arr.toObjectList().map { it.toHashtagItem() }
            }.getOrDefault(emptyList())
        }

    suspend fun isVideoLikedByMe(videoId: String): Boolean {
        val uid = supabase.currentUser?.uid ?: return false
        return runCatching {
            val likeId = "${videoId}:${uid}"
            val arr = supabase.select(
                "vyora_video_likes",
                "select=id&id=eq.${SupabaseClient.urlEncode(likeId)}&limit=1"
            )
            arr.length() > 0
        }.getOrDefault(false)
    }

    suspend fun publishVideo(
        creator: UserProfile,
        videoUrl: String,
        thumbnailUrl: String,
        caption: String,
        soundId: String,
        soundTitle: String,
        durationSec: Long
    ): Result<String> = runCatching {
        requireUid()
        val hashtags = Regex("#([\\p{L}0-9_]+)")
            .findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(15)
            .toList()

        val videoId = supabase.rpc(
            "vyora_publish_video",
            JSONObject().apply {
                put("p_video_url", videoUrl)
                put("p_thumbnail_url", thumbnailUrl)
                put("p_caption", caption.take(500))
                put("p_hashtags", JSONArray(hashtags))
                put("p_sound_id", soundId)
                put("p_sound_title", soundTitle.ifBlank { "Original Sound - ${creator.username}" }.take(120))
                put("p_duration_sec", durationSec.coerceIn(1L, 300L))
            }
        ).trim().trim('"')
        require(videoId.isNotBlank()) { "Server did not return a valid video ID" }

        val verification = supabase.select(
            "vyora_videos_ledger",
            "video_id=eq.${SupabaseClient.urlEncode(videoId)}&status=eq.active&limit=1"
        )
        if (verification.length() == 0) {
            throw IllegalStateException(
                "Video was saved ($videoId) but is not visible via vyora_videos_ledger SELECT RLS policy. Re-run supabase/schema.sql."
            )
        }

        supabase.notifyChange("videos", "hashtags", "sounds")
        videoId
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun updateVideoCaption(videoId: String, caption: String): Result<Unit> = runCatching {
        requireUid()
        val hashtags = Regex("#([\\p{L}0-9_]+)")
            .findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(15)
            .toList()
        supabase.update(
            "vyora_videos_ledger",
            "video_id=eq.${SupabaseClient.urlEncode(videoId)}",
            JSONObject().apply {
                put("caption", caption.take(500))
                put("hashtags", JSONArray(hashtags))
            }
        )
        supabase.notifyChange("videos")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun deleteVideo(videoId: String): Result<Unit> = runCatching {
        requireUid()
        supabase.delete("vyora_videos_ledger", "video_id=eq.${SupabaseClient.urlEncode(videoId)}")
        supabase.notifyChange("videos")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun toggleLike(video: VideoPost, actor: UserProfile): Result<Boolean> = runCatching {
        requireUid()
        val raw = supabase.rpc(
            "vyora_toggle_like",
            JSONObject().put("p_video_id", video.id)
        ).trim()
        supabase.notifyChange("videos", "profiles", "notifications")
        raw == "true"
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun toggleBookmark(videoId: String): Result<Boolean> = runCatching {
        requireUid()
        val raw = supabase.rpc(
            "vyora_toggle_bookmark",
            JSONObject().put("p_video_id", videoId)
        ).trim()
        supabase.notifyChange("bookmarks", "videos")
        raw == "true"
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun recordViewIfNew(videoId: String): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_record_view_reward",
            JSONObject().put("p_video_id", videoId)
        )
        supabase.notifyChange("videos", "wallet")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun addComment(video: VideoPost, actor: UserProfile, text: String): Result<Unit> = runCatching {
        requireUid()
        val trimmed = text.trim()
        require(trimmed.isNotEmpty())
        supabase.rpc(
            "vyora_add_comment",
            JSONObject().apply {
                put("p_video_id", video.id)
                put("p_text", trimmed.take(500))
            }
        )
        supabase.notifyChange("comments", "videos", "notifications")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun recordShare(video: VideoPost, actor: UserProfile): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_record_share",
            JSONObject().put("p_video_id", video.id)
        )
        supabase.notifyChange("videos", "notifications")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun createSoundTrack(
        title: String,
        artist: String,
        audioUrl: String,
        durationSec: Long,
        isLicensed: Boolean
    ): Result<String> = runCatching {
        val uid = requireUid()
        val soundId = UUID.randomUUID().toString().replace("-", "")
        supabase.insert(
            "vyora_sounds",
            JSONObject().apply {
                put("id", soundId)
                put("title", title.take(120))
                put("artist", artist.take(100))
                put("audio_url", audioUrl.take(1500))
                put("cover_url", "")
                put("duration_sec", durationSec.coerceIn(1L, 300L))
                put("is_licensed", isLicensed)
                put("creator_id", uid)
                put("uses_count", 0L)
            }
        )
        supabase.notifyChange("sounds")
        soundId
    }.onFailure { if (it is CancellationException) throw it }
}
