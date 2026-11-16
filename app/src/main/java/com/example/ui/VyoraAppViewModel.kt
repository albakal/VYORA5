package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminRole
import com.example.data.model.AdminSettings
import com.example.data.model.AppNotification
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.EarnAdminOverview
import com.example.data.model.EarnDashboardState
import com.example.data.model.EarnProgramConfig
import com.example.data.model.FollowRequest
import com.example.data.model.HashtagItem
import com.example.data.model.LiveComment
import com.example.data.model.LiveStream
import com.example.data.model.ModerationReport
import com.example.data.model.SoundTrack
import com.example.data.model.UserProfile
import com.example.data.model.VideoComment
import com.example.data.model.VideoPost
import com.example.data.model.Wallet
import com.example.data.model.WalletTransaction
import com.example.data.model.WithdrawalRequest
import com.example.data.preferences.AppPreferencesRepository
import com.example.data.remote.AgoraLiveSession
import com.example.data.remote.EarnProgramRepository
import com.example.data.remote.FirebaseBackendRepository
import com.example.data.remote.FirebaseCloudSummary
import com.example.data.remote.StorageMediaRepository
import com.example.data.remote.UploadStatus
import com.example.data.repository.ChatRepository
import com.example.data.repository.LiveRepository
import com.example.data.repository.NotificationRepository
import com.example.data.repository.UserRepository
import com.example.data.repository.VideoRepository
import com.example.data.repository.WalletAdminRepository
import com.example.security.DeviceIntegrityHelper
import com.example.security.DeviceIntegrityInfo
import com.example.ui.feed.WatchWheelLogic
import com.example.ui.feed.WatchWheelUiState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface LedgerSyncState {
    data object Checking : LedgerSyncState
    data class Synced(val summary: FirebaseCloudSummary) : LedgerSyncState
    data class Error(val message: String) : LedgerSyncState
}

