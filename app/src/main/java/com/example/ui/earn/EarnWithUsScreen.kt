package com.example.ui.earn

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.EarnDashboardState
import com.example.data.model.EarnFriendProgress
import com.example.earn.EarnLogic
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.VyoraAvatar
import com.example.ui.components.VyoraEmptyState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PulseMagenta
import com.example.ui.theme.vString
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarnWithUsScreen(
    viewModel: VyoraAppViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dashboardState by viewModel.earnDashboard.collectAsStateWithLifecycle()
    val isBusy by viewModel.isEarnBusy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshEarnDashboard()
    }

    val dash = dashboardState ?: EarnDashboardState()
    val cfg = dash.config
    val inviteLink = if (dash.referralCode.isNotBlank()) {
        EarnLogic.buildReferralInviteUri(dash.referralCode)
    } else {
        ""
    }

    val (statusLabel, statusColor) = when (dash.status) {
        "under_review" -> vString(R.string.earn_status_under_review) to AmberGold
        "eligible" -> vString(R.string.earn_status_eligible) to NeonCyan
        "approved" -> vString(R.string.earn_status_approved) to EmeraldLive
        "rejected" -> vString(R.string.earn_status_rejected) to CrimsonError
        else -> vString(R.string.earn_status_in_progress) to ElectricViolet
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vString(R.string.earn_program_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("earn_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshEarnDashboard() },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("earn_refresh_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = vString(R.string.sync_now))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!cfg.isEnabled) {
                    item {
                        Surface(
                            color = AmberGold.copy(alpha = 0.16f),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("earn_paused_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.PauseCircle, contentDescription = null, tint = AmberGold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = vString(R.string.earn_paused_banner),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AmberGold
                                )
                            }
                        }
                    }
                }

                // Hero Card: Target 1500 EGP + Progress + Status
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(ElectricViolet, Color(0xFF4C1D95), PulseMagenta.copy(alpha = 0.85f))
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = AmberGold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = vString(R.string.earn_reward_amount, cfg.rewardEgp.toInt().toString()),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = statusColor.copy(alpha = 0.28f),
                                    modifier = Modifier.testTag("earn_status_badge")
                                ) {
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Text(
                                text = vString(R.string.earn_reward_points, cfg.rewardPoints.toString()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.88f)
                            )

                            val pctInt = dash.progressPercent.roundToInt().coerceIn(0, 100)
                            LinearProgressIndicator(
                                progress = { (pctInt / 100f).coerceIn(0f, 1f) },
                                color = NeonCyan,
                                trackColor = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$pctInt%",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                                if (dash.cairoDate.isNotBlank()) {
                                    Text(
                                        text = "Cairo: ${dash.cairoDate}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            if (dash.reviewNote.isNotBlank()) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = Color.Black.copy(alpha = 0.25f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = dash.reviewNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Device / Anti-Fraud Notice Card
                if (dash.suspiciousFlag || !dash.isDeviceEligible) {
                    item {
                        Surface(
                            color = AmberGold.copy(alpha = 0.14f),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AmberGold.copy(alpha = 0.45f), MaterialTheme.shapes.large)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = AmberGold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = vString(R.string.earn_friend_under_review),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AmberGold
                                    )
                                    Text(
                                        text = vString(R.string.earn_security_notice),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // KPI Grid: Personal Videos, Invited Friends, Completed Friends
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        EarnMetricCard(
                            icon = Icons.Filled.VideoLibrary,
                            title = vString(R.string.earn_personal_videos),
                            value = "${dash.personalVideosCount} / ${cfg.inviterVideosRequired}",
                            accent = ElectricViolet,
                            modifier = Modifier.weight(1f)
                        )
                        EarnMetricCard(
                            icon = Icons.Filled.GroupAdd,
                            title = vString(R.string.earn_invited_friends),
                            value = "${dash.invitedFriendsCount} / ${cfg.requiredFriends}",
                            accent = NeonCyan,
                            modifier = Modifier.weight(1f)
                        )
                        EarnMetricCard(
                            icon = Icons.Filled.CheckCircle,
                            title = vString(R.string.earn_completed_friends),
                            value = "${dash.completedFriendsCount} / ${cfg.requiredFriends}",
                            accent = EmeraldLive,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // User's Own Daily Progress
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Timer, contentDescription = null, tint = NeonCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = vString(R.string.earn_my_daily_usage),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = vString(R.string.earn_friend_minutes, dash.myTodayMinutes, cfg.friendDailyMinutesRequired),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = vString(R.string.earn_friend_videos, dash.myTodayVideos, cfg.friendDailyVideosRequired),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = vString(R.string.earn_friend_days, dash.myConsecutiveDays, cfg.friendDaysRequired),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldLive
                                )
                            }
                        }
                    }
                }

                // Direct Automatic Invite Link Card (Copy Link & Share Link)
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Link, contentDescription = null, tint = NeonCyan)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = vString(R.string.earn_invite_link_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ElectricViolet.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = inviteLink.ifBlank { "…" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .testTag("earn_my_referral_link")
                                )
                            }
                            Text(
                                text = vString(R.string.earn_auto_link_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val copiedMsg = vString(R.string.earn_link_copied)
                            val shareText = vString(R.string.earn_share_link_message, inviteLink)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (inviteLink.isNotBlank()) {
                                            clipboard.setText(AnnotatedString(inviteLink))
                                            viewModel.showToast(copiedMsg)
                                        }
                                    },
                                    enabled = inviteLink.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("earn_copy_link_button")
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(vString(R.string.earn_copy_link))
                                }
                                Button(
                                    onClick = {
                                        if (inviteLink.isNotBlank()) {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, null))
                                        }
                                    },
                                    enabled = inviteLink.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("earn_share_link_button")
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(vString(R.string.earn_share_invite))
                                }
                            }
                        }
                    }
                }

                // Automatic Attribution Status + Security Note (no manual input field)
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Security, contentDescription = null, tint = ElectricViolet)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = vString(R.string.earn_auto_attribution_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (dash.referredByCode.isNotBlank()) {
                                Surface(
                                    color = EmeraldLive.copy(alpha = 0.14f),
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = EmeraldLive, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = vString(R.string.earn_redeemed_badge),
                                            color = EmeraldLive,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = vString(R.string.earn_security_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Invited Friends Progress List
                item {
                    Text(
                        text = vString(R.string.earn_friends_progress_title, dash.invitedFriendsCount, cfg.requiredFriends),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (dash.friends.isEmpty()) {
                    item {
                        VyoraEmptyState(
                            icon = Icons.Filled.GroupAdd,
                            title = vString(R.string.earn_invited_friends),
                            subtitle = vString(R.string.earn_no_friends_yet)
                        )
                    }
                } else {
                    items(dash.friends, key = { it.userId }) { friend ->
                        EarnFriendProgressCard(friend = friend)
                    }
                }
            }
        }
    }
}

@Composable
private fun EarnMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
fun EarnFriendProgressCard(friend: EarnFriendProgress) {
    val badgeColor = when {
        friend.isCompleted -> EmeraldLive
        friend.suspiciousFlag || !friend.isDeviceEligible -> AmberGold
        else -> NeonCyan
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VyoraAvatar(
                    avatarUrl = friend.avatarUrl,
                    name = friend.displayName.ifBlank { friend.username },
                    size = 44.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = friend.displayName.ifBlank { "@${friend.username}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${friend.username} • ${friend.registeredAt.take(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = when {
                            friend.isCompleted -> vString(R.string.earn_friend_completed)
                            friend.suspiciousFlag || !friend.isDeviceEligible -> vString(R.string.earn_friend_under_review)
                            else -> "${friend.completedDays}/${friend.requiredDays}"
                        },
                        color = badgeColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            val dayRatio = (friend.completedDays.toFloat() / friend.requiredDays.coerceAtLeast(1)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { dayRatio },
                color = badgeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = vString(R.string.earn_friend_days, friend.completedDays, friend.requiredDays),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = vString(R.string.earn_friend_minutes, friend.todayActiveMinutes, friend.requiredDailyMinutes),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = vString(R.string.earn_friend_videos, friend.todayValidVideos, friend.requiredDailyVideos),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
