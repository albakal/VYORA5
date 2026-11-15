package com.example.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.EarnAdminOverview
import com.example.data.model.EarnAdminParticipant
import com.example.data.model.EarnDeviceFlag
import com.example.data.model.EarnProgramConfig
import com.example.ui.VyoraAppViewModel
import com.example.ui.components.VyoraAvatar
import com.example.ui.earn.EarnFriendProgressCard
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.vString

@Composable
fun AdminEarnSection(
    viewModel: VyoraAppViewModel
) {
    val overviewState by viewModel.earnAdminOverview.collectAsStateWithLifecycle()
    val isBusy by viewModel.isEarnBusy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshAdminEarnOverview()
    }

    val overview = overviewState ?: EarnAdminOverview()
    val cfg = overview.config

    var isEnabled by remember(cfg) { mutableStateOf(cfg.isEnabled) }
    var rewardEgpStr by remember(cfg) { mutableStateOf(cfg.rewardEgp.toInt().toString()) }
    var rewardPointsStr by remember(cfg) { mutableStateOf(cfg.rewardPoints.toString()) }
    var inviterVidsStr by remember(cfg) { mutableStateOf(cfg.inviterVideosRequired.toString()) }
    var requiredFriendsStr by remember(cfg) { mutableStateOf(cfg.requiredFriends.toString()) }
    var daysReqStr by remember(cfg) { mutableStateOf(cfg.friendDaysRequired.toString()) }
    var minutesReqStr by remember(cfg) { mutableStateOf(cfg.friendDailyMinutesRequired.toString()) }
    var dailyVidsReqStr by remember(cfg) { mutableStateOf(cfg.friendDailyVideosRequired.toString()) }

    var inspectParticipant by remember { mutableStateOf<EarnAdminParticipant?>(null) }
    var reviewPayoutTarget by remember { mutableStateOf<Pair<EarnAdminParticipant, Boolean>?>(null) }
    var reviewDeviceTarget by remember { mutableStateOf<Pair<EarnDeviceFlag, String>?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master Toggle + Rules Config Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = vString(R.string.earn_program_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { viewModel.refreshAdminEarnOverview() },
                            enabled = !isBusy
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = (if (isEnabled) EmeraldLive else CrimsonError).copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = vString(R.string.earn_admin_enable_toggle),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    isEnabled = checked
                                    viewModel.adminUpdateEarnConfig(cfg.copy(isEnabled = checked))
                                },
                                modifier = Modifier.testTag("admin_earn_master_toggle")
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = rewardEgpStr,
                            onValueChange = { rewardEgpStr = it },
                            label = { Text("Reward EGP") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rewardPointsStr,
                            onValueChange = { rewardPointsStr = it },
                            label = { Text("Reward Points") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inviterVidsStr,
                            onValueChange = { inviterVidsStr = it },
                            label = { Text("Personal Videos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = requiredFriendsStr,
                            onValueChange = { requiredFriendsStr = it },
                            label = { Text("Friends Needed") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = daysReqStr,
                            onValueChange = { daysReqStr = it },
                            label = { Text("Streak Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutesReqStr,
                            onValueChange = { minutesReqStr = it },
                            label = { Text("Min/Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dailyVidsReqStr,
                            onValueChange = { dailyVidsReqStr = it },
                            label = { Text("Vids/Day") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.adminUpdateEarnConfig(
                                EarnProgramConfig(
                                    isEnabled = isEnabled,
                                    rewardEgp = rewardEgpStr.toDoubleOrNull() ?: 1500.0,
                                    rewardPoints = rewardPointsStr.toLongOrNull() ?: 150_000L,
                                    inviterVideosRequired = (inviterVidsStr.toIntOrNull() ?: 10).coerceAtLeast(1),
                                    requiredFriends = (requiredFriendsStr.toIntOrNull() ?: 10).coerceAtLeast(1),
                                    friendDaysRequired = (daysReqStr.toIntOrNull() ?: 10).coerceAtLeast(1),
                                    friendDailyMinutesRequired = (minutesReqStr.toIntOrNull() ?: 30).coerceAtLeast(1),
                                    friendDailyVideosRequired = (dailyVidsReqStr.toIntOrNull() ?: 2).coerceAtLeast(1)
                                )
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_save_earn_config_button")
                    ) {
                        Text(vString(R.string.save_changes))
                    }
                }
            }
        }

        // Flagged / Shared Devices Review
        if (overview.flaggedDevices.isNotEmpty()) {
            item {
                Text(
                    text = vString(R.string.earn_admin_suspicious_devices),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(overview.flaggedDevices, key = { it.deviceFingerprint }) { dev ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Devices, contentDescription = null, tint = AmberGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Device: ${dev.deviceFingerprint.take(14)}…",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Accounts (${dev.linkedUserIds.size}) • Reason: ${dev.flagReason.ifBlank { "shared" }} • Play: ${dev.playIntegrityStatus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Decision: ${dev.adminDecision.uppercase()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when (dev.adminDecision) {
                                        "approved_exception" -> EmeraldLive
                                        "blocked" -> CrimsonError
                                        else -> AmberGold
                                    }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { reviewDeviceTarget = dev to "approved_exception" },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(vString(R.string.earn_admin_approve_exception), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { reviewDeviceTarget = dev to "blocked" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(vString(R.string.earn_admin_block_device), color = CrimsonError, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Participants & Invited Friends Inspector
        item {
            Text(
                text = "Participants (${overview.participants.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(overview.participants, key = { it.userId }) { p ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VyoraAvatar(
                            avatarUrl = p.avatarUrl,
                            name = p.displayName.ifBlank { p.username },
                            size = 42.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${p.username} (${p.referralCode})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Videos: ${p.personalVideosCount}/${cfg.inviterVideosRequired} • Invited: ${p.invitedFriendsCount}/${cfg.requiredFriends} • Completed: ${p.completedFriendsCount}/${cfg.requiredFriends}",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonCyan
                            )
                            Text(
                                text = "Status: ${p.status.uppercase()} • Integrity: ${p.playIntegrityStatus}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (p.suspiciousFlag) AmberGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { inspectParticipant = p },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Friends (${p.friends.size})")
                        }
                        if (p.status != "approved" && p.paidOutAt.isBlank()) {
                            Button(
                                onClick = { reviewPayoutTarget = p to true },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldLive),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(vString(R.string.approve))
                            }
                            OutlinedButton(
                                onClick = { reviewPayoutTarget = p to false },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(vString(R.string.reject), color = CrimsonError)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldLive.copy(alpha = 0.16f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = vString(R.string.earn_status_approved),
                                    color = EmeraldLive,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Payout Logs
        if (overview.payoutLogs.isNotEmpty()) {
            item {
                Text(
                    text = vString(R.string.earn_admin_payout_logs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(overview.payoutLogs, key = { it.id }) { log ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "@${log.username} • ${log.decision.uppercase()} (${log.rewardEgp.toInt()} EGP / ${log.rewardPoints} pts)",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (log.decision == "approved") EmeraldLive else CrimsonError
                        )
                        if (log.note.isNotBlank()) {
                            Text(log.note, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = log.createdAt.take(19),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Friends Drill-down Dialog
    inspectParticipant?.let { participant ->
        AlertDialog(
            onDismissRequest = { inspectParticipant = null },
            title = { Text("@${participant.username} (${participant.referralCode})") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (participant.friends.isEmpty()) {
                        Text("No friends linked to this referral code yet.")
                    } else {
                        participant.friends.forEach { friend ->
                            EarnFriendProgressCard(friend = friend)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectParticipant = null }) {
                    Text(vString(R.string.dismiss))
                }
            }
        )
    }

    // Approve / Reject Payout With Reason Dialog
    reviewPayoutTarget?.let { (participant, approve) ->
        var note by remember {
            mutableStateOf(
                if (approve) "تم التحقق من استيفاء جميع شروط اربح معنا"
                else "لم يتم استيفاء الشروط المطلوبة"
            )
        }
        AlertDialog(
            onDismissRequest = { reviewPayoutTarget = null },
            title = {
                Text(if (approve) "Approve ${cfg.rewardEgp.toInt()} EGP Reward (@${participant.username})" else "Reject Reward (@${participant.username})")
            },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reason / Admin Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.adminReviewEarnPayout(participant.userId, approve, note)
                        reviewPayoutTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (approve) EmeraldLive else CrimsonError
                    )
                ) {
                    Text(if (approve) vString(R.string.approve) else vString(R.string.reject))
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewPayoutTarget = null }) {
                    Text(vString(R.string.dismiss))
                }
            }
        )
    }

    // Device Exception / Block Dialog
    reviewDeviceTarget?.let { (dev, decision) ->
        var note by remember {
            mutableStateOf(
                if (decision == "approved_exception") "تم التحقق كجهاز عائلي مشترك موثوق"
                else "محظور لتعدد الحسابات الوهمية"
            )
        }
        AlertDialog(
            onDismissRequest = { reviewDeviceTarget = null },
            title = {
                Text(
                    if (decision == "approved_exception") vString(R.string.earn_admin_approve_exception)
                    else vString(R.string.earn_admin_block_device)
                )
            },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Admin Decision Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.adminReviewEarnDevice(dev.deviceFingerprint, decision, note)
                    reviewDeviceTarget = null
                }) {
                    Text(vString(R.string.save_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { reviewDeviceTarget = null }) {
                    Text(vString(R.string.dismiss))
                }
            }
        )
    }
}
