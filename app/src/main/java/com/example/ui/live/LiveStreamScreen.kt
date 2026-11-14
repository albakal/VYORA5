package com.example.ui.live

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.LiveStream
import com.example.data.remote.AgoraLiveSession
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.VyoraAvatar
import com.example.ui.components.VyoraEmptyState
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PulseMagenta
import com.example.ui.theme.vString
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLobbyScreen(
    viewModel: VyoraAppViewModel,
    onBack: () -> Unit,
    onEnterStream: (streamId: String, isHost: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val streams by viewModel.activeLiveStreams.collectAsStateWithLifecycle()
    var showStartDialog by remember { mutableStateOf(false) }
    var streamTitle by remember { mutableStateOf("") }
    var isStartingLive by remember { mutableStateOf(false) }

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasPermissions = res[Manifest.permission.CAMERA] == true && res[Manifest.permission.RECORD_AUDIO] == true
        if (hasPermissions) {
            showStartDialog = true
        } else {
            viewModel.showToast(context.getString(R.string.permission_camera_audio))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vString(R.string.tab_live)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (hasPermissions) {
                                showStartDialog = true
                            } else {
                                permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PulseMagenta),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("open_go_live_dialog_btn")
                    ) {
                        Text(vString(R.string.studio_go_live), color = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        if (streams.isEmpty()) {
            VyoraEmptyState(
                icon = Icons.Filled.LiveTv,
                title = vString(R.string.no_live_streams),
                subtitle = vString(R.string.studio_go_live),
                actionLabel = vString(R.string.start_live_btn),
                onAction = {
                    if (hasPermissions) showStartDialog = true
                    else permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(streams, key = { it.id }) { stream ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEnterStream(stream.id, stream.hostId == viewModel.currentUserId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VyoraAvatar(
                                avatarUrl = stream.hostAvatarUrl,
                                name = stream.hostUsername,
                                size = 54.dp,
                                isLive = true
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stream.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("@${stream.hostUsername}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = PulseMagenta
                            ) {
                                Text(
                                    text = vString(R.string.live_viewers, stream.viewerCount.toInt()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartDialog) {
        AlertDialog(
            onDismissRequest = { if (!isStartingLive) showStartDialog = false },
            title = { Text(vString(R.string.start_live_btn)) },
            text = {
                OutlinedTextField(
                    value = streamTitle,
                    onValueChange = { streamTitle = it },
                    label = { Text(vString(R.string.live_title_hint)) },
                    enabled = !isStartingLive,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_title_input")
                )
            },
            confirmButton = {
                Button(
                    enabled = !isStartingLive,
                    modifier = Modifier.testTag("confirm_start_live_btn"),
                    onClick = {
                        isStartingLive = true
                        scope.launch {
                            val created = viewModel.startLiveStream(streamTitle)
                            isStartingLive = false
                            if (created != null) {
                                showStartDialog = false
                                streamTitle = ""
                                onEnterStream(created.id, true)
                            }
                        }
                    }
                ) {
                    if (isStartingLive) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(vString(R.string.start_live_btn))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isStartingLive,
                    onClick = { showStartDialog = false }
                ) {
                    Text(vString(R.string.dismiss))
                }
            }
        )
    }
}

@Composable
fun LiveRoomScreen(
    streamId: String,
    isHost: Boolean,
    viewModel: VyoraAppViewModel,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val stream by viewModel.observeLiveStream(streamId).collectAsStateWithLifecycle(initialValue = null)
    val comments by viewModel.observeLiveComments(streamId).collectAsStateWithLifecycle(initialValue = emptyList())
    val activeUsers by viewModel.activeUsers.collectAsStateWithLifecycle()
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()

    var commentText by remember { mutableStateOf("") }
    var isMicMuted by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var remoteUid by remember { mutableIntStateOf(0) }
    var rtcEngine by remember { mutableStateOf<RtcEngine?>(null) }
    var liveSession by remember { mutableStateOf<AgoraLiveSession?>(null) }
    var isFetchingToken by remember { mutableStateOf(true) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    var tokenRetryKey by remember { mutableIntStateOf(0) }

    val handleExit: () -> Unit = {
        if (isHost) {
            viewModel.endLiveStream(streamId)
        } else {
            viewModel.joinOrLeaveLiveViewer(streamId, -1L)
        }
        onExit()
    }

    BackHandler(onBack = handleExit)

    LaunchedEffect(streamId, isHost) {
        if (!isHost) {
            viewModel.joinOrLeaveLiveViewer(streamId, 1L)
        }
    }

    LaunchedEffect(streamId, tokenRetryKey) {
        isFetchingToken = true
        tokenError = null
        viewModel.fetchAgoraLiveSession(streamId)
            .onSuccess { session ->
                liveSession = session
                isFetchingToken = false
            }
            .onFailure { err ->
                tokenError = err.localizedMessage
                isFetchingToken = false
            }
    }

    // Initialize real Agora RTC Engine using server-issued App ID & short-lived RTC token
    DisposableEffect(liveSession) {
        val session = liveSession
        if (session != null) {
            try {
                val config = RtcEngineConfig().apply {
                    mContext = context.applicationContext
                    mAppId = session.appId
                    mEventHandler = object : IRtcEngineEventHandler() {
                        override fun onUserJoined(uid: Int, elapsed: Int) {
                            mainExecutor.execute { remoteUid = uid }
                        }

                        override fun onUserOffline(uid: Int, reason: Int) {
                            mainExecutor.execute {
                                if (remoteUid == uid) remoteUid = 0
                            }
                        }

                        override fun onError(err: Int) {
                            mainExecutor.execute {
                                tokenError = "Agora RTC error ($err)"
                            }
                        }
                    }
                }
                val engine = RtcEngine.create(config)
                engine.enableVideo()
                val options = ChannelMediaOptions().apply {
                    channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                    clientRoleType = if (isHost) Constants.CLIENT_ROLE_BROADCASTER else Constants.CLIENT_ROLE_AUDIENCE
                }
                if (isHost) {
                    engine.startPreview()
                }
                engine.joinChannel(session.token, session.channelName, session.rtcUid, options)
                rtcEngine = engine
            } catch (e: Exception) {
                tokenError = e.localizedMessage ?: "Failed to start live video engine"
                rtcEngine = null
            }
        }

        onDispose {
            rtcEngine?.let { engine ->
                try {
                    engine.leaveChannel()
                    RtcEngine.destroy()
                } catch (_: Exception) {
                }
            }
            rtcEngine = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF140D26), Color.Black))
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Agora Video Canvas (Local for Host, Remote for Audience) or local CameraX preview fallback for Host
        val hasCameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (rtcEngine != null) {
            if (isHost) {
                key(rtcEngine) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).also { sv ->
                                rtcEngine?.setupLocalVideo(VideoCanvas(sv, VideoCanvas.RENDER_MODE_HIDDEN, 0))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (remoteUid != 0) {
                key(rtcEngine, remoteUid) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).also { sv ->
                                rtcEngine?.setupRemoteVideo(VideoCanvas(sv, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else if (isHost && !isFetchingToken && hasCameraPerm) {
            val fallbackPreviewView = remember { PreviewView(context) }
            DisposableEffect(isFrontCamera) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = fallbackPreviewView.surfaceProvider
                        }
                        val selector = if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                    } catch (_: Exception) {
                    }
                }, mainExecutor)

                onDispose {
                    runCatching {
                        if (cameraProviderFuture.isDone) {
                            cameraProviderFuture.get().unbindAll()
                        }
                    }
                }
            }
            AndroidView(
                factory = { fallbackPreviewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isFetchingToken) {
            androidx.compose.material3.CircularProgressIndicator(
                color = NeonCyan,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (liveSession == null) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isHost && hasCameraPerm) vString(R.string.live_preview_local_note)
                        else vString(R.string.agora_missing_warning),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!tokenError.isNullOrBlank()) {
                        Text(
                            text = tokenError.orEmpty(),
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(
                        onClick = { tokenRetryKey += 1 },
                        modifier = Modifier.testTag("retry_live_token_button")
                    ) {
                        Text(vString(R.string.retry), color = NeonCyan)
                    }
                }
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VyoraAvatar(
                    avatarUrl = stream?.hostAvatarUrl.orEmpty(),
                    name = stream?.hostUsername ?: "Host",
                    size = 42.dp,
                    isLive = true
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(stream?.title ?: "LIVE", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = vString(R.string.live_viewers, (stream?.viewerCount ?: 1L).toInt()),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isHost && stream != null && stream!!.hostId !in followingIds) {
                    Button(
                        onClick = {
                            activeUsers.firstOrNull { it.userId == stream!!.hostId }?.let {
                                viewModel.followOrUnfollow(it)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PulseMagenta)
                    ) {
                        Text(vString(R.string.follow), color = Color.White)
                    }
                }
                IconButton(onClick = {
                    if (isHost) {
                        viewModel.endLiveStream(streamId)
                    } else {
                        viewModel.joinOrLeaveLiveViewer(streamId, -1L)
                    }
                    onExit()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = vString(R.string.end_live_btn), tint = Color.White)
                }
            }
        }

        // Bottom Live Comments + Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "@${comment.username}: ${comment.text}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text(vString(R.string.comment_hint), color = Color.White.copy(alpha = 0.6f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            viewModel.sendLiveComment(streamId, commentText)
                            commentText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ElectricViolet)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = vString(R.string.send), tint = Color.White)
                }
                Spacer(modifier = Modifier.width(6.dp))
                if (isHost) {
                    IconButton(onClick = {
                        isMicMuted = !isMicMuted
                        rtcEngine?.muteLocalAudioStream(isMicMuted)
                    }) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = {
                        isFrontCamera = !isFrontCamera
                        rtcEngine?.switchCamera()
                    }) {
                        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch Camera", tint = Color.White)
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.sendLiveLike(streamId) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(PulseMagenta)
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Like", tint = Color.White)
                    }
                }
            }
        }
    }
}
