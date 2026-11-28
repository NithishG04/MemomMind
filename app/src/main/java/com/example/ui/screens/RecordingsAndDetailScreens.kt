package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ExtractedInfo
import com.example.model.MemoTimeFormatter
import com.example.model.PlaybackState
import com.example.model.Recording
import com.example.model.TranscriptSegment
import com.example.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.TranscriptStatusPill
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.RecordingCrimson

enum class ConversationSortOrder(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LONGEST("Longest")
}

enum class ConversationStatusFilter(val label: String, val rawStatus: String?) {
    ALL("All Status", null),
    TRANSCRIBED("Transcribed", "COMPLETED"),
    PENDING("Pending", "PENDING"),
    PROCESSING("Processing", "PROCESSING"),
    FAILED("Retry Needed", "FAILED")
}

fun filterAndSortRecordings(
    recordings: List<Recording>,
    extractedInfo: List<ExtractedInfo>,
    transcribingIds: Set<String>,
    searchQuery: String,
    selectedCategory: String,
    selectedStatus: ConversationStatusFilter,
    sortOrder: ConversationSortOrder
): List<Recording> {
    val q = searchQuery.trim().lowercase()
    val extractedByRecording = if (q.isEmpty()) emptyMap() else extractedInfo.groupBy { it.recordingId }

    val filtered = recordings.filter { rec ->
        val matchesCategory = selectedCategory == "All" || rec.category.equals(selectedCategory, ignoreCase = true)
        if (!matchesCategory) return@filter false

        val effectiveStatus = if (transcribingIds.contains(rec.id)) "PROCESSING" else rec.transcriptStatus
        val matchesStatus = selectedStatus.rawStatus == null ||
            effectiveStatus.equals(selectedStatus.rawStatus, ignoreCase = true)
        if (!matchesStatus) return@filter false

        if (q.isEmpty()) return@filter true
        val recExtracted = extractedByRecording[rec.id].orEmpty()
        rec.title.lowercase().contains(q) ||
            rec.notes.lowercase().contains(q) ||
            rec.category.lowercase().contains(q) ||
            rec.language.lowercase().contains(q) ||
            rec.fullTranscript.lowercase().contains(q) ||
            rec.summaryTopics.lowercase().contains(q) ||
            rec.summaryDecisions.lowercase().contains(q) ||
            rec.summaryTasks.lowercase().contains(q) ||
            rec.summaryLocations.lowercase().contains(q) ||
            rec.summaryFinancials.lowercase().contains(q) ||
            rec.tags.any { it.lowercase().contains(q) } ||
            recExtracted.any { it.label.lowercase().contains(q) || it.value.lowercase().contains(q) }
    }

    return when (sortOrder) {
        ConversationSortOrder.NEWEST -> filtered.sortedByDescending { it.recordedAtMs }
        ConversationSortOrder.OLDEST -> filtered.sortedBy { it.recordedAtMs }
        ConversationSortOrder.LONGEST -> filtered.sortedByDescending { it.durationMs }
    }
}

@Composable
fun RecordingsLibraryScreen(
    recordingsState: UiState<List<Recording>>,
    extractedInfo: List<ExtractedInfo>,
    transcribingIds: Set<String>,
    onOpenRecording: (String) -> Unit,
    onPlayRecording: (Recording) -> Unit,
    onStartRecordingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SavedConversationsScreen(
        recordingsState = recordingsState,
        extractedInfo = extractedInfo,
        transcribingIds = transcribingIds,
        onOpenRecording = onOpenRecording,
        onPlayRecording = onPlayRecording,
        onStartRecordingClick = onStartRecordingClick,
        modifier = modifier
    )
}