@OptIn(ExperimentalCoroutinesApi::class)
class VyoraAppViewModel(
    val currentUserId: String,
    private val auth: FirebaseAuth,
    private val userRepo: UserRepository,
    private val videoRepo: VideoRepository,
    private val chatRepo: ChatRepository,
    private val liveRepo: LiveRepository,
    private val notifRepo: NotificationRepository,
    private val walletAdminRepo: WalletAdminRepository,
    private val storageRepo: StorageMediaRepository,
    private val backendRepo: FirebaseBackendRepository,
    private val earnRepo: EarnProgramRepository,
    private val deviceIntegrityHelper: DeviceIntegrityHelper? = null,
    private val prefsRepo: AppPreferencesRepository? = null
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadStatus?>(null)
    val uploadState: StateFlow<UploadStatus?> = _uploadState.asStateFlow()

    private val _ledgerSyncState = MutableStateFlow<LedgerSyncState>(LedgerSyncState.Checking)
    val ledgerSyncState: StateFlow<LedgerSyncState> = _ledgerSyncState.asStateFlow()

    private val _isUpdatingAvatar = MutableStateFlow(false)
    val isUpdatingAvatar: StateFlow<Boolean> = _isUpdatingAvatar.asStateFlow()

    private val _earnDashboard = MutableStateFlow<EarnDashboardState?>(null)
    val earnDashboard: StateFlow<EarnDashboardState?> = _earnDashboard.asStateFlow()

    private val _earnAdminOverview = MutableStateFlow<EarnAdminOverview?>(null)
    val earnAdminOverview: StateFlow<EarnAdminOverview?> = _earnAdminOverview.asStateFlow()

    private val _isEarnBusy = MutableStateFlow(false)
    val isEarnBusy: StateFlow<Boolean> = _isEarnBusy.asStateFlow()

    private val _watchWheelState = MutableStateFlow(WatchWheelUiState())
    val watchWheelState: StateFlow<WatchWheelUiState> = _watchWheelState.asStateFlow()

    private var cachedDeviceIntegrity: DeviceIntegrityInfo? = null
    private var heartbeatJob: Job? = null

    private val _feedRefreshTrigger = MutableStateFlow(0)

    val currentUserProfile: StateFlow<UserProfile?> = userRepo.observeUser(currentUserId)
        .catch { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "Profile observe error", e)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val feedVideos: StateFlow<UiState<List<VideoPost>>> = _feedRefreshTrigger
        .flatMapLatest {
            videoRepo.observeActiveVideosState()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activeUsers: StateFlow<List<UserProfile>> = userRepo.observeActiveUsers()
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val followingIds: StateFlow<Set<String>> = userRepo.observeFollowingIds(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val blockedIds: StateFlow<Set<String>> = userRepo.observeBlockedIds(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val bookmarkedIds: StateFlow<Set<String>> = videoRepo.observeBookmarkedIds(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptySet())

    val followRequests: StateFlow<List<FollowRequest>> = userRepo.observeIncomingFollowRequests(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val conversations: StateFlow<List<Conversation>> = chatRepo.observeConversations(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> = notifRepo.observeNotifications(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeLiveStreams: StateFlow<List<LiveStream>> = liveRepo.observeActiveStreams()
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val sounds: StateFlow<List<SoundTrack>> = videoRepo.observeSounds()
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val hashtags: StateFlow<List<HashtagItem>> = videoRepo.observeHashtags()
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val wallet: StateFlow<Wallet?> = walletAdminRepo.observeWallet(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val transactions: StateFlow<List<WalletTransaction>> = walletAdminRepo.observeTransactions(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myWithdrawals: StateFlow<List<WithdrawalRequest>> = walletAdminRepo.observeMyWithdrawals(currentUserId)
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminSettings: StateFlow<AdminSettings> = walletAdminRepo.observeAdminSettings()
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminSettings())

    val adminUsers: StateFlow<List<UserProfile>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeAllUsersAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminVideos: StateFlow<List<VideoPost>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeAllVideosAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminReports: StateFlow<List<ModerationReport>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeReportsAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminWithdrawals: StateFlow<List<WithdrawalRequest>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeWithdrawalsAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminRoles: StateFlow<List<AdminRole>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeAdmins() else flowOf(emptyList()) }
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminLiveStreams: StateFlow<List<LiveStream>> = _isAdmin
        .flatMapLatest { admin -> if (admin) walletAdminRepo.observeLiveStreamsAdmin() else flowOf(emptyList()) }
        .catch { e -> if (e is CancellationException) throw e }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user != null) {
                userRepo.ensureUserProfile(
                    uid = user.uid,
                    email = user.email.orEmpty(),
                    displayName = user.displayName,
                    avatarUrl = user.photoUrl?.toString()
                )
                val adminStatus = walletAdminRepo.checkIsAdmin(user.uid, user.email)
                _isAdmin.value = adminStatus
                refreshAccountingSync()

                val device = resolveDeviceIntegrity()
                earnRepo.registerDeviceAndCode(device)

                val pendingCode = runCatching {
                    prefsRepo?.preferencesFlow?.first()?.pendingReferralCode.orEmpty()
                }.getOrDefault("")
                if (pendingCode.isNotBlank()) {
                    applyIncomingReferralCode(pendingCode)
                }

                refreshEarnDashboard()
                refreshWatchWheelStatus()
                if (adminStatus) {
                    refreshAdminEarnOverview()
                }
            }
        }
        viewModelScope.launch {
            adminSettings.collect { cfg ->
                _watchWheelState.value = _watchWheelState.value.copy(
                    enabled = cfg.watchWheelEnabled,
                    cycleSec = cfg.watchWheelCycleSec,
                    pointsPerCycle = cfg.watchWheelPointsPerCycle,
                    dailyCapCycles = cfg.watchWheelDailyCapCycles,
                    elapsedMillis = if (cfg.watchWheelEnabled) _watchWheelState.value.elapsedMillis else 0L
                )
            }
        }
    }

    private suspend fun resolveDeviceIntegrity(): DeviceIntegrityInfo {
        cachedDeviceIntegrity?.let { return it }
        val collected = deviceIntegrityHelper?.collectDeviceIntegrity(currentUserId)
            ?: DeviceIntegrityInfo(
                deviceFingerprint = DeviceIntegrityHelper.sha256Hex("fallback:$currentUserId"),
                appSetId = "",
                hardwareId = "",
                playIntegrityStatus = "unavailable",
                playIntegrityTokenHash = ""
            )
        cachedDeviceIntegrity = collected
        return collected
    }

    fun applyIncomingReferralCode(rawCode: String) {
        if (rawCode.isBlank()) return
        viewModelScope.launch {
            val device = resolveDeviceIntegrity()
            earnRepo.redeemReferralCode(rawCode, device)
                .onSuccess { underReview ->
                    prefsRepo?.clearPendingReferralCode()
                    _toastMessage.value = if (underReview) {
                        "تم تسجيل الدعوة والجهاز قيد المراجعة الإدارية"
                    } else {
                        "تم تفعيل رابط الدعوة بنجاح!"
                    }
                    earnRepo.fetchDashboard(device).onSuccess { _earnDashboard.value = it }
                }
                .onFailure { err ->
                    prefsRepo?.clearPendingReferralCode()
                    _toastMessage.value = err.localizedMessage
                }
        }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        if (!inForeground) {
            heartbeatJob?.cancel()
            heartbeatJob = null
            return
        }
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                val device = resolveDeviceIntegrity()
                earnRepo.sendHeartbeat(30L, device)
                earnRepo.fetchDashboard(device).onSuccess {
                    _earnDashboard.value = it
                }
            }
        }
    }

    fun refreshEarnDashboard() {
        viewModelScope.launch {
            _isEarnBusy.value = true
            val device = resolveDeviceIntegrity()
            earnRepo.fetchDashboard(device)
                .onSuccess { _earnDashboard.value = it }
                .onFailure { e ->
                    Log.w(TAG, "Earn dashboard load error", e)
                }
            _isEarnBusy.value = false
        }
    }

    fun refreshAdminEarnOverview() {
        if (!_isAdmin.value) return
        viewModelScope.launch {
            _isEarnBusy.value = true
            earnRepo.fetchAdminOverview()
                .onSuccess { _earnAdminOverview.value = it }
                .onFailure { e -> _toastMessage.value = e.localizedMessage }
            _isEarnBusy.value = false
        }
    }

    fun adminUpdateEarnConfig(config: EarnProgramConfig) {
        viewModelScope.launch {
            _isEarnBusy.value = true
            earnRepo.adminUpdateConfig(config)
                .onSuccess {
                    _toastMessage.value = "تم تحديث إعدادات برنامج اربح معنا"
                    earnRepo.fetchAdminOverview().onSuccess { _earnAdminOverview.value = it }
                    refreshEarnDashboard()
                }
                .onFailure { _toastMessage.value = it.localizedMessage }
            _isEarnBusy.value = false
        }
    }

    fun adminReviewEarnDevice(deviceFingerprint: String, decision: String, note: String) {
        viewModelScope.launch {
            _isEarnBusy.value = true
            earnRepo.adminReviewDevice(deviceFingerprint, decision, note)
                .onSuccess {
                    _toastMessage.value = "تم تحديث حالة الجهاز بنجاح"
                    earnRepo.fetchAdminOverview().onSuccess { _earnAdminOverview.value = it }
                    refreshEarnDashboard()
                }
                .onFailure { _toastMessage.value = it.localizedMessage }
            _isEarnBusy.value = false
        }
    }

    fun adminReviewEarnPayout(targetUid: String, approve: Boolean, note: String) {
        viewModelScope.launch {
            _isEarnBusy.value = true
            earnRepo.adminReviewPayout(targetUid, approve, note)
                .onSuccess {
                    _toastMessage.value = if (approve) {
                        "تم اعتماد المكافأة وإضافة النقاط إلى محفظة المستخدم"
                    } else {
                        "تم رفض طلب المكافأة وتسجيل السبب"
                    }
                    earnRepo.fetchAdminOverview().onSuccess { _earnAdminOverview.value = it }
                    refreshAccountingSync()
                    refreshEarnDashboard()
                }
                .onFailure { _toastMessage.value = it.localizedMessage }
            _isEarnBusy.value = false
        }
    }

    fun refreshAccountingSync() {
        viewModelScope.launch {
            _ledgerSyncState.value = LedgerSyncState.Checking
            backendRepo.fetchWalletAccountingSummary()
                .onSuccess { summary ->
                    _ledgerSyncState.value = LedgerSyncState.Synced(summary)
                }
                .onFailure { err ->
                    _ledgerSyncState.value = LedgerSyncState.Error(
                        err.localizedMessage ?: "Unable to sync Firebase ledger"
                    )
                }
        }
    }

    fun retryFeed() {
        _feedRefreshTrigger.value += 1
        refreshAccountingSync()
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    private fun resolveCurrentProfile(): UserProfile? {
        currentUserProfile.value?.let { return it }
        val authUser = auth.currentUser ?: return null
        val fallbackUsername = (authUser.email ?: "")
            .substringBefore("@")
            .lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .trim('_')
            .ifBlank { "creator_${authUser.uid.take(6)}" }
        return UserProfile(
            id = authUser.uid,
            userId = authUser.uid,
            username = fallbackUsername,
            displayName = authUser.displayName?.ifBlank { fallbackUsername } ?: fallbackUsername,
            avatarUrl = authUser.photoUrl?.toString().orEmpty()
        )
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearUploadState() {
        _uploadState.value = null
    }

    fun observeComments(videoId: String) = videoRepo.observeComments(videoId)
    fun observeMessages(conversationId: String) = chatRepo.observeMessages(conversationId, currentUserId)
    fun observeLiveComments(streamId: String) = liveRepo.observeLiveComments(streamId)
    fun observeLiveStream(streamId: String) = liveRepo.observeStream(streamId)

    suspend fun isVideoLiked(videoId: String): Boolean = videoRepo.isVideoLikedByMe(videoId)

    fun toggleLike(video: VideoPost) {
        val actor = resolveCurrentProfile() ?: return
        viewModelScope.launch {
            videoRepo.toggleLike(video, actor).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun toggleBookmark(videoId: String) {
        viewModelScope.launch {
            videoRepo.toggleBookmark(videoId).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    suspend fun fetchAgoraLiveSession(streamId: String): Result<AgoraLiveSession> =
        backendRepo.fetchAgoraLiveSession(streamId)

    fun recordView(videoId: String) {
        viewModelScope.launch {
            backendRepo.recordVideoViewServer(videoId).onFailure {
                videoRepo.recordViewIfNew(videoId)
            }
        }
    }

    fun recordShare(video: VideoPost) {
        val actor = resolveCurrentProfile() ?: return
        viewModelScope.launch {
            videoRepo.recordShare(video, actor)
        }
    }

    fun addComment(video: VideoPost, text: String) {
        val actor = resolveCurrentProfile() ?: return
        viewModelScope.launch {
            videoRepo.addComment(video, actor, text).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun followOrUnfollow(targetUser: UserProfile) {
        val actor = resolveCurrentProfile() ?: return
        val currentlyFollowing = targetUser.userId in followingIds.value
        viewModelScope.launch {
            if (currentlyFollowing) {
                userRepo.unfollowUser(targetUser.userId)
            } else {
                userRepo.followOrRequestUser(actor, targetUser).onSuccess { result ->
                    if (result == "requested") {
                        _toastMessage.value = "Follow request sent"
                    }
                }
            }.onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun respondToFollowRequest(request: FollowRequest, accept: Boolean) {
        val me = resolveCurrentProfile() ?: return
        viewModelScope.launch {
            userRepo.respondToFollowRequest(request, me, accept).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun toggleBlockUser(targetUserId: String) {
        val blocked = targetUserId in blockedIds.value
        viewModelScope.launch {
            userRepo.toggleBlockUser(targetUserId, blocked).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun uploadAndPublishVideo(
        context: Context,
        videoUri: Uri,
        customCoverUri: Uri?,
        caption: String,
        soundId: String,
        soundTitle: String,
        durationSec: Long,
        onPublished: () -> Unit
    ) {
        val creator = resolveCurrentProfile() ?: run {
            _toastMessage.value = "Please sign in first."
            return
        }
        viewModelScope.launch {
            val authUser = auth.currentUser
            if (currentUserProfile.value == null && authUser != null) {
                userRepo.ensureUserProfile(
                    uid = authUser.uid,
                    email = authUser.email.orEmpty(),
                    displayName = authUser.displayName,
                    avatarUrl = authUser.photoUrl?.toString()
                )
            }
            storageRepo.uploadVideoWithThumbnail(context, currentUserId, videoUri, customCoverUri)
                .collect { status ->
                    _uploadState.value = status
                    if (status is UploadStatus.Completed) {
                        videoRepo.publishVideo(
                            creator = creator,
                            videoUrl = status.downloadUrl,
                            thumbnailUrl = status.thumbnailUrl,
                            caption = caption,
                            soundId = soundId,
                            soundTitle = soundTitle,
                            durationSec = durationSec,
                            contentHash = status.contentHash,
                            fileSizeBytes = status.fileSizeBytes
                        ).onSuccess {
                            _toastMessage.value = "Video published!"
                            retryFeed()
                            refreshEarnDashboard()
                            onPublished()
                        }.onFailure { e ->
                            val msg = e.localizedMessage ?: "Failed to save video"
                            _toastMessage.value = msg
                            _uploadState.value = UploadStatus.Failed(msg)
                        }
                    }
                }
        }
    }

    fun updateVideoCaption(videoId: String, caption: String) {
        viewModelScope.launch {
            videoRepo.updateVideoCaption(videoId, caption).onSuccess {
                _toastMessage.value = "Updated"
            }.onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            videoRepo.deleteVideo(videoId).onSuccess {
                _toastMessage.value = "Deleted"
                refreshEarnDashboard()
            }.onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    fun updateProfileAvatar(avatarUri: Uri) {
        viewModelScope.launch {
            _isUpdatingAvatar.value = true
            val uploaded = storageRepo.uploadImage(currentUserId, "avatars", avatarUri)
            if (uploaded.isFailure) {
                _isUpdatingAvatar.value = false
                _toastMessage.value = uploaded.exceptionOrNull()?.localizedMessage
                    ?: "Failed to upload profile photo"
                return@launch
            }
            val avatarUrl = uploaded.getOrThrow()
            userRepo.updateAvatar(avatarUrl)
                .onSuccess { _toastMessage.value = "Profile photo updated" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save profile photo" }
            _isUpdatingAvatar.value = false
        }
    }

    fun updateProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUri: Uri?,
        currentAvatarUrl: String,
        isPrivate: Boolean,
        interests: List<String>? = null
    ) {
        viewModelScope.launch {
            _isUpdatingAvatar.value = avatarUri != null
            val finalAvatar = if (avatarUri != null) {
                val uploaded = storageRepo.uploadImage(currentUserId, "avatars", avatarUri)
                if (uploaded.isFailure) {
                    _isUpdatingAvatar.value = false
                    _toastMessage.value = uploaded.exceptionOrNull()?.localizedMessage
                        ?: "Failed to upload profile photo"
                    return@launch
                }
                uploaded.getOrThrow()
            } else {
                currentAvatarUrl
            }
            userRepo.updateProfile(username, displayName, bio, finalAvatar, isPrivate, interests)
                .onSuccess { _toastMessage.value = "Profile saved" }
                .onFailure { _toastMessage.value = it.localizedMessage ?: "Failed to save profile" }
            _isUpdatingAvatar.value = false
        }
    }

    fun updateUserPreferences(language: String, themeMode: String) {
        viewModelScope.launch {
            userRepo.updatePreferences(language, themeMode)
        }
    }

    suspend fun openConversationWith(peerId: String): String? {
        return chatRepo.getOrCreateConversation(peerId).onFailure {
            _toastMessage.value = it.localizedMessage
        }.getOrNull()
    }

    fun sendChatMessage(
        conversationId: String,
        participantIds: List<String>,
        type: String,
        text: String = "",
        mediaUri: Uri? = null,
        audioDurationMs: Long = 0L
    ) {
        val actor = resolveCurrentProfile() ?: return
        viewModelScope.launch {
            var mediaUrl = ""
            if (mediaUri != null) {
                val ext = when (type) {
                    "video" -> "mp4"
                    "audio" -> "m4a"
                    else -> "jpg"
                }
                val uploaded = storageRepo.uploadMediaFile(currentUserId, "chat_media", mediaUri, ext)
                if (uploaded.isFailure) {
                    _toastMessage.value = uploaded.exceptionOrNull()?.localizedMessage ?: "Media upload failed"
                    return@launch
                }
                mediaUrl = uploaded.getOrThrow()
            }
            chatRepo.sendMessage(conversationId, participantIds, actor, type, text, mediaUrl, audioDurationMs)
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun markMessageRead(conversationId: String, message: ChatMessage) {
        viewModelScope.launch {
            chatRepo.markMessageRead(conversationId, message)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatRepo.deleteConversation(conversationId).onFailure {
                _toastMessage.value = it.localizedMessage
            }
        }
    }

    suspend fun startLiveStream(title: String): LiveStream? {
        val host = resolveCurrentProfile() ?: run {
            _toastMessage.value = "Please sign in first."
            return null
        }
        val authUser = auth.currentUser
        if (currentUserProfile.value == null && authUser != null) {
            userRepo.ensureUserProfile(
                uid = authUser.uid,
                email = authUser.email.orEmpty(),
                displayName = authUser.displayName,
                avatarUrl = authUser.photoUrl?.toString()
            )
        }
        return liveRepo.startStream(host, title).onFailure {
            _toastMessage.value = it.localizedMessage ?: "Failed to start live broadcast"
        }.getOrNull()
    }

    fun endLiveStream(streamId: String) {
        viewModelScope.launch {
            liveRepo.endStream(streamId)
        }
    }

    fun joinOrLeaveLiveViewer(streamId: String, delta: Long) {
        viewModelScope.launch {
            liveRepo.updateViewerCount(streamId, delta)
        }
    }

    fun sendLiveLike(streamId: String) {
        viewModelScope.launch {
            liveRepo.sendLiveLike(streamId)
        }
    }

    fun sendLiveComment(streamId: String, text: String) {
        val actor = resolveCurrentProfile() ?: return
        viewModelScope.launch {
            liveRepo.sendLiveComment(streamId, actor, text)
        }
    }

    fun createSoundTrack(title: String, artist: String, audioUri: Uri?, durationSec: Long) {
        viewModelScope.launch {
            val url = if (audioUri != null) {
                storageRepo.uploadMediaFile(currentUserId, "sounds", audioUri, "m4a").getOrElse {
                    _toastMessage.value = it.localizedMessage
                    return@launch
                }
            } else {
                ""
            }
            videoRepo.createSoundTrack(title, artist, url, durationSec, isLicensed = true)
                .onSuccess { _toastMessage.value = "Sound added" }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun markNotificationsRead(list: List<AppNotification>) {
        viewModelScope.launch {
            notifRepo.markAllRead(list)
        }
    }

    fun submitReport(targetType: String, targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            walletAdminRepo.submitReport(targetType, targetId, reason, details)
                .onSuccess { _toastMessage.value = "Report submitted" }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun requestWithdrawal(amountUsd: Double, method: String, accountDetails: String) {
        val settings = adminSettings.value
        val myWallet = wallet.value
        if (amountUsd < settings.minWithdrawalUsd) {
            _toastMessage.value = "Minimum withdrawal is $${settings.minWithdrawalUsd}"
            return
        }
        if (myWallet == null || myWallet.balanceUsd < amountUsd) {
            _toastMessage.value = "Insufficient balance"
            return
        }
        viewModelScope.launch {
            backendRepo.submitWithdrawalServer(amountUsd, method, accountDetails)
                .onSuccess {
                    _toastMessage.value = "Withdrawal request submitted"
                    refreshAccountingSync()
                }
                .onFailure { err ->
                    _toastMessage.value = err.localizedMessage
                }
        }
    }

    fun adminSetUserBanned(targetUid: String, banned: Boolean) {
        viewModelScope.launch {
            walletAdminRepo.setUserBanned(targetUid, banned).onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminRemoveVideo(videoId: String) {
        viewModelScope.launch {
            walletAdminRepo.removeVideoAdmin(videoId).onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminResolveReport(reportId: String, status: String) {
        viewModelScope.launch {
            walletAdminRepo.updateReportStatus(reportId, status).onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminReviewWithdrawal(request: WithdrawalRequest, approve: Boolean) {
        viewModelScope.launch {
            backendRepo.adminReviewWithdrawalServer(request.id, approve)
                .onSuccess { refreshAccountingSync() }
                .onFailure { err -> _toastMessage.value = err.localizedMessage }
        }
    }

    fun adminGrantPoints(targetUid: String, points: Long, reason: String) {
        viewModelScope.launch {
            backendRepo.adminGrantPointsServer(targetUid, points, reason)
                .onSuccess {
                    _toastMessage.value = "Points updated"
                    refreshAccountingSync()
                }
                .onFailure { err -> _toastMessage.value = err.localizedMessage }
        }
    }

    fun adminSaveSettings(pointValueUsd: Double, minWithdrawalUsd: Double, rewardsEnabled: Boolean, maxVideoDurationSec: Long) {
        viewModelScope.launch {
            walletAdminRepo.saveAdminSettings(pointValueUsd, minWithdrawalUsd, rewardsEnabled, maxVideoDurationSec)
                .onSuccess { _toastMessage.value = "Settings saved" }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminAssignRole(targetUid: String, email: String, role: String) {
        viewModelScope.launch {
            walletAdminRepo.assignAdminRole(targetUid, email, role)
                .onSuccess { _toastMessage.value = "Admin role updated" }
                .onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminRemoveRole(targetUid: String) {
        viewModelScope.launch {
            walletAdminRepo.removeAdminRole(targetUid).onFailure { _toastMessage.value = it.localizedMessage }
        }
    }

    fun adminEndLiveStream(streamId: String) {
        viewModelScope.launch {
            liveRepo.endStream(streamId)
        }
    }

    fun refreshWatchWheelStatus() {
        viewModelScope.launch {
            backendRepo.fetchWatchWheelStatus().onSuccess { status ->
                _watchWheelState.value = _watchWheelState.value.copy(
                    enabled = status.enabled,
                    cycleSec = status.cycleSec,
                    pointsPerCycle = status.pointsPerCycle,
                    dailyCapCycles = status.dailyCapCycles,
                    todayCycles = status.todayCycles
                )
            }
        }
    }

    fun onFeedPlaybackTick(
        videoId: String,
        deltaMillis: Long,
        isScreenActive: Boolean,
        isVideoPlaying: Boolean
    ) {
        val current = _watchWheelState.value
        val settings = adminSettings.value
        val effectiveEnabled = settings.watchWheelEnabled && current.enabled
        val outcome = WatchWheelLogic.advanceTick(
            currentElapsedMillis = current.elapsedMillis,
            deltaMillis = deltaMillis,
            cycleSec = settings.watchWheelCycleSec,
            isEnabled = effectiveEnabled,
            isScreenActive = isScreenActive,
            isVideoPlaying = isVideoPlaying,
            isClaiming = current.isClaiming,
            isDailyCapReached = current.dailyCapReached
        )
        _watchWheelState.value = current.copy(
            enabled = effectiveEnabled,
            cycleSec = settings.watchWheelCycleSec,
            pointsPerCycle = settings.watchWheelPointsPerCycle,
            dailyCapCycles = settings.watchWheelDailyCapCycles,
            elapsedMillis = outcome.nextElapsedMillis,
            isActivelyPlaying = effectiveEnabled && isScreenActive && isVideoPlaying
        )
        if (outcome.cycleCompleted) {
            claimWatchWheelCycle(videoId, outcome.completedWatchSeconds)
        }
    }

    private fun claimWatchWheelCycle(videoId: String, watchedSec: Long) {
        if (_watchWheelState.value.isClaiming) return
        viewModelScope.launch {
            _watchWheelState.value = _watchWheelState.value.copy(isClaiming = true)
            backendRepo.claimWatchWheelReward(videoId = videoId, watchedSec = watchedSec)
                .onSuccess { res ->
                    _watchWheelState.value = _watchWheelState.value.copy(
                        isClaiming = false,
                        todayCycles = res.todayCycles,
                        dailyCapCycles = res.dailyCapCycles,
                        lastAwardedPoints = if (res.awarded && res.pointsDelta > 0L) res.pointsDelta else null,
                        statusNotice = when {
                            res.reason == "daily_cap_reached" -> "تم الوصول للحد اليومي لعجلة المشاهدة"
                            res.reason == "disabled" -> "تم إيقاف عجلة الربح من الإدارة"
                            else -> ""
                        }
                    )
                    if (res.awarded && res.pointsDelta > 0L) {
                        delay(2400L)
                        _watchWheelState.value = _watchWheelState.value.copy(lastAwardedPoints = null)
                    }
                }
                .onFailure { err ->
                    Log.w(TAG, "Watch wheel claim error", err)
                    _watchWheelState.value = _watchWheelState.value.copy(
                        isClaiming = false,
                        statusNotice = err.localizedMessage.orEmpty()
                    )
                }
        }
    }

    fun adminUpdateWatchWheelSettings(
        enabled: Boolean,
        cycleSec: Long = adminSettings.value.watchWheelCycleSec,
        pointsPerCycle: Long = adminSettings.value.watchWheelPointsPerCycle,
        dailyCapCycles: Long = adminSettings.value.watchWheelDailyCapCycles
    ) {
        viewModelScope.launch {
            walletAdminRepo.updateWatchWheelSettings(
                enabled = enabled,
                cycleSec = cycleSec,
                pointsPerCycle = pointsPerCycle,
                dailyCapCycles = dailyCapCycles
            ).onSuccess {
                _watchWheelState.value = _watchWheelState.value.copy(
                    enabled = enabled,
                    cycleSec = cycleSec.coerceIn(10L, 180L),
                    pointsPerCycle = pointsPerCycle.coerceIn(1L, 500L),
                    dailyCapCycles = dailyCapCycles.coerceIn(1L, 500L),
                    elapsedMillis = if (enabled) _watchWheelState.value.elapsedMillis else 0L
                )
                _toastMessage.value = if (enabled) {
                    "تم تشغيل عجلة الربح من المشاهدات"
                } else {
                    "تم إيقاف عجلة الربح من المشاهدات"
                }
            }.onFailure { err ->
                _toastMessage.value = err.localizedMessage ?: "تعذر تحديث إعدادات عجلة الربح"
            }
        }
    }

    private companion object {
        const val TAG = "VyoraAppVM"
    }
}
