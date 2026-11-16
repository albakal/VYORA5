package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
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

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeConversations(userId: String): Flow<List<Conversation>> =
        db.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(Conversation::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "conversations", auth)
                emit(emptyList())
            }

    fun observeMessages(conversationId: String, currentUserId: String): Flow<List<ChatMessage>> =
        db.collection("conversations").document(conversationId).collection("messages")
            .whereArrayContains("participantIds", currentUserId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "conversations/$conversationId/messages", auth)
                emit(emptyList())
            }

    suspend fun getOrCreateConversation(peerId: String): Result<String> = runCatching {
        val uid = requireUid()
        val sorted = listOf(uid, peerId).sorted()
        val convId = "${sorted[0]}_${sorted[1]}"
        val convRef = db.collection("conversations").document(convId)
        val snap = convRef.get().await()
        if (!snap.exists()) {
            convRef.set(
                mapOf(
                    "participantIds" to sorted,
                    "lastMessage" to "",
                    "lastMessageType" to "text",
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        convId
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "conversations", auth)
    }

    suspend fun sendMessage(
        conversationId: String,
        participantIds: List<String>,
        actor: UserProfile,
        type: String,
        text: String = "",
        mediaUrl: String = "",
        audioDurationMs: Long = 0L
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val resolvedParticipants = participantIds.distinct().take(2).let {
            if (it.size == 2) it else listOf(uid, uid)
        }
        val cleanType = if (type in listOf("text", "image", "video", "audio")) type else "text"
        val cleanText = text.trim().take(2000)
        val cleanMedia = mediaUrl.trim().take(2000)
        val preview = when (cleanType) {
            "image" -> "[Photo]"
            "video" -> "[Video]"
            "audio" -> "[Voice Note]"
            else -> cleanText
        }.take(200)

        val convRef = db.collection("conversations").document(conversationId)
        val convSnap = convRef.get().await()
        if (!convSnap.exists()) {
            convRef.set(
                mapOf(
                    "participantIds" to resolvedParticipants,
                    "lastMessage" to preview,
                    "lastMessageType" to cleanType,
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val msgId = UUID.randomUUID().toString().replace("-", "")
        val msgRef = convRef.collection("messages").document(msgId)
        val batch = db.batch()
        batch.set(
            msgRef,
            mapOf(
                "senderId" to uid,
                "participantIds" to resolvedParticipants,
                "type" to cleanType,
                "text" to cleanText,
                "mediaUrl" to cleanMedia,
                "audioDurationMs" to audioDurationMs.coerceIn(0L, 600000L),
                "readBy" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            convRef,
            mapOf(
                "lastMessage" to preview,
                "lastMessageType" to cleanType,
                "lastSenderId" to uid,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.commit().await()

        val peerId = resolvedParticipants.firstOrNull { it != uid }.orEmpty()
        if (peerId.isNotBlank()) {
            runCatching {
                val notifId = UUID.randomUUID().toString().replace("-", "")
                db.collection("notifications").document(notifId).set(
                    mapOf(
                        "recipientId" to peerId,
                        "actorId" to uid,
                        "actorUsername" to actor.username.take(50),
                        "actorAvatarUrl" to actor.avatarUrl.take(2000),
                        "type" to "message",
                        "targetId" to conversationId,
                        "message" to preview.ifBlank { "sent a message" },
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "conversations/$conversationId/messages", auth)
    }

    suspend fun markMessageRead(conversationId: String, message: ChatMessage): Result<Unit> = runCatching {
        val uid = requireUid()
        if (uid !in message.readBy && message.senderId != uid) {
            val nextReadBy = (message.readBy + uid).distinct().take(2)
            db.collection("conversations").document(conversationId)
                .collection("messages").document(message.id)
                .update(mapOf("readBy" to nextReadBy))
                .await()
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "conversations/$conversationId/messages/${message.id}", auth)
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("conversations").document(conversationId).delete().await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.DELETE, "conversations/$conversationId", auth)
    }
}
