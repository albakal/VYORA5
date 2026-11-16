package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.remote.FirestoreErrorHandler
import com.example.data.remote.OperationType
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NotificationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id)),
        auth = FirebaseAuth.getInstance()
    )

    fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        db.collection("notifications")
            .whereEqualTo("recipientId", userId)
            .limit(50)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { err ->
                if (err is CancellationException) throw err
                FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.LIST, "notifications", auth)
                emit(emptyList())
            }

    suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        db.collection("notifications").document(notificationId)
            .update(mapOf("isRead" to true))
            .await()
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "notifications/$notificationId", auth)
    }

    suspend fun markAllRead(notifications: List<AppNotification>): Result<Unit> = runCatching {
        val unread = notifications.filter { !it.isRead && it.id.isNotBlank() }
        if (unread.isNotEmpty()) {
            val batch = db.batch()
            unread.forEach { item ->
                batch.update(
                    db.collection("notifications").document(item.id),
                    mapOf("isRead" to true)
                )
            }
            batch.commit().await()
        }
        Unit
    }.onFailure { err ->
        if (err is CancellationException) throw err
        FirestoreErrorHandler.handleFirestoreError(err as Exception, OperationType.UPDATE, "notifications", auth)
    }
}
