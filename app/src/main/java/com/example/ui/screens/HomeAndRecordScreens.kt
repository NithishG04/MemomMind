package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.model.ExtractedInfo
import com.example.model.MemoTimeFormatter
import com.example.model.RecorderState
import com.example.model.RecorderStatus
import com.example.model.Recording
import com.example.model.ReminderItem
import com.example.model.SuggestedReminderDraft
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PulsingRecordingDot
import com.example.ui.components.RecordingConsentDialog
import com.example.ui.components.TranscriptStatusPill
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.RecordingCrimson
import com.example.ui.theme.RecordingCrimsonSoft
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldSoft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val HOME_RECORD_TAG = "HomeAndRecordScreens"

@Composable
fun HomeDashboardScreen(
    userProfile: UserProfile?,
    recordingsState: UiState<List<Recording>>,
    extractedInfoState: UiState<List<ExtractedInfo>>,
    remindersState: UiState<List<ReminderItem>>,
    pendingReminderDrafts: List<SuggestedReminderDraft>,
    transcribingIds: Set<String>,
    recorderState: RecorderState = RecorderState(),
    onStartRecordingClick: () -> Unit,
    onResumeActiveStudio: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    onOpenRecording: (String) -> Unit,
    onOpenAiMemory: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenVault: () -> Unit,
    onPlayRecording: (Recording) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showConsentDialog by remember { mutableStateOf(false) }
    var permissionDeniedNotice by remember { mutableStateOf(false) }
    var promptedCameraOnce by remember { mutableStateOf(false) }

    val capturePermissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        promptedCameraOnce = true
        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        if (micGranted) {
            permissionDeniedNotice = false
            showConsentDialog = true
        } else {
            permissionDeniedNotice = true
        }
    }

    fun requestPermissionsAndStart() {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val hasNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (hasMic && (hasCamera && hasNotifications || promptedCameraOnce)) {
            permissionDeniedNotice = false
            showConsentDialog = true
        } else {
            permissionsLauncher.launch(capturePermissions)
        }
    }

    if (showConsentDialog) {
        RecordingConsentDialog(
            onConfirmAndStart = {
                showConsentDialog = false
                onStartRecordingClick()
            },
            onDismiss = { showConsentDialog = false }
        )
    }

    val recordings = (recordingsState as? UiState.Success)?.data.orEmpty()
    val extracted = (extractedInfoState as? UiState.Success)?.data.orEmpty()
    val reminders = (remindersState as? UiState.Success)?.data.orEmpty()
    val openReminders = reminders.count { !it.completed }
    val hasActiveFgsSession = recorderState.status == RecorderStatus.RECORDING || recorderState.status == RecorderStatus.PAUSED

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MemoMind",
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text(
                            text = "Hello, ${userProfile?.displayName?.ifBlank { "Friend" } ?: "Friend"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenAiMemory,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp).testTag("home_ask_memory_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ask Memory")
                    }
                }
            }

            if (hasActiveFgsSession) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = RecordingCrimsonSoft),
                        border = BorderStroke(1.dp, RecordingCrimson.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onResumeActiveStudio)
                            .testTag("home_active_fgs_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                PulsingRecordingDot(isActive = recorderState.status == RecorderStatus.RECORDING)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Live Voice Note Active (${MemoTimeFormatter.formatTimestamp(recorderState.elapsedMs)})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = RecordingCrimson
                                    )
                                    Text(
                                        text = "Foreground service running • Tap to return to Voice Studio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelLarge,
                                color = RecordingCrimson
                            )
                        }
                    }
                }
            }

            if (permissionDeniedNotice) {
                item {
                    Surface(
                        color = RecordingCrimsonSoft,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Microphone permission is required for the foreground voice-note service (and Camera enables live visual context snapshots). Please allow access when prompted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RecordingCrimson,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            // Primary Hero Action: Start Recording
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(RecordingCrimson)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CAMERA + AUDIO FOREGROUND READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Capture a Conversation",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Run a foreground Mic + Camera session to record real-time voice notes, snap whiteboards or receipts, and auto-transcribe in English or Tamil.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = {
                                if (hasActiveFgsSession) {
                                    onResumeActiveStudio()
                                } else {
                                    requestPermissionsAndStart()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RecordingCrimson,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_recording_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (hasActiveFgsSession) "Open Live Studio" else "Start Recording",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // Quick Metrics Strip
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricTile(
                        label = "Recordings",
                        value = recordings.size.toString(),
                        icon = Icons.Filled.GraphicEq,
                        onClick = onOpenRecordings,
                        modifier = Modifier.weight(1f).testTag("home_open_recordings_tile")
                    )
                    MetricTile(
                        label = "Extracted",
                        value = extracted.size.toString(),
                        icon = Icons.Filled.Bookmark,
                        onClick = onOpenVault,
                        modifier = Modifier.weight(1f).testTag("home_open_vault_tile")
                    )
                    MetricTile(
                        label = "Reminders",
                        value = openReminders.toString(),
                        icon = Icons.Filled.NotificationsActive,
                        onClick = onOpenReminders,
                        modifier = Modifier.weight(1f).testTag("home_open_reminders_tile")
                    )
                }
            }

            // Pending AI Reminder Confirmation Alert
            if (pendingReminderDrafts.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, RecordingCrimson.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenReminders)
                            .testTag("home_pending_reminders_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = RecordingCrimson
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${pendingReminderDrafts.size} reminder suggestion(s) need confirmation",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Tap to review, edit date/time, and approve before saving.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recent Conversations",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            when (recordingsState) {
                is UiState.Loading -> {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp)
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = recordingsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (recordings.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Mic,
                                title = "No conversations recorded yet",
                                subtitle = "Tap Start Recording above to launch the foreground Mic + Camera studio.",
                                actionLabel = "Start Recording",
                                onAction = { requestPermissionsAndStart() }
                            )
                        }
                    } else {
                        items(recordings.take(6), key = { it.id }) { rec ->
                            RecordingSummaryCard(
                                recording = rec,
                                isTranscribing = transcribingIds.contains(rec.id),
                                onClick = { onOpenRecording(rec.id) },
                                onPlayClick = { onPlayRecording(rec) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RecordingSummaryCard(
    recording: Recording,
    isTranscribing: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("recording_card_${recording.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recording.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${MemoTimeFormatter.formatFullDateTime(recording.recordedAtMs)} • ${MemoTimeFormatter.formatTimestamp(recording.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(48.dp).testTag("play_card_${recording.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play ${recording.title}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (recording.summaryTopics.isNotBlank() && recording.summaryTopics != "None") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recording.summaryTopics,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (recording.fullTranscript.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = recording.fullTranscript,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TranscriptStatusPill(
                    status = if (isTranscribing) "PROCESSING" else recording.transcriptStatus
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = recording.category,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (recording.language.isNotBlank()) {
                    Text(
                        text = recording.language,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecordingScreen(
    recorderState: RecorderState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStopAndSave: (String, String, String) -> Unit,
    onDiscardAndExit: () -> Unit,
    onToggleCameraPreview: (Boolean) -> Unit = {},
    onCreateVisualContextFile: () -> File = { File.createTempFile("ctx", ".jpg") },
    onCameraContextCaptured: (File) -> Unit = {},
    onAddHighlight: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var titleInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Personal") }
    var tagsInput by remember { mutableStateOf("") }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val categories = listOf("Personal", "Work", "Errands", "Finance", "Travel")
    val isRecording = recorderState.status == RecorderStatus.RECORDING
    val isPaused = recorderState.status == RecorderStatus.PAUSED
    val hasActiveSession = isRecording || isPaused

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Voice Studio", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(
                    onClick = onDiscardAndExit,
                    modifier = Modifier.testTag("recording_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .align(Alignment.CenterHorizontally)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Visible Recording & Foreground Service Status Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isRecording) RecordingCrimsonSoft else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        PulsingRecordingDot(isActive = isRecording)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (recorderState.status) {
                                RecorderStatus.RECORDING -> "RECORDING LIVE AUDIO"
                                RecorderStatus.PAUSED -> "RECORDING PAUSED"
                                RecorderStatus.STOPPED -> "AUDIO CAPTURED"
                                RecorderStatus.IDLE -> "READY TO RECORD"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRecording) RecordingCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (recorderState.isForegroundServiceActive) {
                    Surface(
                        color = VerifiedEmeraldSoft,
                        shape = RoundedCornerShape(99.dp),
                        modifier = Modifier.testTag("fgs_status_badge")
                    ) {
                        Text(
                            text = "FGS • ${recorderState.activeFgsTypesLabel.ifBlank { "MIC" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerifiedEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = MemoTimeFormatter.formatTimestamp(recorderState.elapsedMs),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.testTag("recording_timer_text")
            )

            if (!recorderState.statusNotice.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = recorderState.statusNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Live Waveform Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    WaveformVisualizer(
                        peaks = recorderState.liveWaveform,
                        isLiveRecording = isRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (recorderState.activeFileName.isNotBlank()) {
                                "Stream: ${recorderState.activeFileName}"
                            } else {
                                "Foreground microphone • Private local .m4a"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (isRecording) {
                            Text(
                                text = if (recorderState.isSpeechActiveNow) "Voice detected" else "Listening...",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (recorderState.isSpeechActiveNow) RecordingCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CameraX Live Visual Context Card
            CameraContextCaptureCard(
                hasCameraPermission = hasCameraPermission,
                isPreviewEnabled = recorderState.isCameraPreviewEnabled,
                isCapturingSessionActive = hasActiveSession,
                isLiveProcessing = recorderState.isLiveProcessing,
                capturedPath = recorderState.capturedVisualContextPath,
                capturedFrameCount = recorderState.capturedFrameCount,
                onRequestCameraPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onTogglePreview = onToggleCameraPreview,
                onCreateOutputFile = onCreateVisualContextFile,
                onCaptured = onCameraContextCaptured,
                onAddHighlight = onAddHighlight
            )

            // Real-Time Processed Notes Card
            if (recorderState.liveNotesPreview.isNotBlank() || recorderState.isLiveProcessing) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().testTag("live_notes_preview_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = RecordingCrimson,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Real-Time Voice & Visual Notes",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            if (recorderState.isLiveProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        if (recorderState.liveNotesPreview.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = recorderState.liveNotesPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Transport Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!hasActiveSession) {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecordingCrimson,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("studio_start_button")
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Recording", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDiscardAndExit,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("studio_discard_button")
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Discard recording")
                    }

                    Button(
                        onClick = { if (isRecording) onPause() else onResume() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("studio_pause_resume_button")
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRecording) "Pause" else "Resume")
                    }

                    Button(
                        onClick = { onStopAndSave(titleInput, selectedCategory, tagsInput) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("studio_stop_save_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop & Save")
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Optional Conversation Labels
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("Conversation Title (optional — AI suggests one)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("recording_title_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = tagsInput,
                onValueChange = { tagsInput = it },
                label = { Text("Tags (comma-separated, e.g. contractor, budget)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Participant Consent Notice: Foreground notification and live indicator stay visible while Mic + Camera capture is active.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CameraContextCaptureCard(
    hasCameraPermission: Boolean,
    isPreviewEnabled: Boolean,
    isCapturingSessionActive: Boolean,
    isLiveProcessing: Boolean,
    capturedPath: String?,
    capturedFrameCount: Int,
    onRequestCameraPermission: () -> Unit,
    onTogglePreview: (Boolean) -> Unit,
    onCreateOutputFile: () -> File,
    onCaptured: (File) -> Unit,
    onAddHighlight: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var useFrontCamera by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isTakingPhoto by remember { mutableStateOf(false) }

    val thumbnailBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = capturedPath, key2 = capturedFrameCount) {
        value = if (!capturedPath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                val file = File(capturedPath)
                if (file.exists() && file.length() > 0L) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                } else null
            }
        } else null
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Visual Context Camera",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (capturedFrameCount > 0) {
                                "$capturedFrameCount context frame(s) attached"
                            } else {
                                "Snap whiteboards, receipts, or notes live"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (hasCameraPermission) {
                    Switch(
                        checked = isPreviewEnabled,
                        onCheckedChange = onTogglePreview,
                        modifier = Modifier.testTag("toggle_camera_preview_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!hasCameraPermission) {
                OutlinedButton(
                    onClick = onRequestCameraPermission,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("grant_camera_permission_button")
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enable Camera for Visual Voice Notes")
                }
            } else if (isPreviewEnabled) {
                val previewView = remember(context) {
                    PreviewView(context).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                }

                DisposableEffect(lifecycleOwner, useFrontCamera, isPreviewEnabled) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    val executor = ContextCompat.getMainExecutor(context)
                    var boundProvider: ProcessCameraProvider? = null

                    cameraProviderFuture.addListener(
                        {
                            try {
                                val provider = cameraProviderFuture.get()
                                boundProvider = provider
                                val desiredSelector = if (useFrontCamera) {
                                    CameraSelector.DEFAULT_FRONT_CAMERA
                                } else {
                                    CameraSelector.DEFAULT_BACK_CAMERA
                                }
                                val selector = when {
                                    provider.hasCamera(desiredSelector) -> desiredSelector
                                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                    else -> null
                                }

                                if (selector == null) {
                                    cameraError = "No hardware camera detected on this device."
                                    imageCapture = null
                                    return@addListener
                                }

                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()

                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                                imageCapture = capture
                                cameraError = null
                            } catch (e: Exception) {
                                Log.w(HOME_RECORD_TAG, "CameraX bind failed", e)
                                cameraError = e.localizedMessage ?: "Camera preview unavailable."
                                imageCapture = null
                            }
                        },
                        executor
                    )

                    onDispose {
                        try {
                            boundProvider?.unbindAll()
                        } catch (_: Exception) {
                        }
                    }
                }

                if (cameraError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(Icons.Filled.VideocamOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = cameraError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        IconButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.48f))
                                .testTag("flip_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Switch camera lens",
                                tint = Color.White
                            )
                        }
                        thumbnailBitmap?.let { thumb ->
                            Image(
                                bitmap = thumb,
                                contentDescription = "Last captured visual context frame",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                val capture = imageCapture ?: return@Button
                                isTakingPhoto = true
                                val outputFile = onCreateOutputFile()
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                                capture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            isTakingPhoto = false
                                            onCaptured(outputFile)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isTakingPhoto = false
                                            cameraError = exception.localizedMessage ?: "Capture failed."
                                        }
                                    }
                                )
                            },
                            enabled = imageCapture != null && !isTakingPhoto && !isLiveProcessing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("capture_camera_context_button")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isTakingPhoto -> "Snapping..."
                                    isLiveProcessing -> "Analyzing..."
                                    else -> stringResource(R.string.snap_visual_context)
                                },
                                maxLines = 1
                            )
                        }

                        if (isCapturingSessionActive) {
                            OutlinedButton(
                                onClick = onAddHighlight,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("mark_voice_highlight_button")
                            ) {
                                Icon(Icons.Filled.Flag, contentDescription = "Mark voice moment", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mark")
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Camera preview paused — microphone foreground capture remains active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
