package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.MemoTimeFormatter
import com.example.model.PlaybackState
import com.example.model.SuggestedReminderDraft
import com.example.ui.theme.RecordingCrimson
import com.example.ui.theme.RecordingCrimsonSoft
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft

@Composable
fun WaveformVisualizer(
    peaks: List<Int>,
    progressFraction: Float = 0f,
    isLiveRecording: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeColor = if (isLiveRecording) RecordingCrimson else MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val bars = remember(peaks) {
        if (peaks.isEmpty()) List(36) { 14 } else peaks
    }

    Canvas(modifier = modifier) {
        val count = bars.size.coerceAtLeast(1)
        val totalWidth = size.width
        val slotWidth = totalWidth / count
        val barWidth = (slotWidth * 0.56f).coerceIn(3f, 10f)
        val centerY = size.height / 2f

        bars.forEachIndexed { index, rawPeak ->
            val fraction = (index.toFloat() / count.toFloat())
            val barColor = if (isLiveRecording || fraction <= progressFraction) activeColor else inactiveColor
            val normHeight = (rawPeak.coerceIn(6, 100) / 100f) * (size.height * 0.88f)
            val barH = normHeight.coerceAtLeast(8f)
            val left = index * slotWidth + (slotWidth - barWidth) / 2f
            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun PulsingRecordingDot(isActive: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rec_pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (isActive) RecordingCrimson.copy(alpha = alpha) else MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun TranscriptStatusPill(status: String, modifier: Modifier = Modifier) {
    val (bg, fg, label) = when (status.uppercase()) {
        "COMPLETED" -> Triple(VerifiedEmeraldSoft, VerifiedEmerald, "Transcribed")
        "PROCESSING" -> Triple(WarningAmberSoft, WarningAmber, "Transcribing...")
        "FAILED" -> Triple(RecordingCrimsonSoft, RecordingCrimson, "Retry Needed")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Saved")
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MiniAudioPlayerBar(
    playbackState: PlaybackState,
    recordingTitle: String,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onStop: () -> Unit,
    onOpenRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (playbackState.recordingId == null) return
    val duration = playbackState.durationMs.coerceAtLeast(1L)
    val position = playbackState.currentPositionMs.coerceIn(0L, duration)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenRecording)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("mini_player_play_pause")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause playback" else "Resume playback",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = recordingTitle.ifBlank { "Conversation Audio" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${MemoTimeFormatter.formatTimestamp(position)} / ${MemoTimeFormatter.formatTimestamp(duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onCycleSpeed,
                    modifier = Modifier.testTag("mini_player_speed")
                ) {
                    Text(
                        text = "${playbackState.playbackSpeed}x",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onStop,
                    modifier = Modifier.size(48.dp).testTag("mini_player_close")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Stop and close player"
                    )
                }
            }
            Slider(
                value = position.toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun RecordingConsentDialog(
    onConfirmAndStart: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Consented Conversation Recording",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Please inform conversation participants before recording. MemoMind always displays a visible red indicator and an active foreground notification—never covert recording.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Audio and optional CameraX snapshots are saved locally to private app storage, and you can pause, stop, or permanently delete any recording at any time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmAndStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RecordingCrimson,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("consent_confirm_start_button")
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("I Have Consent • Start")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmReminderDialog(
    draft: SuggestedReminderDraft,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(draft.id) { mutableStateOf(draft.title) }
    var dueText by remember(draft.id) { mutableStateOf(draft.dueDateTimeText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Extracted Reminder", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "From \"${draft.recordingTitle}\" at ${MemoTimeFormatter.formatTimestamp(draft.timestampMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder task") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueText,
                    onValueChange = { dueText = it },
                    label = { Text("Date & time") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(title, dueText) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_reminder_save_button")
            ) {
                Text("Confirm Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
