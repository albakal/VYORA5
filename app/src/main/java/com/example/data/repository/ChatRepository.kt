package com.example.data.repository

import android.content.Context
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.data.remote.SupabaseClient
import com.example.data.remote.toChatMessage
import com.example.data.remote.toConversation
import com.example.data.remote.toObjectList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class ChatRepository(
    private val supabase: SupabaseClient
) {
    constructor(context: Context) : this(SupabaseClient.getInstance(context))

    private fun requireUid(): String =
        supabase.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeConversations(userId: String): Flow<List<Conversation>> =
        supabase.observeTopic("conversations").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_conversations",
                    "participant_ids=cs.${SupabaseClient.urlEncode("{$userId}")}&order=last_message_at.desc"
                )
                arr.toObjectList().map { it.toConversation() }
            }.getOrDefault(emptyList())
        }

    fun observeMessages(conversationId: String, currentUserId: String): Flow<List<ChatMessage>> =
        supabase.observeTopic("messages").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_messages",
                    "conversation_id=eq.${SupabaseClient.urlEncode(conversationId)}&order=created_at.asc"
                )
                arr.toObjectList().map { it.toChatMessage() }
            }.getOrDefault(emptyList())
        }

    suspend fun getOrCreateConversation(peerId: String): Result<String> = runCatching {
        val uid = requireUid()
        val sorted = listOf(uid, peerId).sorted()
        val convId = "${sorted[0]}_${sorted[1]}"
        supabase.insert(
            "vyora_conversations",
            JSONObject().apply {
                put("id", convId)
                put("participant_ids", JSONArray(sorted))
                put("last_message", "")
                put("last_message_type", "text")
                put("last_sender_id", uid)
            },
            upsert = true,
            onConflict = "id"
        )
        supabase.notifyChange("conversations")
        convId
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun sendMessage(
        conversationId: String,
        participantIds: List<String>,
        actor: UserProfile,
        type: String,
        text: String = "",
        mediaUrl: String = "",
        audioDurationMs: Long = 0L
    ): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_send_message",
            JSONObject().apply {
                put("p_conversation_id", conversationId)
                put("p_participant_ids", JSONArray(participantIds))
                put("p_type", type)
                put("p_text", text.take(2000))
                put("p_media_url", mediaUrl.take(1500))
                put("p_audio_duration_ms", audioDurationMs.coerceIn(0L, 600000L))
            }
        )
        supabase.notifyChange("messages", "conversations", "notifications")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun markMessageRead(conversationId: String, message: ChatMessage): Result<Unit> = runCatching {
        val uid = requireUid()
        if (uid !in message.readBy && message.senderId != uid) {
            val nextReadBy = (message.readBy + uid).distinct().take(2)
            supabase.update(
                "vyora_messages",
                "id=eq.${SupabaseClient.urlEncode(message.id)}",
                JSONObject().put("read_by", JSONArray(nextReadBy))
            )
            supabase.notifyChange("messages")
        }
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        requireUid()
        supabase.delete("vyora_conversations", "id=eq.${SupabaseClient.urlEncode(conversationId)}")
        supabase.notifyChange("conversations", "messages")
        Unit
    }.onFailure { if (it is CancellationException) throw it }
}
