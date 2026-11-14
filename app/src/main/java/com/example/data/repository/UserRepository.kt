package com.example.data.repository

import android.content.Context
import com.example.data.model.FollowEdge
import com.example.data.model.FollowRequest
import com.example.data.model.UserProfile
import com.example.data.remote.SupabaseClient
import com.example.data.remote.toFollowEdge
import com.example.data.remote.toFollowRequest
import com.example.data.remote.toObjectList
import com.example.data.remote.toUserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

class UserRepository(
    private val supabase: SupabaseClient
) {
    constructor(context: Context) : this(SupabaseClient.getInstance(context))

    private fun requireUid(): String =
        supabase.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeUser(userId: String): Flow<UserProfile?> =
        supabase.observeTopic("profiles").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_profiles",
                    "user_id=eq.${SupabaseClient.urlEncode(userId)}&limit=1"
                )
                arr.toObjectList().firstOrNull()?.toUserProfile()
            }.getOrNull()
        }

    fun observeActiveUsers(): Flow<List<UserProfile>> =
        supabase.observeTopic("profiles").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_profiles",
                    "is_banned=eq.false&order=followers_count.desc&limit=60"
                )
                arr.toObjectList().map { it.toUserProfile() }
            }.getOrDefault(emptyList())
        }

    fun observeFollowingIds(userId: String): Flow<Set<String>> =
        supabase.observeTopic("follows").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_follows",
                    "select=following_id&follower_id=eq.${SupabaseClient.urlEncode(userId)}"
                )
                arr.toObjectList().mapNotNull { it.optString("following_id").takeIf(String::isNotBlank) }.toSet()
            }.getOrDefault(emptySet())
        }

    fun observeFollowers(userId: String): Flow<List<FollowEdge>> =
        supabase.observeTopic("follows").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_follows",
                    "following_id=eq.${SupabaseClient.urlEncode(userId)}&order=created_at.desc"
                )
                arr.toObjectList().map { it.toFollowEdge(isFollowingEdge = false) }
            }.getOrDefault(emptyList())
        }

    fun observeFollowing(userId: String): Flow<List<FollowEdge>> =
        supabase.observeTopic("follows").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_follows",
                    "follower_id=eq.${SupabaseClient.urlEncode(userId)}&order=created_at.desc"
                )
                arr.toObjectList().map { it.toFollowEdge(isFollowingEdge = true) }
            }.getOrDefault(emptyList())
        }

    fun observeBlockedIds(userId: String): Flow<Set<String>> =
        supabase.observeTopic("blocks").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_blocks",
                    "select=target_id&user_id=eq.${SupabaseClient.urlEncode(userId)}"
                )
                arr.toObjectList().mapNotNull { it.optString("target_id").takeIf(String::isNotBlank) }.toSet()
            }.getOrDefault(emptySet())
        }

    fun observeIncomingFollowRequests(userId: String): Flow<List<FollowRequest>> =
        supabase.observeTopic("follows").map {
            runCatching {
                val arr = supabase.select(
                    "vyora_follow_requests",
                    "target_id=eq.${SupabaseClient.urlEncode(userId)}&status=eq.pending&order=created_at.desc"
                )
                arr.toObjectList().map { it.toFollowRequest() }
            }.getOrDefault(emptyList())
        }

    suspend fun getUser(userId: String): Result<UserProfile?> = runCatching {
        val arr = supabase.select(
            "vyora_profiles",
            "user_id=eq.${SupabaseClient.urlEncode(userId)}&limit=1"
        )
        arr.toObjectList().firstOrNull()?.toUserProfile()
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun ensureUserProfile(
        uid: String,
        email: String,
        displayName: String?,
        avatarUrl: String?
    ): Result<UserProfile> = runCatching {
        val raw = supabase.rpc(
            "vyora_ensure_profile",
            JSONObject().apply {
                put("p_email", email)
                put("p_display_name", displayName.orEmpty())
                put("p_avatar_url", avatarUrl.orEmpty())
            }
        )
        supabase.notifyChange("profiles", "wallet")
        val arr = JSONArray(raw)
        arr.toObjectList().firstOrNull()?.toUserProfile()
            ?: UserProfile(id = uid, userId = uid, username = email.substringBefore("@").ifBlank { "creator" })
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String,
        isPrivate: Boolean,
        interests: List<String>? = null
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val updates = JSONObject().apply {
            put("username", username.trim().lowercase().take(40))
            put("display_name", displayName.trim().take(60))
            put("bio", bio.trim().take(250))
            put("avatar_url", avatarUrl.trim())
            put("is_private", isPrivate)
            if (interests != null) {
                put("interests", JSONArray(interests.take(20)))
            }
        }
        supabase.update("vyora_profiles", "user_id=eq.${SupabaseClient.urlEncode(uid)}", updates)
        runCatching {
            supabase.rpc("vyora_update_avatar", JSONObject().put("p_avatar_url", avatarUrl.trim()))
        }
        supabase.notifyChange("profiles", "videos", "comments", "live")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun updateAvatar(avatarUrl: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val cleanUrl = avatarUrl.trim()
        val rpcSucceeded = runCatching {
            supabase.rpc("vyora_update_avatar", JSONObject().put("p_avatar_url", cleanUrl))
        }.isSuccess
        if (!rpcSucceeded) {
            supabase.update(
                "vyora_profiles",
                "user_id=eq.${SupabaseClient.urlEncode(uid)}",
                JSONObject().put("avatar_url", cleanUrl)
            )
        }
        supabase.notifyChange("profiles", "videos", "comments", "live")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun updatePreferences(language: String, themeMode: String): Result<Unit> = runCatching {
        val uid = requireUid()
        val updates = JSONObject().apply {
            put("language", language)
            put("theme_mode", themeMode)
        }
        supabase.update("vyora_profiles", "user_id=eq.${SupabaseClient.urlEncode(uid)}", updates)
        supabase.notifyChange("profiles")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun followOrRequestUser(currentUser: UserProfile, targetUser: UserProfile): Result<String> = runCatching {
        val uid = requireUid()
        require(uid != targetUser.userId) { "Cannot follow yourself" }
        val res = supabase.rpc(
            "vyora_follow_or_request",
            JSONObject().put("p_target_id", targetUser.userId)
        ).trim().trim('"')
        supabase.notifyChange("follows", "profiles", "notifications")
        res.ifBlank { "followed" }
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun unfollowUser(targetUserId: String): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_unfollow_user",
            JSONObject().put("p_target_id", targetUserId)
        )
        supabase.notifyChange("follows", "profiles")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun respondToFollowRequest(request: FollowRequest, currentUser: UserProfile, accept: Boolean): Result<Unit> = runCatching {
        requireUid()
        supabase.rpc(
            "vyora_respond_follow_request",
            JSONObject().apply {
                put("p_request_id", request.id)
                put("p_accept", accept)
            }
        )
        supabase.notifyChange("follows", "profiles")
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun toggleBlockUser(targetUserId: String, currentlyBlocked: Boolean): Result<Unit> = runCatching {
        val uid = requireUid()
        val edgeId = "${uid}_${targetUserId}"
        if (currentlyBlocked) {
            supabase.delete("vyora_blocks", "id=eq.${SupabaseClient.urlEncode(edgeId)}")
        } else {
            supabase.insert(
                "vyora_blocks",
                JSONObject().apply {
                    put("id", edgeId)
                    put("user_id", uid)
                    put("target_id", targetUserId)
                },
                upsert = true,
                onConflict = "id"
            )
        }
        supabase.notifyChange("blocks")
        Unit
    }.onFailure { if (it is CancellationException) throw it }
}
