package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.HashtagItem
import com.example.data.model.SoundTrack
import com.example.data.model.UserProfile
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.data.remote.FirestoreErrorHandler
import com.example.data.remote.OperationType
import com.example.earn.EarnLogic
import com.example.ui.UiState
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

class VideoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeActiveVideosState(): Flow<UiState<List<VideoPost>>> =
        db.collection("videos")
            .whereEqualTo("status", "active")
            .limit(60)
            .snapshots()
            .map { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    doc.toObject(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                UiState.Success(list) as UiState<List<VideoPost>>
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "videos", auth)
                emit(UiState.Error(err.localizedMessage ?: "Failed to load feed"))
            }

    fun observeActiveVideos(): Flow<List<VideoPost>> =
        db.collection("videos")
            .whereEqualTo("status", "active")
            .limit(60)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "videos", auth)
                emit(emptyList())
            }

    fun observeUserVideos(userId: String): Flow<List<VideoPost>> =
        db.collection("videos")
            .whereEqualTo("status", "active")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(VideoPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "videos", auth)
                emit(emptyList())
            }

    fun observeBookmarkedIds(userId: String): Flow<Set<String>> =
        db.collection("users").document(userId).collection("bookmarks")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { it.getString("videoId")?.takeIf(String::isNotBlank) ?: it.id }.toSet()
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "users/$userId/bookmarks", auth)
                emit(emptySet())
            }

    fun observeComments(videoId: String): Flow<List<VideoComment>> =
        db.collection("videos").document(videoId).collection("comments")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(VideoComment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "videos/$videoId/comments", auth)
                emit(emptyList())
            }

    fun observeSounds(): Flow<List<SoundTrack>> =
        db.collection("sounds")
            .whereEqualTo("isLicensed", true)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(SoundTrack::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.usesCount }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "sounds", auth)
                emit(emptyList())
            }

    fun observeHashtags(): Flow<List<HashtagItem>> =
        db.collection("hashtags")
            .whereGreaterThanOrEqualTo("videosCount", 0L)
            .limit(30)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(HashtagItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.videosCount }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "hashtags", auth)
                emit(emptyList())
            }

    suspend fun isVideoLikedByMe(videoId: String): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return runCatching {
            val snap = db.collection("videos").document(videoId)
                .collection("likes").document(uid)
                .get()
                .await()
            snap.exists()
        }.getOrDefault(false)
    }

    suspend fun publishVideo(
        creator: UserProfile,
        videoUrl: String,
        thumbnailUrl: String,
        caption: String,
        soundId: String,
        soundTitle: String,
        durationSec: Long,
        contentHash: String = "",
        fileSizeBytes: Long = 0L
    ): Result<String> = runCatching {
        val uid = requireUid()
        val videoId = UUID.randomUUID().toString().replace("-", "")
        val hashtags = Regex("#([\\p{L}0-9_]+)")
            .findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(15)
            .toList()

        val payload = mapOf(
            "userId" to uid,
            "creatorUsername" to creator.username.ifBlank { "creator_${uid.take(6)}" }.take(50),
            "creatorAvatarUrl" to creator.avatarUrl.take(2000),
            "videoUrl" to videoUrl.trim().take(2000),
            "thumbnailUrl" to thumbnailUrl.trim().take(2000),
            "caption" to caption.trim().take(500),
            "hashtags" to hashtags,
            "soundId" to soundId.trim().take(128),
            "soundTitle" to soundTitle.ifBlank { "Original Sound - ${creator.username}" }.take(150),
            "durationSec" to durationSec.coerceIn(1L, 300L),
            "viewsCount" to 0L,
            "likesCount" to 0L,
            "commentsCount" to 0L,
            "sharesCount" to 0L,
            "bookmarksCount" to 0L,
            "status" to "active",
            "contentHash" to contentHash.take(128),
            "fileSizeBytes" to fileSizeBytes.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val videoRef = db.collection("videos").document(videoId)
        videoRef.set(payload).await()

        // Update trending hashtags using deterministic ASCII doc IDs (supports Arabic hashtags safely)
        hashtags.forEach { tag ->
            runCatching {
                val tagDocId = EarnLogic.stableSha256Id(tag)
                val tagRef = db.collection("hashtags").document(tagDocId)
                db.runTransaction { tx ->
                    val snap = tx.get(tagRef)
                    val cur = snap.getLong("videosCount") ?: 0L
                    tx.set(
                        tagRef,
                        mapOf(
                            "tag" to tag.take(64),
                            "videosCount" to cur + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }.await()
            }
        }

        if (soundId.isNotBlank()) {
            runCatching {
                val soundRef = db.collection("sounds").document(soundId)
                db.runTransaction { tx ->
                    val sSnap = tx.get(soundRef)
                    if (sSnap.exists()) {
                        val uses = sSnap.getLong("usesCount") ?: 0L
                        tx.update(soundRef, mapOf("usesCount" to uses + 1L))
                    }
                }.await()
            }
        }

        val verify = videoRef.get().await()
        require(verify.exists() && verify.getString("status") == "active") {
            "Video could not be verified on Firestore"
        }

        videoId
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.CREATE, "videos", auth)
    }

    suspend fun updateVideoCaption(videoId: String, caption: String): Result<Unit> = runCatching {
        requireUid()
        val hashtags = Regex("#([\\p{L}0-9_]+)")
            .findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(15)
            .toList()
        db.collection("videos").document(videoId)
            .update(
                mapOf(
                    "caption" to caption.trim().take(500),
                    "hashtags" to hashtags,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "videos/$videoId", auth)
    }

    suspend fun deleteVideo(videoId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("videos").document(videoId).delete().await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.DELETE, "videos/$videoId", auth)
    }

    suspend fun toggleLike(video: VideoPost, actor: UserProfile): Result<Boolean> = runCatching {
        val uid = requireUid()
        val videoRef = db.collection("videos").document(video.id)
        val likeRef = videoRef.collection("likes").document(uid)
        val creatorProfileRef = db.collection("profiles").document(video.userId)

        val nowLiked = db.runTransaction { tx ->
            val vSnap = tx.get(videoRef)
            val lSnap = tx.get(likeRef)
            val cSnap = if (video.userId.isNotBlank()) tx.get(creatorProfileRef) else null

            val curLikes = vSnap.getLong("likesCount") ?: 0L
            val creatorLikes = cSnap?.getLong("likesCount") ?: 0L

            if (lSnap.exists()) {
                tx.delete(likeRef)
                tx.update(
                    videoRef,
                    mapOf(
                        "likesCount" to (curLikes - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (cSnap != null && cSnap.exists() && creatorLikes > 0L) {
                    tx.update(
                        creatorProfileRef,
                        mapOf(
                            "likesCount" to creatorLikes - 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                false
            } else {
                tx.set(
                    likeRef,
                    mapOf(
                        "userId" to uid,
                        "videoId" to video.id,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    videoRef,
                    mapOf(
                        "likesCount" to curLikes + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (cSnap != null && cSnap.exists()) {
                    tx.update(
                        creatorProfileRef,
                        mapOf(
                            "likesCount" to creatorLikes + 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                true
            }
        }.await()

        if (nowLiked && video.userId.isNotBlank() && video.userId != uid) {
            sendVideoNotification(
                recipientId = video.userId,
                actor = actor,
                type = "like",
                targetId = video.id,
                message = "liked your video"
            )
        }
        nowLiked
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "videos/${video.id}/likes", auth)
    }

    suspend fun toggleBookmark(videoId: String): Result<Boolean> = runCatching {
        val uid = requireUid()
        val videoRef = db.collection("videos").document(videoId)
        val bookmarkRef = db.collection("users").document(uid).collection("bookmarks").document(videoId)

        db.runTransaction { tx ->
            val vSnap = tx.get(videoRef)
            val bSnap = tx.get(bookmarkRef)
            val curCount = vSnap.getLong("bookmarksCount") ?: 0L

            if (bSnap.exists()) {
                tx.delete(bookmarkRef)
                tx.update(
                    videoRef,
                    mapOf(
                        "bookmarksCount" to (curCount - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                false
            } else {
                tx.set(
                    bookmarkRef,
                    mapOf(
                        "userId" to uid,
                        "videoId" to videoId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    videoRef,
                    mapOf(
                        "bookmarksCount" to curCount + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                true
            }
        }.await()
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "users/${auth.currentUser?.uid}/bookmarks/$videoId", auth)
    }

    suspend fun recordViewIfNew(videoId: String): Result<Unit> = runCatching {
        requireUid()
        val videoRef = db.collection("videos").document(videoId)
        db.runTransaction { tx ->
            val snap = tx.get(videoRef)
            if (snap.exists() && snap.getString("status") == "active") {
                val cur = snap.getLong("viewsCount") ?: 0L
                tx.update(
                    videoRef,
                    mapOf(
                        "viewsCount" to cur + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "videos/$videoId", auth)
    }

    suspend fun addComment(video: VideoPost, actor: UserProfile, text: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val trimmed = text.trim().take(500)
        require(trimmed.isNotEmpty())
        val videoRef = db.collection("videos").document(video.id)
        val commentId = UUID.randomUUID().toString().replace("-", "")
        val commentRef = videoRef.collection("comments").document(commentId)

        db.runTransaction { tx ->
            val vSnap = tx.get(videoRef)
            val cur = vSnap.getLong("commentsCount") ?: 0L
            tx.set(
                commentRef,
                mapOf(
                    "videoId" to video.id,
                    "userId" to uid,
                    "username" to actor.username.ifBlank { "creator" }.take(50),
                    "avatarUrl" to actor.avatarUrl.take(2000),
                    "text" to trimmed,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            tx.update(
                videoRef,
                mapOf(
                    "commentsCount" to cur + 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()

        if (video.userId.isNotBlank() && video.userId != uid) {
            sendVideoNotification(
                recipientId = video.userId,
                actor = actor,
                type = "comment",
                targetId = video.id,
                message = "commented: ${trimmed.take(80)}"
            )
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "videos/${video.id}/comments", auth)
    }

    suspend fun recordShare(video: VideoPost, actor: UserProfile): Result<Unit> = runCatching {
        val uid = requireUid()
        val videoRef = db.collection("videos").document(video.id)
        db.runTransaction { tx ->
            val snap = tx.get(videoRef)
            if (snap.exists()) {
                val cur = snap.getLong("sharesCount") ?: 0L
                tx.update(
                    videoRef,
                    mapOf(
                        "sharesCount" to cur + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.await()

        if (video.userId.isNotBlank() && video.userId != uid) {
            sendVideoNotification(
                recipientId = video.userId,
                actor = actor,
                type = "share",
                targetId = video.id,
                message = "shared your video"
            )
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "videos/${video.id}", auth)
    }

    suspend fun createSoundTrack(
        title: String,
        artist: String,
        audioUrl: String,
        durationSec: Long,
        isLicensed: Boolean
    ): Result<String> = runCatching {
        val uid = requireUid()
        val soundId = UUID.randomUUID().toString().replace("-", "")
        db.collection("sounds").document(soundId).set(
            mapOf(
                "title" to title.trim().take(150),
                "artist" to artist.trim().take(120),
                "audioUrl" to audioUrl.trim().take(2000),
                "coverUrl" to "",
                "durationSec" to durationSec.coerceIn(1L, 300L),
                "isLicensed" to isLicensed,
                "creatorId" to uid,
                "usesCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        soundId
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.CREATE, "sounds", auth)
    }

    private suspend fun sendVideoNotification(
        recipientId: String,
        actor: UserProfile,
        type: String,
        targetId: String,
        message: String
    ) {
        runCatching {
            val notifId = UUID.randomUUID().toString().replace("-", "")
            db.collection("notifications").document(notifId).set(
                mapOf(
                    "recipientId" to recipientId,
                    "actorId" to actor.userId,
                    "actorUsername" to actor.username.take(50),
                    "actorAvatarUrl" to actor.avatarUrl.take(2000),
                    "type" to type,
                    "targetId" to targetId,
                    "message" to message.take(400),
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
