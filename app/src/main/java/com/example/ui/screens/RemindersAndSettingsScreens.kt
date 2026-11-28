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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.model.ExtractedInfo
import com.example.model.MemoTimeFormatter
import com.example.model.ReminderItem
import com.example.model.SuggestedReminderDraft
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.components.ConfirmReminderDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.RecordingCrimson
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldSoft
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberSoft

@Composable
fun RemindersScreen(
    remindersState: UiState<List<ReminderItem>>,
    pendingDrafts: List<SuggestedReminderDraft>,
    onConfirmDraft: (SuggestedReminderDraft, String, String) -> Unit,
    onDismissDraft: (String) -> Unit,
    onCreateManualReminder: (String, String) -> Unit,
    onToggleCompleted: (ReminderItem) -> Unit,
    onEditReminder: (ReminderItem, String, String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onPlaySourceAudio: (String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var draftToConfirm by remember { mutableStateOf<SuggestedReminderDraft?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<ReminderItem?>(null) }

    draftToConfirm?.let { draft ->
        ConfirmReminderDialog(
            draft = draft,
            onConfirm = { title, dueText ->
                onConfirmDraft(draft, title, dueText)
                draftToConfirm = null
            },
            onDismiss = { draftToConfirm = null }
        )
    }

    if (showCreateDialog) {
        ManualReminderDialog(
            initialTitle = "",
            initialDue = "Tomorrow 10:00 AM",
            dialogTitle = "New Reminder",
            onSave = { title, due ->
                onCreateManualReminder(title, due)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingReminder?.let { rem ->
        ManualReminderDialog(
            initialTitle = rem.title,
            initialDue = rem.dueDateTimeText,
            dialogTitle = "Edit Reminder",
            onSave = { title, due ->
                onEditReminder(rem, title, due)
                editingReminder = null
            },
            onDismiss = { editingReminder = null }
        )
    }

    val reminders = (remindersState as? UiState.Success)?.data.orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .fillMaxSize()
        ) {
            item {
                Text("Reminders", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AI-detected tasks always ask for your confirmation before being saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (pendingDrafts.isNotEmpty()) {
                item {
                    Text(
                        text = "Awaiting Your Confirmation (${pendingDrafts.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = RecordingCrimson
                    )
                }
                items(pendingDrafts, key = { it.id }) { draft ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, RecordingCrimson.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(draft.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Suggested: ${draft.dueDateTimeText} • From ${draft.recordingTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { draftToConfirm = draft },
                                    modifier = Modifier.testTag("review_confirm_draft_${draft.id}")
                                ) {
                                    Text("Review & Confirm")
                                }
                                OutlinedButton(onClick = { onDismissDraft(draft.id) }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }

            when (remindersState) {
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
                                text = remindersState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (reminders.isEmpty() && pendingDrafts.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.NotificationsActive,
                                title = "No reminders yet",
                                subtitle = "When conversations mention tasks or appointments, MemoMind proposes them here for your confirmation.",
                                actionLabel = "Add Reminder",
                                onAction = { showCreateDialog = true }
                            )
                        }
                    } else {
                        items(reminders, key = { it.id }) { rem ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Checkbox(
                                        checked = rem.completed,
                                        onCheckedChange = { onToggleCompleted(rem) },
                                        modifier = Modifier.testTag("reminder_check_${rem.id}")
                                    )
                                    Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                        Text(
                                            text = rem.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            textDecoration = if (rem.completed) TextDecoration.LineThrough else null
                                        )
                                        Text(
                                            text = rem.dueDateTimeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (rem.recordingId.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "▶ ${rem.recordingTitle} (${MemoTimeFormatter.formatTimestamp(rem.timestampMs)})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    onPlaySourceAudio(rem.recordingId, rem.timestampMs)
                                                }
                                            )
                                        }
                                    }
                                    IconButton(onClick = { editingReminder = rem }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit reminder")
                                    }
                                    IconButton(onClick = { onDeleteReminder(rem.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete reminder")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp)
                .testTag("add_reminder_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create manual reminder")
        }
    }
}

@Composable
private fun ManualReminderDialog(
    initialTitle: String,
    initialDue: String,
    dialogTitle: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var due by remember { mutableStateOf(initialDue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Reminder") })
                OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("Date & Time") })
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title, due) }, enabled = title.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractedInfoVaultScreen(
    extractedInfoState: UiState<List<ExtractedInfo>>,
    onBack: () -> Unit,
    onPlayAt: (String, Long) -> Unit,
    onUpdateInfo: (String, String, String, Boolean) -> Unit,
    onDeleteInfo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var editingInfo by remember { mutableStateOf<ExtractedInfo?>(null) }

    val categories = listOf("All", "LOCATION", "DIRECTION", "PRICE", "BUDGET", "DATE", "TASK", "DECISION", "NAME", "PHONE")
    val allItems = (extractedInfoState as? UiState.Success)?.data.orEmpty()
    val filtered = remember(allItems, selectedCategory) {
        if (selectedCategory == "All") allItems else allItems.filter { it.category == selectedCategory }
    }

    editingInfo?.let { info ->
        var label by remember(info.id) { mutableStateOf(info.label) }
        var value by remember(info.id) { mutableStateOf(info.value) }
        AlertDialog(
            onDismissRequest = { editingInfo = null },
            title = { Text("Edit Extracted Detail") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") })
                    OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Value") })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateInfo(info.id, label, value, true)
                        editingInfo = null
                    },
                    enabled = label.isNotBlank() && value.isNotBlank()
                ) {
                    Text("Verify & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingInfo = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Extracted Information", style = MaterialTheme.typography.titleLarge) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .fillMaxSize()
        ) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Filled.Bookmark,
                        title = "No extracted items in this category",
                        subtitle = "Names, directions, budgets, dates, and decisions from transcribed recordings appear here."
                    )
                }
            } else {
                items(filtered, key = { it.id }) { info ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = if (info.verified) VerifiedEmeraldSoft else WarningAmberSoft,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (info.verified) "VERIFIED • ${info.category}" else "UNVERIFIED • ${info.category}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (info.verified) VerifiedEmerald else WarningAmber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        onUpdateInfo(info.id, info.label, info.value, !info.verified)
                                    }) {
                                        Icon(
                                            imageVector = if (info.verified) Icons.Filled.Verified else Icons.Filled.CheckCircle,
                                            contentDescription = "Toggle verification",
                                            tint = if (info.verified) VerifiedEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { editingInfo = info }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit detail")
                                    }
                                    IconButton(onClick = { onDeleteInfo(info.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete detail")
                                    }
                                }
                            }
                            Text(info.label, style = MaterialTheme.typography.titleMedium)
                            Text(info.value, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onPlayAt(info.recordingId, info.timestampMs) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${info.recordingTitle} • ${MemoTimeFormatter.formatTimestamp(info.timestampMs)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndPrivacyScreen(
    userProfile: UserProfile?,
    userEmail: String,
    onUpdateSettings: (displayName: String?, aiConsent: Boolean?, defaultLanguage: String?) -> Unit,
    onOpenVault: () -> Unit,
    onBuildExportPayload: () -> String,
    onSignOut: () -> Unit,
    onDeleteAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var editingName by remember(userProfile?.displayName) { mutableStateOf(userProfile?.displayName ?: "") }

    val languages = listOf("Auto", "English", "Tamil", "Tanglish")

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete All Personal Memory Data?") },
            text = {
                Text("This permanently deletes all your cloud recordings, transcripts, extracted facts, AI chat messages, reminders, and local audio files on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        onDeleteAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RecordingCrimson, contentColor = Color.White)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }

    Box(contentAlignment = Alignment.TopCenter, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
        ) {
            item {
                Text("Settings & Privacy", style = MaterialTheme.typography.headlineLarge)
            }

            // Profile Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Account Profile", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = userEmail.ifBlank { userProfile?.email ?: "Signed in with Google" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editingName,
                                onValueChange = { editingName = it },
                                label = { Text("Display Name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { onUpdateSettings(editingName, null, null) },
                                enabled = editingName.isNotBlank() && editingName != userProfile?.displayName,
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // Language & AI Consent
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Transcription Language", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(languages) { lang ->
                                FilterChip(
                                    selected = (userProfile?.defaultLanguage ?: "Auto") == lang,
                                    onClick = { onUpdateSettings(null, null, lang) },
                                    label = { Text(lang) }
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("AI Transcription & Memory Processing", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "When enabled, recorded audio is transcribed and summarized with Gemini AI Logic.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = userProfile?.aiProcessingConsent ?: true,
                                onCheckedChange = { checked -> onUpdateSettings(null, checked, null) }
                            )
                        }
                    }
                }
            }

            // Data Management Actions
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Memory Vault & Data Control", style = MaterialTheme.typography.titleMedium)

                        OutlinedButton(
                            onClick = onOpenVault,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_open_vault_button")
                        ) {
                            Icon(Icons.Filled.Bookmark, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Extracted Information Vault")
                        }

                        OutlinedButton(
                            onClick = {
                                val text = onBuildExportPayload()
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "MemoMind Memory Export")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export MemoMind Data"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_export_data_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export All Transcripts & Notes")
                        }

                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out")
                        }

                        Button(
                            onClick = { showDeleteAccountDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = RecordingCrimson
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_delete_account_data_button")
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete All Personal Data")
                        }
                    }
                }
            }
        }
    }
}
