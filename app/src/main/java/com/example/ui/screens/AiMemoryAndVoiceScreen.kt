package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audio.VoiceAssistantHelper
import com.example.model.MemoTimeFormatter
import com.example.model.MemoryMessage
import com.example.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.RecordingCrimson
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.VerifiedEmeraldSoft

@Composable
fun AiMemoryChatScreen(
    messagesState: UiState<List<MemoryMessage>>,
    isAskingMemory: Boolean,
    onAskQuestion: (String, ((String) -> Unit)?) -> Unit,
    onPlayCitedAudio: (recordingId: String, timestampMs: Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceHelper = remember(context) { VoiceAssistantHelper(context) }
    val voiceState by voiceHelper.state.collectAsStateWithLifecycle()
    var speakAnswersAloud by remember { mutableStateOf(true) }
    var questionInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(voiceHelper) {
        onDispose { voiceHelper.release() }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceHelper.startListening { spoken ->
                onAskQuestion(spoken) { answerText ->
                    if (speakAnswersAloud) voiceHelper.speak(answerText)
                }
            }
        }
    }

    fun triggerVoiceAsk() {
        if (voiceState.isListening) {
            voiceHelper.stopListening()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            voiceHelper.startListening { spoken ->
                onAskQuestion(spoken) { answerText ->
                    if (speakAnswersAloud) voiceHelper.speak(answerText)
                }
            }
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val samplePrompts = listOf(
        "What budget did someone tell me?",
        "Which road or directions were mentioned?",
        "What tasks did I agree to complete?",
        "What did we decide in the last conversation?",
        "Did anyone mention an address or phone number?"
    )

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Memory", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        text = "Ask by text or voice. Every factual answer cites the exact recording timestamp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(
                        onClick = {
                            speakAnswersAloud = !speakAnswersAloud
                            if (!speakAnswersAloud) voiceHelper.stopSpeaking()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (speakAnswersAloud) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (speakAnswersAloud) "Mute voice answers" else "Enable spoken answers"
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearHistory,
                            modifier = Modifier.size(48.dp).testTag("clear_memory_chat_button")
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear chat history")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Voice Conversation Status Bar
            if (voiceState.isListening || voiceState.partialTranscript.isNotBlank() || !voiceState.errorMessage.isNullOrBlank()) {
                Surface(
                    color = if (voiceState.isListening) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = when {
                                voiceState.isListening && voiceState.partialTranscript.isNotBlank() ->
                                    "Listening: \"${voiceState.partialTranscript}\""
                                voiceState.isListening -> "Listening... Speak your memory question now"
                                else -> voiceState.errorMessage.orEmpty()
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (voiceState.isListening) {
                            OutlinedButton(onClick = { voiceHelper.stopListening() }) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(samplePrompts) { prompt ->
                    SuggestionChip(
                        onClick = { onAskQuestion(prompt, null) },
                        label = { Text(prompt, maxLines = 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (messagesState) {
                    is UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    is UiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = messagesState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    is UiState.Success -> {
                        if (messages.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Filled.Psychology,
                                title = "Ask MemoMind Anything You Forgot",
                                subtitle = "Ask a question about past conversations or tap the Voice Ask mic button. MemoMind only answers from your saved recordings and lets you play the original audio.",
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    MemoryMessageBubble(
                                        message = msg,
                                        onPlayOriginalAudio = {
                                            if (msg.citedRecordingId.isNotBlank()) {
                                                onPlayCitedAudio(msg.citedRecordingId, msg.citedTimestampMs)
                                            }
                                        }
                                    )
                                }
                                if (isAskingMemory) {
                                    item {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Searching saved conversations...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input + Voice Ask Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { triggerVoiceAsk() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (voiceState.isListening) RecordingCrimson else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (voiceState.isListening) Color.White else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("ai_memory_voice_button")
                ) {
                    Icon(
                        imageVector = if (voiceState.isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (voiceState.isListening) "Stop listening" else "Ask by voice"
                    )
                }

                OutlinedTextField(
                    value = questionInput,
                    onValueChange = { questionInput = it },
                    placeholder = { Text("Ask about a past conversation...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_memory_input")
                )

                Button(
                    onClick = {
                        val q = questionInput.trim()
                        if (q.isNotBlank()) {
                            questionInput = ""
                            onAskQuestion(q) { answerText ->
                                if (speakAnswersAloud) voiceHelper.speak(answerText)
                            }
                        }
                    },
                    enabled = questionInput.isNotBlank() && !isAskingMemory,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("ai_memory_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send memory question")
                }
            }
        }
    }
}

@Composable
private fun MemoryMessageBubble(
    message: MemoryMessage,
    onPlayOriginalAudio: () -> Unit
) {
    val isUser = message.role == "USER"
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
            ),
            border = if (isUser) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )

                if (!isUser && message.citedRecordingId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "SOURCE EVIDENCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = VerifiedEmeraldSoft,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${message.evidenceConfidence} CONFIDENCE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VerifiedEmerald,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = message.citedRecordingTitle.ifBlank { "Saved Conversation" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${message.citedDateText} • Timestamp ${MemoTimeFormatter.formatTimestamp(message.citedTimestampMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (message.citedExcerpt.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "\"${message.citedExcerpt}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onPlayOriginalAudio,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("play_cited_audio_${message.id}")
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play Original Audio (${MemoTimeFormatter.formatTimestamp(message.citedTimestampMs)})")
                            }
                        }
                    }
                }
            }
        }
    }
}
