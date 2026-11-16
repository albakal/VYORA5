package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.FollowEdge
import com.example.data.model.FollowRequest
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class UserRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance()
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeUser(userId: String): Flow<UserProfile?> =
        db.collection("profiles").document(userId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "profiles/$userId", auth)
                emit(null)
            }

    fun observeActiveUsers(): Flow<List<UserProfile>> =
        db.collection("profiles")
            .whereEqualTo("isBanned", false)
            .limit(60)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.followersCount }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "profiles", auth)
                emit(emptyList())
            }

    fun observeFollowingIds(userId: String): Flow<Set<String>> {
        val directFlow = db.collection("users").document(userId).collection("following")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { it.getString("userId")?.takeIf(String::isNotBlank) ?: it.id }.toSet()
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "users/$userId/following", auth)
                emit(emptySet())
            }

        val acceptedPrivateFlow = db.collection("follow_requests")
            .whereEqualTo("requesterId", userId)
            .snapshots()
            .map { snap ->
                snap.documents
                    .filter { it.getString("status") == "accepted" }
                    .mapNotNull { it.getString("targetId")?.takeIf(String::isNotBlank) }
                    .toSet()
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "follow_requests", auth)
                emit(emptySet())
            }

        return combine(directFlow, acceptedPrivateFlow) { direct, accepted -> direct + accepted }
    }

    fun observeFollowers(userId: String): Flow<List<FollowEdge>> =
        db.collection("users").document(userId).collection("followers")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(FollowEdge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "users/$userId/followers", auth)
                emit(emptyList())
            }

    fun observeFollowing(userId: String): Flow<List<FollowEdge>> =
        db.collection("users").document(userId).collection("following")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(FollowEdge::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "users/$userId/following", auth)
                emit(emptyList())
            }

    fun observeBlockedIds(userId: String): Flow<Set<String>> =
        db.collection("users").document(userId).collection("blocked")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { it.getString("targetId")?.takeIf(String::isNotBlank) ?: it.id }.toSet()
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "users/$userId/blocked", auth)
                emit(emptySet())
            }

    fun observeIncomingFollowRequests(userId: String): Flow<List<FollowRequest>> =
        db.collection("follow_requests")
            .whereEqualTo("targetId", userId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(FollowRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.filter { it.status == "pending" }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "follow_requests", auth)
                emit(emptyList())
            }

    suspend fun getUser(userId: String): Result<UserProfile?> = runCatching {
        val snap = db.collection("profiles").document(userId).get().await()
        if (!snap.exists()) null
        else snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.GET, "profiles/$userId", auth)
    }

    suspend fun ensureUserProfile(
        uid: String,
        email: String,
        displayName: String?,
        avatarUrl: String?
    ): Result<UserProfile> = runCatching {
        val profileRef = db.collection("profiles").document(uid)
        val privateRef = db.collection("users").document(uid).collection("private").document("info")
        val walletRef = db.collection("wallets").document(uid)

        val existingSnap = profileRef.get().await()
        val fallbackUsername = email.substringBefore("@")
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .trim('_')
            .ifBlank { "creator_${uid.take(6)}" }
            .take(40)
        val resolvedDisplay = displayName?.trim()?.take(80)?.ifBlank { fallbackUsername } ?: fallbackUsername
        val resolvedAvatar = avatarUrl?.trim().orEmpty()

        if (!existingSnap.exists()) {
            val profileData = mapOf(
                "userId" to uid,
                "username" to fallbackUsername,
                "displayName" to resolvedDisplay,
                "bio" to "",
                "avatarUrl" to resolvedAvatar,
                "isPrivate" to false,
                "isBanned" to false,
                "followersCount" to 0L,
                "followingCount" to 0L,
                "likesCount" to 0L,
                "interests" to emptyList<String>(),
                "language" to "ar",
                "themeMode" to "dark",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            profileRef.set(profileData).await()
        } else if (resolvedAvatar.isNotBlank() && existingSnap.getString("avatarUrl").isNullOrBlank()) {
            runCatching {
                profileRef.update(
                    mapOf(
                        "avatarUrl" to resolvedAvatar,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        val privateSnap = privateRef.get().await()
        if (!privateSnap.exists()) {
            privateRef.set(
                mapOf(
                    "userId" to uid,
                    "email" to email.trim().take(200),
                    "displayName" to resolvedDisplay,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else if (email.isNotBlank() && privateSnap.getString("email") != email) {
            runCatching {
                privateRef.update(
                    mapOf(
                        "email" to email.trim().take(200),
                        "displayName" to resolvedDisplay,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        val walletSnap = runCatching { walletRef.get().await() }.getOrNull()
        if (walletSnap == null || !walletSnap.exists()) {
            runCatching {
                walletRef.set(
                    mapOf(
                        "userId" to uid,
                        "points" to 0L,
                        "balanceUsd" to 0.0,
                        "totalEarnedUsd" to 0.0,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }

        val finalSnap = profileRef.get().await()
        finalSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?: UserProfile(
                id = uid,
                userId = uid,
                username = fallbackUsername,
                displayName = resolvedDisplay,
                avatarUrl = resolvedAvatar
            )
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "profiles/$uid", auth)
    }

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        isPrivate: Boolean,
        interests: List<String>? = null
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val cleanAvatar = avatarUrl.trim().take(2000)
        val cleanUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_.]"), "_").ifBlank { "creator_${uid.take(6)}" }.take(50)
        val updates = mutableMapOf<String, Any>(
            "username" to cleanUsername,
            "displayName" to displayName.trim().take(80),
            "bio" to bio.trim().take(300),
            "avatarUrl" to cleanAvatar,
            "isPrivate" to isPrivate,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (interests != null) {
            updates["interests"] = interests.take(20)
        }
        db.collection("profiles").document(uid).update(updates).await()
        propagateAvatarToOwnVideos(uid, cleanAvatar)
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "profiles/${auth.currentUser?.uid}", auth)
    }

    suspend fun updateAvatar(avatarUrl: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val cleanUrl = avatarUrl.trim().take(2000)
        require(cleanUrl.isNotBlank()) { "رابط الصورة الشخصية غير صالح" }
        val profileRef = db.collection("profiles").document(uid)
        val snap = profileRef.get().await()
        if (!snap.exists()) {
            val email = auth.currentUser?.email.orEmpty()
            ensureUserProfile(uid, email, auth.currentUser?.displayName, cleanUrl).getOrThrow()
        }
        profileRef.update(
            mapOf(
                "avatarUrl" to cleanUrl,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        propagateAvatarToOwnVideos(uid, cleanUrl)
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "profiles/${auth.currentUser?.uid}", auth)
    }

    private suspend fun propagateAvatarToOwnVideos(uid: String, avatarUrl: String) {
        if (avatarUrl.isBlank()) return
        runCatching {
            val myVideos = db.collection("videos")
                .whereEqualTo("userId", uid)
                .limit(30)
                .get()
                .await()
            if (!myVideos.isEmpty) {
                val batch = db.batch()
                myVideos.documents.forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "creatorAvatarUrl" to avatarUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
        }
    }

    suspend fun updatePreferences(language: String, themeMode: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val lang = if (language in listOf("ar", "en")) language else "ar"
        val theme = if (themeMode in listOf("dark", "light", "system")) themeMode else "dark"
        db.collection("profiles").document(uid)
            .update(
                mapOf(
                    "language" to lang,
                    "themeMode" to theme,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "profiles/${auth.currentUser?.uid}", auth)
    }

    suspend fun followOrRequestUser(currentUser: UserProfile, targetUser: UserProfile): Result<String> = runCatching {
        val uid = requireUid()
        val targetId = targetUser.userId
        require(uid != targetId) { "Cannot follow yourself" }

        if (targetUser.isPrivate) {
            val reqId = "${uid}_$targetId"
            db.collection("follow_requests").document(reqId).set(
                mapOf(
                    "requesterId" to uid,
                    "requesterUsername" to currentUser.username.ifBlank { "creator" }.take(50),
                    "requesterAvatarUrl" to currentUser.avatarUrl.take(2000),
                    "targetId" to targetId,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            sendFollowNotification(
                recipientId = targetId,
                actor = currentUser,
                type = "follow_request",
                message = "sent you a follow request"
            )
            return@runCatching "requested"
        }

        val myProfileRef = db.collection("profiles").document(uid)
        val targetProfileRef = db.collection("profiles").document(targetId)
        val myFollowingRef = db.collection("users").document(uid).collection("following").document(targetId)
        val targetFollowerRef = db.collection("users").document(targetId).collection("followers").document(uid)

        db.runTransaction { tx ->
            val mySnap = tx.get(myProfileRef)
            val targetSnap = tx.get(targetProfileRef)
            val existingEdge = tx.get(myFollowingRef)
            if (existingEdge.exists()) return@runTransaction

            val myFollowingCount = mySnap.getLong("followingCount") ?: 0L
            val targetFollowersCount = targetSnap.getLong("followersCount") ?: 0L

            tx.set(
                myFollowingRef,
                mapOf(
                    "userId" to targetId,
                    "username" to targetUser.username.ifBlank { "creator" }.take(50),
                    "avatarUrl" to targetUser.avatarUrl.take(2000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            tx.set(
                targetFollowerRef,
                mapOf(
                    "userId" to uid,
                    "username" to currentUser.username.ifBlank { "creator" }.take(50),
                    "avatarUrl" to currentUser.avatarUrl.take(2000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            tx.update(
                myProfileRef,
                mapOf(
                    "followingCount" to myFollowingCount + 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            tx.update(
                targetProfileRef,
                mapOf(
                    "followersCount" to targetFollowersCount + 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()

        sendFollowNotification(
            recipientId = targetId,
            actor = currentUser,
            type = "follow",
            message = "started following you"
        )
        "followed"
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "users/${auth.currentUser?.uid}/following", auth)
    }

    suspend fun unfollowUser(targetUserId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val myProfileRef = db.collection("profiles").document(uid)
        val targetProfileRef = db.collection("profiles").document(targetUserId)
        val myFollowingRef = db.collection("users").document(uid).collection("following").document(targetUserId)
        val targetFollowerRef = db.collection("users").document(targetUserId).collection("followers").document(uid)

        db.runTransaction { tx ->
            val mySnap = tx.get(myProfileRef)
            val targetSnap = tx.get(targetProfileRef)
            val edge = tx.get(myFollowingRef)

            if (edge.exists()) {
                val myFollowingCount = mySnap.getLong("followingCount") ?: 0L
                val targetFollowersCount = targetSnap.getLong("followersCount") ?: 0L
                tx.delete(myFollowingRef)
                tx.delete(targetFollowerRef)
                if (myFollowingCount > 0L) {
                    tx.update(
                        myProfileRef,
                        mapOf(
                            "followingCount" to myFollowingCount - 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (targetFollowersCount > 0L) {
                    tx.update(
                        targetProfileRef,
                        mapOf(
                            "followersCount" to targetFollowersCount - 1L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
        }.await()

        // Also clean up accepted private follow request if present
        runCatching {
            val reqRef = db.collection("follow_requests").document("${uid}_$targetUserId")
            if (reqRef.get().await().exists()) {
                reqRef.delete().await()
            }
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.DELETE, "users/${auth.currentUser?.uid}/following/$targetUserId", auth)
    }

    suspend fun respondToFollowRequest(
        request: FollowRequest,
        currentUser: UserProfile,
        accept: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val reqRef = db.collection("follow_requests").document(request.id)
        val myProfileRef = db.collection("profiles").document(uid)
        val followerRef = db.collection("users").document(uid).collection("followers").document(request.requesterId)

        db.runTransaction { tx ->
            val reqSnap = tx.get(reqRef)
            require(reqSnap.exists()) { "Follow request not found" }
            val mySnap = if (accept) tx.get(myProfileRef) else null
            val existingFollower = if (accept) tx.get(followerRef) else null

            tx.update(
                reqRef,
                mapOf(
                    "status" to if (accept) "accepted" else "rejected",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (accept && existingFollower != null && !existingFollower.exists()) {
                val curFollowers = mySnap?.getLong("followersCount") ?: 0L
                tx.set(
                    followerRef,
                    mapOf(
                        "userId" to request.requesterId,
                        "username" to request.requesterUsername.ifBlank { "creator" }.take(50),
                        "avatarUrl" to request.requesterAvatarUrl.take(2000),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.update(
                    myProfileRef,
                    mapOf(
                        "followersCount" to curFollowers + 1L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.await()

        if (accept) {
            sendFollowNotification(
                recipientId = request.requesterId,
                actor = currentUser,
                type = "follow",
                message = "accepted your follow request"
            )
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "follow_requests/${request.id}", auth)
    }

    suspend fun toggleBlockUser(targetUserId: String, currentlyBlocked: Boolean): Result<Unit> = runCatching {
        val uid = requireUid()
        val docRef = db.collection("users").document(uid).collection("blocked").document(targetUserId)
        if (currentlyBlocked) {
            docRef.delete().await()
        } else {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "targetId" to targetUserId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.WRITE, "users/${auth.currentUser?.uid}/blocked/$targetUserId", auth)
    }

    private suspend fun sendFollowNotification(
        recipientId: String,
        actor: UserProfile,
        type: String,
        message: String
    ) {
        if (recipientId.isBlank() || recipientId == actor.userId) return
        runCatching {
            val notifId = UUID.randomUUID().toString().replace("-", "")
            db.collection("notifications").document(notifId).set(
                mapOf(
                    "recipientId" to recipientId,
                    "actorId" to actor.userId,
                    "actorUsername" to actor.username.take(50),
                    "actorAvatarUrl" to actor.avatarUrl.take(2000),
                    "type" to type,
                    "targetId" to actor.userId,
                    "message" to message,
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }
}
