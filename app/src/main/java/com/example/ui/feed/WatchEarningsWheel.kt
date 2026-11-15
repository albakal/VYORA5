package com.example.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.EmeraldLive
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PulseMagenta
import com.example.ui.theme.vString

data class WatchWheelUiState(
    val enabled: Boolean = true,
    val elapsedMillis: Long = 0L,
    val cycleSec: Long = 30L,
    val pointsPerCycle: Long = 5L,
    val todayCycles: Long = 0L,
    val dailyCapCycles: Long = 60L,
    val isActivelyPlaying: Boolean = false,
    val isClaiming: Boolean = false,
    val lastAwardedPoints: Long? = null,
    val statusNotice: String = ""
) {
    val cycleMillis: Long get() = cycleSec.coerceIn(10L, 180L) * 1000L
    val dailyCapReached: Boolean get() = todayCycles >= dailyCapCycles.coerceAtLeast(1L)
    val progressFraction: Float
        get() = WatchWheelLogic.computeProgress(elapsedMillis, cycleSec, dailyCapReached)
    val remainingSec: Long
        get() = WatchWheelLogic.computeRemainingSeconds(elapsedMillis, cycleSec)
}

object WatchWheelLogic {
    data class TickOutcome(
        val nextElapsedMillis: Long,
        val cycleCompleted: Boolean,
        val completedWatchSeconds: Long
    )

    fun advanceTick(
        currentElapsedMillis: Long,
        deltaMillis: Long,
        cycleSec: Long,
        isEnabled: Boolean,
        isScreenActive: Boolean,
        isVideoPlaying: Boolean,
        isClaiming: Boolean,
        isDailyCapReached: Boolean
    ): TickOutcome {
        val safeCycleSec = cycleSec.coerceIn(10L, 180L)
        val cycleMillis = safeCycleSec * 1000L
        val safeElapsed = currentElapsedMillis.coerceIn(0L, cycleMillis)
        if (!isEnabled || !isScreenActive || !isVideoPlaying || isClaiming || isDailyCapReached || deltaMillis <= 0L) {
            return TickOutcome(
                nextElapsedMillis = safeElapsed,
                cycleCompleted = false,
                completedWatchSeconds = 0L
            )
        }
        val updated = safeElapsed + deltaMillis
        return if (updated >= cycleMillis) {
            TickOutcome(
                nextElapsedMillis = 0L,
                cycleCompleted = true,
                completedWatchSeconds = safeCycleSec
            )
        } else {
            TickOutcome(
                nextElapsedMillis = updated,
                cycleCompleted = false,
                completedWatchSeconds = 0L
            )
        }
    }

    fun computeProgress(elapsedMillis: Long, cycleSec: Long, dailyCapReached: Boolean): Float {
        if (dailyCapReached) return 1f
        val cycleMillis = (cycleSec.coerceIn(10L, 180L) * 1000L).toFloat()
        return (elapsedMillis.toFloat() / cycleMillis).coerceIn(0f, 1f)
    }

    fun computeRemainingSeconds(elapsedMillis: Long, cycleSec: Long): Long {
        val cycleMillis = cycleSec.coerceIn(10L, 180L) * 1000L
        val remainingMs = (cycleMillis - elapsedMillis).coerceAtLeast(0L)
        return ((remainingMs + 999L) / 1000L).coerceIn(0L, cycleSec.coerceIn(10L, 180L))
    }
}

@Composable
fun WatchEarningsWheelBadge(
    state: WatchWheelUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progressFraction,
        animationSpec = tween(durationMillis = 280, easing = LinearEasing),
        label = "watch_wheel_progress"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "coin_spin")
    val coinGlowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coin_glow_rotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // Floating reward celebration pill (+5 pts)
        AnimatedVisibility(
            visible = state.lastAwardedPoints != null,
            enter = fadeIn() + scaleIn(spring(dampingRatio = 0.55f)) + slideInVertically { it / 2 },
            exit = fadeOut() + scaleOut() + slideOutVertically { -it / 2 }
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EmeraldLive,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "+${state.lastAwardedPoints ?: state.pointsPerCycle}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
            }
        }

        // Circular Animated Watch Wheel
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(62.dp)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(Color(0xB80E101C), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .clickable(onClick = onClick)
                .testTag("watch_earnings_wheel")
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val strokePx = 4.5.dp.toPx()
                val inset = strokePx / 2f
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val topLeft = Offset(inset, inset)

                // Track background circle
                drawArc(
                    color = Color.White.copy(alpha = 0.18f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Active gradient sweep
                val sweep = (animatedProgress * 360f).coerceIn(0f, 360f)
                if (sweep > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(AmberGold, NeonCyan, PulseMagenta, AmberGold)
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }

            // Inner Coin Core
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (!state.isActivelyPlaying && !state.dailyCapReached) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.MonetizationOn
                    },
                    contentDescription = vString(R.string.watch_wheel_title),
                    tint = if (state.dailyCapReached) EmeraldLive else AmberGold,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(if (state.isActivelyPlaying && !state.dailyCapReached) coinGlowRotation else 0f)
                )
                Text(
                    text = when {
                        state.dailyCapReached -> vString(R.string.watch_wheel_max_short)
                        state.isClaiming -> "…"
                        else -> "${state.remainingSec}s"
                    },
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Compact badge below the wheel showing cycles or reward per cycle
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xCC111322),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = "+${state.pointsPerCycle} • ${state.todayCycles}/${state.dailyCapCycles}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = AmberGold,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun WatchEarningsWheelDialog(
    state: WatchWheelUiState,
    walletPoints: Long,
    onDismiss: () -> Unit,
    onOpenWallet: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = AmberGold.copy(alpha = 0.16f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MonetizationOn,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = vString(R.string.watch_wheel_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = vString(R.string.watch_wheel_desc, state.pointsPerCycle, state.cycleSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = vString(R.string.watch_wheel_today_cycles),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${state.todayCycles} / ${state.dailyCapCycles}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }
                        LinearProgressIndicator(
                            progress = {
                                (state.todayCycles.toFloat() / state.dailyCapCycles.coerceAtLeast(1L).toFloat())
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AmberGold,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = vString(R.string.points_balance),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$walletPoints",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold
                            )
                        }
                    }
                }

                if (state.statusNotice.isNotBlank()) {
                    Text(
                        text = state.statusNotice,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.dailyCapReached) EmeraldLive else PulseMagenta
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenWallet()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(vString(R.string.wallet_earnings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(vString(R.string.dismiss))
            }
        }
    )
}