@Composable
fun SavedConversationsScreen(
    recordingsState: UiState<List<Recording>>,
    extractedInfo: List<ExtractedInfo>,
    transcribingIds: Set<String>,
    onOpenRecording: (String) -> Unit,
    onPlayRecording: (Recording) -> Unit,
    onStartRecordingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf(ConversationStatusFilter.ALL) }
    var sortOrder by remember { mutableStateOf(ConversationSortOrder.NEWEST) }
    var showAdvancedFilters by remember { mutableStateOf(false) }

    val allRecordings = (recordingsState as? UiState.Success)?.data.orEmpty()
    val categories = remember(allRecordings) {
        val defaults = listOf("All", "Personal", "Work", "Errands", "Finance", "Travel", "General")
        val extras = allRecordings.map { it.category.trim() }
            .filter { cat -> cat.isNotBlank() && defaults.none { it.equals(cat, ignoreCase = true) } }
            .distinct()
        defaults + extras
    }

    val filteredRecordings = remember(
        allRecordings,
        extractedInfo,
        transcribingIds,
        searchQuery,
        selectedCategory,
        selectedStatus,
        sortOrder
    ) {
        filterAndSortRecordings(
            recordings = allRecordings,
            extractedInfo = extractedInfo,
            transcribingIds = transcribingIds,
            searchQuery = searchQuery,
            selectedCategory = selectedCategory,
            selectedStatus = selectedStatus,
            sortOrder = sortOrder
        )
    }

    val hasActiveFilter = searchQuery.isNotBlank() ||
        selectedCategory != "All" ||
        selectedStatus != ConversationStatusFilter.ALL

    fun clearAllFilters() {
        searchQuery = ""
        selectedCategory = "All"
        selectedStatus = ConversationStatusFilter.ALL
        sortOrder = ConversationSortOrder.NEWEST
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        Text("Saved Conversations", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Browse conversations from your database or search transcripts, places, prices, and tags.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showAdvancedFilters = !showAdvancedFilters },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_conversation_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = "Toggle status and sort filters",
                            tint = if (showAdvancedFilters || selectedStatus != ConversationStatusFilter.ALL) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search titles, transcripts, places, prices, tags...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("clear_search_query_button")
                            ) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("recordings_search_input")
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            modifier = Modifier.testTag("category_chip_${cat.lowercase()}")
                        )
                    }
                }
            }

            if (showAdvancedFilters || selectedStatus != ConversationStatusFilter.ALL) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ConversationStatusFilter.entries) { statusFilter ->
                                FilterChip(
                                    selected = selectedStatus == statusFilter,
                                    onClick = { selectedStatus = statusFilter },
                                    label = { Text(statusFilter.label) },
                                    modifier = Modifier.testTag("status_chip_${statusFilter.name.lowercase()}")
                                )
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Sort:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            items(ConversationSortOrder.entries) { order ->
                                FilterChip(
                                    selected = sortOrder == order,
                                    onClick = { sortOrder = order },
                                    label = { Text(order.label) },
                                    modifier = Modifier.testTag("sort_chip_${order.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            if (recordingsState is UiState.Success && allRecordings.isNotEmpty()) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Showing ${filteredRecordings.size} of ${allRecordings.size} saved conversations",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("saved_conversations_count_label")
                        )
                        if (hasActiveFilter) {
                            TextButton(
                                onClick = { clearAllFilters() },
                                modifier = Modifier.testTag("clear_all_filters_button")
                            ) {
                                Text("Clear filters")
                            }
                        }
                    }
                }
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
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (filteredRecordings.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.GraphicEq,
                                title = if (allRecordings.isEmpty()) "No saved conversations" else "No matching conversations",
                                subtitle = if (allRecordings.isEmpty()) {
                                    "Record your first conversation to store it in the database and search it anytime."
                                } else {
                                    "Try a different keyword or reset your category and status filters."
                                },
                                actionLabel = if (allRecordings.isEmpty()) "Start Recording" else "Reset Filters",
                                onAction = if (allRecordings.isEmpty()) onStartRecordingClick else { { clearAllFilters() } }
                            )
                        }
                    } else {
                        items(filteredRecordings, key = { it.id }) { rec ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailsScreen(
    recording: Recording?,
    segmentsState: UiState<List<TranscriptSegment>>,
    extractedInfo: List<ExtractedInfo>,
    playbackState: PlaybackState,
    isTranscribing: Boolean,
    onBack: () -> Unit,
    onPlayAt: (Recording, Long) -> Unit,
    onTogglePlayPause: (Recording) -> Unit,
    onSeek: (Long) -> Unit,
    onCycleSpeed: () -> Unit,
    onRetryTranscription: (Recording) -> Unit,
    onRegenerateSummary: (Recording) -> Unit,
    onUpdateMetadata: (String, String, String, String, String) -> Unit,
    onUpdateSegment: (String, String, String, String, List<TranscriptSegment>) -> Unit,
    onDeleteRecording: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showEditMetaDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingSegment by remember { mutableStateOf<TranscriptSegment?>(null) }

    if (recording == null) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Recording not found", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }
        return
    }

    val segments = (segmentsState as? UiState.Success)?.data.orEmpty()
    val recExtracted = remember(extractedInfo, recording.id) {
        extractedInfo.filter { it.recordingId == recording.id }
    }
    val isThisPlaying = playbackState.recordingId == recording.id
    val totalDuration = (if (isThisPlaying && playbackState.durationMs > 0) playbackState.durationMs else recording.durationMs).coerceAtLeast(1L)
    val currentPos = if (isThisPlaying) playbackState.currentPositionMs.coerceIn(0L, totalDuration) else 0L

    if (showEditMetaDialog) {
        EditRecordingMetadataDialog(
            recording = recording,
            onSave = { title, notes, category, tagsCsv ->
                onUpdateMetadata(recording.id, title, notes, category, tagsCsv)
                showEditMetaDialog = false
            },
            onDismiss = { showEditMetaDialog = false }
        )
    }

    editingSegment?.let { seg ->
        EditTranscriptSegmentDialog(
            segment = seg,
            onSave = { newSpeaker, newText ->
                onUpdateSegment(recording.id, seg.id, newText, newSpeaker, segments)
                editingSegment = null
            },
            onDismiss = { editingSegment = null }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Recording Permanently?") },
            text = { Text("This deletes the audio file, transcript segments, and linked extracted details.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteRecording(recording.id)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Memory Details", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("details_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        val exportText = buildString {
                            appendLine(recording.title)
                            appendLine(MemoTimeFormatter.formatFullDateTime(recording.recordedAtMs))
                            appendLine()
                            if (recording.summaryTopics.isNotBlank() && recording.summaryTopics != "None") {
                                appendLine("Topics: ${recording.summaryTopics}")
                            }
                            if (recording.fullTranscript.isNotBlank()) {
                                appendLine("Transcript:\n${recording.fullTranscript}")
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, recording.title)
                            putExtra(Intent.EXTRA_TEXT, exportText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Transcript"))
                    },
                    modifier = Modifier.testTag("details_export_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Export transcript")
                }
                IconButton(
                    onClick = { showEditMetaDialog = true },
                    modifier = Modifier.testTag("details_edit_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit recording metadata")
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.testTag("details_delete_button")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete recording", tint = RecordingCrimson)
                }
            }
        )

        Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
            ) {
                // Header & Audio Player Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TranscriptStatusPill(
                                    status = if (isTranscribing) "PROCESSING" else recording.transcriptStatus
                                )
                                Text(
                                    text = "${recording.category} • ${recording.language}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(recording.title, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = MemoTimeFormatter.formatFullDateTime(recording.recordedAtMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            WaveformVisualizer(
                                peaks = recording.waveformPeaks,
                                progressFraction = (currentPos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f),
                                isLiveRecording = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                            )
                            Slider(
                                value = currentPos.toFloat(),
                                onValueChange = { pos ->
                                    if (isThisPlaying) {
                                        onSeek(pos.toLong())
                                    } else {
                                        onPlayAt(recording, pos.toLong())
                                    }
                                },
                                valueRange = 0f..totalDuration.toFloat()
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "${MemoTimeFormatter.formatTimestamp(currentPos)} / ${MemoTimeFormatter.formatTimestamp(totalDuration)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = onCycleSpeed) {
                                        Text("${playbackState.playbackSpeed}x", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = { onTogglePlayPause(recording) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(48.dp).testTag("details_play_pause_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isThisPlaying && playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isThisPlaying && playbackState.isPlaying) "Pause" else "Play")
                                    }
                                }
                            }

                            if (!playbackState.errorMessage.isNullOrBlank() && isThisPlaying) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = playbackState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RecordingCrimson
                                )
                            }
                        }
                    }
                }

                // Retry / Transcribe Action if needed
                if (recording.transcriptStatus == "FAILED" || recording.transcriptStatus == "PENDING") {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isTranscribing) "Transcribing with Gemini..." else "Transcription ready to run",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Generate multilingual timestamps, summary, and extracted facts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isTranscribing) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Button(
                                        onClick = { onRetryTranscription(recording) },
                                        modifier = Modifier.testTag("retry_transcription_button")
                                    ) {
                                        Text("Transcribe")
                                    }
                                }
                            }
                        }
                    }
                }

                // Notes & Tags
                if (recording.notes.isNotBlank() || recording.tags.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (recording.notes.isNotBlank()) {
                                    Text("Notes", style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(recording.notes, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (recording.tags.isNotEmpty()) {
                                    if (recording.notes.isNotBlank()) Spacer(modifier = Modifier.height(10.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(recording.tags) { tag ->
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "#$tag",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // AI Structured Summary Card
                if (recording.summaryTopics.isNotBlank() && recording.summaryTopics != "None") {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("AI Conversation Summary", style = MaterialTheme.typography.titleMedium)
                                    IconButton(
                                        onClick = { onRegenerateSummary(recording) },
                                        enabled = !isTranscribing
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Regenerate summary")
                                    }
                                }
                                SummaryLine("Topics", recording.summaryTopics)
                                SummaryLine("Decisions", recording.summaryDecisions)
                                SummaryLine("Tasks", recording.summaryTasks)
                                SummaryLine("Locations", recording.summaryLocations)
                                SummaryLine("Financials", recording.summaryFinancials)
                                SummaryLine("Dates & Times", recording.summaryDates)
                            }
                        }
                    }
                }

                // Extracted Details Linked to This Recording
                if (recExtracted.isNotEmpty()) {
                    item {
                        Text("Extracted Information (${recExtracted.size})", style = MaterialTheme.typography.titleLarge)
                    }
                    items(recExtracted, key = { it.id }) { info ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayAt(recording, info.timestampMs) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth().padding(14.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${info.category} • ${info.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(info.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "▶ ${MemoTimeFormatter.formatTimestamp(info.timestampMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Timestamped Transcript Segments (Tap any timestamp to play from that second!)
                item {
                    Text("Timestamped Transcript", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Tap any timestamp segment to jump directly to that moment in the audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (segments.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = recording.fullTranscript.ifBlank {
                                    if (isTranscribing) "Generating timestamped transcript..." else "No transcript segments available yet."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(segments, key = { it.id }) { seg ->
                        val isActiveSegment = isThisPlaying &&
                            currentPos >= seg.startMs &&
                            currentPos <= seg.endMs.coerceAtLeast(seg.startMs + 1000L)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActiveSegment) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isActiveSegment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayAt(recording, seg.startMs) }
                                .testTag("segment_card_${seg.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = MemoTimeFormatter.formatTimestamp(seg.startMs),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = seg.speaker,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = { editingSegment = seg },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit transcript segment",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(seg.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    if (value.isBlank() || value.equals("None", ignoreCase = true)) return
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EditRecordingMetadataDialog(
    recording: Recording,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(recording.title) }
    var notes by remember { mutableStateOf(recording.notes) }
    var category by remember { mutableStateOf(recording.category) }
    var tagsCsv by remember { mutableStateOf(recording.tags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Recording Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                OutlinedTextField(value = tagsCsv, onValueChange = { tagsCsv = it }, label = { Text("Tags (comma-separated)") })
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, notes, category, tagsCsv) }, enabled = title.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditTranscriptSegmentDialog(
    segment: TranscriptSegment,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var speaker by remember { mutableStateOf(segment.speaker) }
    var text by remember { mutableStateOf(segment.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transcript Segment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = speaker, onValueChange = { speaker = it }, label = { Text("Speaker") })
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Transcript text") },
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(speaker, text) }, enabled = text.isNotBlank()) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
