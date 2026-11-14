package com.example.data.repository

import android.content.Context
import com.example.data.model.AppNotification
import com.example.data.remote.SupabaseClient
import com.example.data.remote.toAppNotification
import com.example.data.remote.toObjectList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class NotificationRepository(
    private val supabase: SupabaseClient
) {
    constructor(context: Context) : this(SupabaseClient.getInstance(context))

    fun observeNotifications(userId: String): Flow<List<AppNotification>> =
        supabase.observeTopic("notifications").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_notifications",
                    "recipient_id=eq.${SupabaseClient.urlEncode(userId)}&order=created_at.desc&limit=50"
                )
                arr.toObjectList().map { it.toAppNotification() }
            }.getOrDefault(emptyList())
        }

    suspend fun markAsRead(notificationId: String): Result<Unit> = runCatching {
        supabase.update(
            "vyora_notifications",
            "id=eq.${SupabaseClient.urlEncode(notificationId)}",
            JSONObject().put("is_read", true)
        )
        supabase.notifyChange("notifications")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun markAllRead(notifications: List<AppNotification>): Result<Unit> = runCatching {
        val uid = supabase.currentUser?.uid
        if (uid != null && notifications.any { !it.isRead }) {
            supabase.update(
                "vyora_notifications",
                "recipient_id=eq.${SupabaseClient.urlEncode(uid)}&is_read=eq.false",
                JSONObject().put("is_read", true)
            )
            supabase.notifyChange("notifications")
        }
        Unit
    }.onFailure { if (it is CancellationException) throw it }
}
