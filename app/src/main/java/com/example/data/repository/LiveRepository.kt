package com.example.data.repository

import android.content.Context
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.UserProfile
import com.example.data.remote.SupabaseClient
import com.example.data.remote.toLiveComment
import com.example.data.remote.toLiveStream
import com.example.data.remote.toObjectList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

class LiveRepository(
    private val supabase: SupabaseClient
) {
    constructor(context: Context) : this(SupabaseClient.getInstance(context))

    private fun requireUid(): String =
        supabase.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeActiveStreams(): Flow<List<LiveStream>> =
        supabase.observeTopic("live").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_live_streams",
                    "is_active=eq.true&order=viewer_count.desc"
                )
                arr.toObjectList().map { it.toLiveStream() }
            }.getOrDefault(emptyList())
        }

    fun observeStream(streamId: String): Flow<LiveStream?> =
        supabase.observeTopic("live").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_live_streams",
                    "id=eq.${SupabaseClient.urlEncode(streamId)}&limit=1"
                )
                arr.toObjectList().firstOrNull()?.toLiveStream()
            }.getOrNull()
        }

    fun observeLiveComments(streamId: String): Flow<List<LiveComment>> =
        supabase.observeTopic("live_comments", intervalMs = 2500L).map {
            runCatching {
                val arr = supabase.select(
                    "vyora_live_comments",
                    "stream_id=eq.${SupabaseClient.urlEncode(streamId)}&order=created_at.asc"
                )
                arr.toObjectList().map { it.toLiveComment() }
            }.getOrDefault(emptyList())
        }

    suspend fun startStream(host: UserProfile, title: String): Result<LiveStream> = runCatching {
        val uid = requireUid()
        val streamId = UUID.randomUUID().toString().replace("-", "")
        val channelName = "vyora_${uid.take(8)}_${streamId.take(8)}"
        val resolvedTitle = title.trim().ifBlank { "${host.displayName.ifBlank { host.username }} LIVE" }.take(120)

        val rpcStream = runCatching {
            val raw = supabase.rpc(
                "vyora_start_live_stream",
                JSONObject().put("p_title", resolvedTitle)
            )
            JSONArray(raw).toObjectList().firstOrNull()?.toLiveStream()
        }.getOrNull()

        if (rpcStream != null && rpcStream.id.isNotBlank()) {
            supabase.notifyChange("live")
            return@runCatching rpcStream
        }

        val payload = JSONObject().apply {
            put("id", streamId)
            put("host_id", uid)
            put("host_username", host.username)
            put("host_avatar_url", host.avatarUrl)
            put("channel_name", channelName)
            put("title", resolvedTitle)
            put("is_active", true)
            put("viewer_count", 1L)
            put("likes_count", 0L)
        }

        val inserted = try {
            supabase.insert("vyora_live_streams", payload)
        } catch (err: Exception) {
            if (err is CancellationException) throw err
            val msg = err.message.orEmpty()
            if (msg.contains("bigint", ignoreCase = true)) {
                val withoutId = JSONObject(payload.toString()).apply { remove("id") }
                try {
                    supabase.insert("vyora_live_streams", withoutId)
                } catch (retryErr: Exception) {
                    if (retryErr is CancellationException) throw retryErr
                    if (retryErr.message.orEmpty().contains("bigint", ignoreCase = true)) {
                        throw IllegalStateException(
                            "Database schema mismatch (bigint column on vyora_live_streams). Please run /supabase/schema.sql in Supabase SQL Editor."
                        )
                    }
                    throw retryErr
                }
            } else {
                throw err
            }
        }

        supabase.notifyChange("live")
        inserted.toObjectList().firstOrNull()?.toLiveStream() ?: LiveStream(
            id = streamId,
            hostId = uid,
            hostUsername = host.username,
            hostAvatarUrl = host.avatarUrl,
            channelName = channelName,
            title = resolvedTitle,
            isActive = true,
            viewerCount = 1L
        )
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun updateViewerCount(streamId: String, delta: Long): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_live_increment_stat",
            JSONObject().apply {
                put("p_stream_id", streamId)
                put("p_viewer_delta", delta)
                put("p_like_delta", 0L)
            }
        )
        supabase.notifyChange("live")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun sendLiveLike(streamId: String): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_live_increment_stat",
            JSONObject().apply {
                put("p_stream_id", streamId)
                put("p_viewer_delta", 0L)
                put("p_like_delta", 1L)
            }
        )
        supabase.notifyChange("live")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun sendLiveComment(streamId: String, actor: UserProfile, text: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmed = text.trim()
        require(trimmed.isNotEmpty())
        val commentId = UUID.randomUUID().toString().replace("-", "")
        supabase.insert(
            "vyora_live_comments",
            JSONObject().apply {
                put("id", commentId)
                put("stream_id", streamId)
                put("user_id", uid)
                put("username", actor.username)
                put("text", trimmed.take(300))
            }
        )
        supabase.notifyChange("live_comments")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun endStream(streamId: String): Result<Unit> = runCatching {
        requireUid()
        supabase.update(
            "vyora_live_streams",
            "id=eq.${SupabaseClient.urlEncode(streamId)}",
            JSONObject().apply {
                put("is_active", false)
                put("ended_at", Instant.now().toString())
            }
        )
        supabase.notifyChange("live")
        Unit
    }.onFailure { if (it is CancellationException) throw it }
}
