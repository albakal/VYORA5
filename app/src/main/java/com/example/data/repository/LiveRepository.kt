package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
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

class LiveRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeActiveStreams(): Flow<List<LiveStream>> =
        db.collection("live_streams")
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(LiveStream::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.viewerCount }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "live_streams", auth)
                emit(emptyList())
            }

    fun observeStream(streamId: String): Flow<LiveStream?> =
        db.collection("live_streams").document(streamId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(LiveStream::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "live_streams/$streamId", auth)
                emit(null)
            }

    fun observeLiveComments(streamId: String): Flow<List<LiveComment>> =
        db.collection("live_streams").document(streamId).collection("comments")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(LiveComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "live_streams/$streamId/comments", auth)
                emit(emptyList())
            }

    suspend fun startStream(host: UserProfile, title: String): Result<LiveStream> = runCatching {
        val uid = requireUid()
        val streamId = UUID.randomUUID().toString().replace("-", "")
        val channelName = "vyora_${uid.take(8)}_${streamId.take(8)}"
        val resolvedUsername = host.username.ifBlank { "creator_${uid.take(6)}" }.take(50)
        val resolvedTitle = title.trim().ifBlank { "${host.displayName.ifBlank { resolvedUsername }} LIVE" }.take(150)

        val streamRef = db.collection("live_streams").document(streamId)
        streamRef.set(
            mapOf(
                "hostId" to uid,
                "hostUsername" to resolvedUsername,
                "hostAvatarUrl" to host.avatarUrl.take(2000),
                "channelName" to channelName,
                "title" to resolvedTitle,
                "isActive" to true,
                "viewerCount" to 1L,
                "likesCount" to 0L,
                "startedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        val snap = streamRef.get().await()
        snap.toObject(LiveStream::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: LiveStream(
                id = streamId,
                hostId = uid,
                hostUsername = resolvedUsername,
                hostAvatarUrl = host.avatarUrl,
                channelName = channelName,
                title = resolvedTitle,
                isActive = true,
                viewerCount = 1L,
                likesCount = 0L
            )
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.CREATE, "live_streams", auth)
    }

    suspend fun updateViewerCount(streamId: String, delta: Long): Result<Unit> = runCatching {
        requireUid()
        val step = if (delta >= 0L) 1L else -1L
        val streamRef = db.collection("live_streams").document(streamId)
        db.runTransaction { tx ->
            val snap = tx.get(streamRef)
            if (snap.exists() && snap.getBoolean("isActive") == true) {
                val cur = snap.getLong("viewerCount") ?: 0L
                val next = cur + step
                if (next >= 0L) {
                    tx.update(streamRef, mapOf("viewerCount" to next))
                }
            }
        }.await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "live_streams/$streamId", auth)
    }

    suspend fun sendLiveLike(streamId: String): Result<Unit> = runCatching {
        requireUid()
        val streamRef = db.collection("live_streams").document(streamId)
        db.runTransaction { tx ->
            val snap = tx.get(streamRef)
            if (snap.exists() && snap.getBoolean("isActive") == true) {
                val cur = snap.getLong("likesCount") ?: 0L
                tx.update(streamRef, mapOf("likesCount" to cur + 1L))
            }
        }.await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "live_streams/$streamId", auth)
    }

    suspend fun sendLiveComment(streamId: String, actor: UserProfile, text: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmed = text.trim().take(300)
        require(trimmed.isNotEmpty())
        val commentId = UUID.randomUUID().toString().replace("-", "")
        db.collection("live_streams").document(streamId)
            .collection("comments").document(commentId)
            .set(
                mapOf(
                    "streamId" to streamId,
                    "userId" to uid,
                    "username" to actor.username.ifBlank { "creator" }.take(50),
                    "text" to trimmed,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.CREATE, "live_streams/$streamId/comments", auth)
    }

    suspend fun endStream(streamId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("live_streams").document(streamId)
            .update(
                mapOf(
                    "isActive" to false,
                    "endedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "live_streams/$streamId", auth)
    }
}
