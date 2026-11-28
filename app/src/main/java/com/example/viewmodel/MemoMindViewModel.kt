package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.MemoMindAiRepository
import com.example.audio.AudioPlayerController
import com.example.audio.AudioRecorderController
import com.example.audio.CompletedRecordingAudio
import com.example.data.MemoMindRepository
import com.example.model.ExtractedInfo
import com.example.model.MemoTimeFormatter
import com.example.model.MemoryMessage
import com.example.model.PlaybackState
import com.example.model.RecorderState
import com.example.model.Recording
import com.example.model.ReminderItem
import com.example.model.SuggestedReminderDraft
import com.example.model.TranscriptSegment
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.service.VoiceNoteForegroundService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "MemoMindVM"
private const val STOP_TIMEOUT_MILLIS = 5000L

class MemoMindViewModel(
    appContext: Context,
    private val repository: MemoMindRepository,
    private val aiRepository: MemoMindAiRepository,
    private val recorderController: AudioRecorderController,
    private val playerController: AudioPlayerController,
    val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String
) : ViewModel() {

    private val appContext = appContext.applicationContext

    val recorderState: StateFlow<RecorderState> = recorderController.state
    val playbackState: StateFlow<PlaybackState> = playerController.state

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        repository.observeUserProfile(currentUserId)
            .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "Failed to load profile") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val recordingsState: StateFlow<UiState<List<Recording>>> =
        repository.observeRecordings(currentUserId)
            .map<List<Recording>, UiState<List<Recording>>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "Failed to load recordings") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val extractedInfoState: StateFlow<UiState<List<ExtractedInfo>>> =
        repository.observeExtractedInfo(currentUserId)
            .map<List<ExtractedInfo>, UiState<List<ExtractedInfo>>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "Failed to load extracted details") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val remindersState: StateFlow<UiState<List<ReminderItem>>> =
        repository.observeReminders(currentUserId)
            .map<List<ReminderItem>, UiState<List<ReminderItem>>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "Failed to load reminders") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val memoryMessagesState: StateFlow<UiState<List<MemoryMessage>>> =
        repository.observeMemoryMessages(currentUserId)
            .map<List<MemoryMessage>, UiState<List<MemoryMessage>>> { UiState.Success(it) }
            .catch { error -> emitStreamError(error, "Failed to load memory chat") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val activeRecordingIdForSegments = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeSegmentsState: StateFlow<UiState<List<TranscriptSegment>>> =
        activeRecordingIdForSegments.flatMapLatest { recId ->
            if (recId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeSegments(currentUserId, recId)
                    .map<List<TranscriptSegment>, UiState<List<TranscriptSegment>>> { UiState.Success(it) }
                    .catch { error -> emitStreamError(error, "Failed to load transcript segments") }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    private val _pendingReminderDrafts = MutableStateFlow<List<SuggestedReminderDraft>>(emptyList())
    val pendingReminderDrafts: StateFlow<List<SuggestedReminderDraft>> = _pendingReminderDrafts.asStateFlow()

    private val _transcribingIds = MutableStateFlow<Set<String>>(emptySet())
    val transcribingIds: StateFlow<Set<String>> = _transcribingIds.asStateFlow()

    private val _isAskingMemory = MutableStateFlow(false)
    val isAskingMemory: StateFlow<Boolean> = _isAskingMemory.asStateFlow()

    private val _feedbackBanner = MutableStateFlow<String?>(null)
    val feedbackBanner: StateFlow<String?> = _feedbackBanner.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName, initialEmail)
        }
        viewModelScope.launch {
            recorderController.pendingNotificationSave.collect { pending ->
                if (pending != null) {
                    val consumed = recorderController.consumePendingNotificationSave()
                    if (consumed != null) {
                        persistCompletedRecording(
                            completed = consumed,
                            customTitle = "",
                            category = "Personal",
                            tagsCsv = "",
                            onSaved = {}
                        )
                    }
                }
            }
        }
    }

    fun selectRecordingForDetails(recordingId: String?) {
        activeRecordingIdForSegments.value = recordingId
    }

    fun clearFeedbackBanner() {
        _feedbackBanner.value = null
    }

    fun acceptOnboardingConsent() {
        viewModelScope.launch {
            val res = repository.updateUserSettings(consentAccepted = true)
            if (res.isFailure) {
                _feedbackBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save consent."
            }
        }
    }

    fun updateSettings(
        displayName: String? = null,
        aiProcessingConsent: Boolean? = null,
        defaultLanguage: String? = null
    ) {
        viewModelScope.launch {
            val res = repository.updateUserSettings(
                displayName = displayName,
                aiProcessingConsent = aiProcessingConsent,
                defaultLanguage = defaultLanguage
            )
            _feedbackBanner.value = if (res.isSuccess) {
                "Settings updated."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update settings."
            }
        }
    }

    fun startRecording(): Boolean {
        playerController.stop()
        val result = recorderController.startRecording()
        if (result.isFailure) {
            _feedbackBanner.value = result.exceptionOrNull()?.localizedMessage
                ?: "Microphone recording could not start."
            return false
        }
        VoiceNoteForegroundService.start(appContext)
        return true
    }

    fun pauseRecording() = recorderController.pauseRecording()

    fun resumeRecording() = recorderController.resumeRecording()

    fun toggleCameraPreview(enabled: Boolean) {
        recorderController.setCameraPreviewEnabled(enabled)
    }

    fun createVisualContextFile(): File = recorderController.createVisualContextFile()

    fun onCameraContextCaptured(imageFile: File) {
        recorderController.recordCapturedVisualFrame(imageFile)
        val allowAi = (userProfileState.value as? UiState.Success)?.data?.aiProcessingConsent ?: true
        if (!allowAi) {
            recorderController.appendLiveNote("Visual frame captured (${imageFile.name}).")
            return
        }
        viewModelScope.launch {
            recorderController.setLiveProcessing(true)
            val result = aiRepository.analyzeVisualContext(imageFile)
            recorderController.setLiveProcessing(false)
            if (result.isSuccess) {
                val visualNote = result.getOrThrow()
                recorderController.appendLiveNote("Camera note: $visualNote")
                _feedbackBanner.value = "Visual context processed and added to live notes."
            } else {
                recorderController.appendLiveNote("Visual frame captured (offline).")
                _feedbackBanner.value = result.exceptionOrNull()?.localizedMessage
                    ?: "Visual frame saved; live AI caption unavailable right now."
            }
        }
    }

    fun addVoiceHighlightNote(customNote: String = "Key moment highlighted") {
        recorderController.markVoiceHighlight(customNote)
    }

    fun discardRecording() {
        recorderController.discardActiveRecording()
        VoiceNoteForegroundService.stop(appContext)
        _feedbackBanner.value = "Recording discarded."
    }

    fun stopAndSaveRecording(
        customTitle: String,
        category: String,
        tagsCsv: String,
        onSaved: (String) -> Unit = {}
    ) {
        val result = recorderController.stopAndFinalize()
        VoiceNoteForegroundService.stop(appContext)
        if (result.isFailure) {
            _feedbackBanner.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to save recording."
            return
        }
        val completed = result.getOrThrow()
        persistCompletedRecording(completed, customTitle, category, tagsCsv, onSaved)
    }

    private fun persistCompletedRecording(
        completed: CompletedRecordingAudio,
        customTitle: String,
        category: String,
        tagsCsv: String,
        onSaved: (String) -> Unit
    ) {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val languageHint = profile?.defaultLanguage ?: "Auto"
        val allowAi = profile?.aiProcessingConsent ?: true
        val nowMs = System.currentTimeMillis()
        val titleToSave = customTitle.trim().ifBlank {
            "Conversation • ${MemoTimeFormatter.formatShortDate(nowMs)}"
        }
        val tags = tagsCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)

        viewModelScope.launch {
            val saveRes = repository.createRecording(
                recordingId = completed.recordingId,
                title = titleToSave,
                category = category,
                tags = tags,
                durationMs = completed.durationMs,
                recordedAtMs = nowMs,
                language = languageHint,
                localAudioFile = completed.file,
                waveformPeaks = completed.waveformPeaks
            )
            if (saveRes.isFailure) {
                _feedbackBanner.value = saveRes.exceptionOrNull()?.localizedMessage
                    ?: "Saved locally, but cloud sync failed."
                return@launch
            }

            val savedId = saveRes.getOrThrow()
            if (completed.liveNotes.isNotBlank()) {
                repository.updateRecordingMetadata(
                    recordingId = savedId,
                    title = titleToSave,
                    notes = completed.liveNotes,
                    category = category,
                    tags = tags
                )
            }
            recorderController.resetToIdle()
            onSaved(savedId)

            if (allowAi) {
                runTranscriptionPipeline(
                    recordingId = savedId,
                    audioFile = completed.file,
                    durationMs = completed.durationMs,
                    languageHint = languageHint,
                    keepCustomTitle = customTitle.isNotBlank(),
                    existingTitle = titleToSave,
                    visualContextFile = completed.visualContextFile,
                    liveNotes = completed.liveNotes
                )
            } else {
                _feedbackBanner.value = "Audio saved locally. Enable AI processing in Settings to transcribe."
            }
        }
    }

    fun retryTranscription(recording: Recording) {
        val file = recorderController.getRecordingFile(recording.localAudioFileName)
        if (!file.exists()) {
            _feedbackBanner.value = "Local audio file is unavailable on this device."
            return
        }
        val profile = (userProfileState.value as? UiState.Success)?.data
        val languageHint = profile?.defaultLanguage ?: recording.language.ifBlank { "Auto" }
        val contextImg = recorderController.getRecordingFile("${recording.id}_context.jpg")
            .takeIf { it.exists() }
        viewModelScope.launch {
            runTranscriptionPipeline(
                recordingId = recording.id,
                audioFile = file,
                durationMs = recording.durationMs,
                languageHint = languageHint,
                keepCustomTitle = true,
                existingTitle = recording.title,
                visualContextFile = contextImg,
                liveNotes = recording.notes
            )
        }
    }

    private suspend fun runTranscriptionPipeline(
        recordingId: String,
        audioFile: File,
        durationMs: Long,
        languageHint: String,
        keepCustomTitle: Boolean,
        existingTitle: String,
        visualContextFile: File? = null,
        liveNotes: String = ""
    ) {
        _transcribingIds.value = _transcribingIds.value + recordingId
        repository.updateRecordingTranscriptionStatus(recordingId, "PROCESSING")

        val aiRes = aiRepository.transcribeAndAnalyzeAudio(
            recordingId = recordingId,
            userId = currentUserId,
            audioFile = audioFile,
            durationMs = durationMs,
            languageHint = languageHint,
            visualContextFile = visualContextFile,
            liveNotes = liveNotes
        )

        if (aiRes.isSuccess) {
            val analysis = aiRes.getOrThrow()
            val finalTitle = if (keepCustomTitle) existingTitle else analysis.suggestedTitle
            val saveRes = repository.saveRecordingAnalysis(
                recordingId = recordingId,
                title = finalTitle,
                language = analysis.detectedLanguage,
                fullTranscript = analysis.fullTranscript,
                summaryTopics = analysis.summaryTopics,
                summaryDecisions = analysis.summaryDecisions,
                summaryTasks = analysis.summaryTasks,
                summaryLocations = analysis.summaryLocations,
                summaryFinancials = analysis.summaryFinancials,
                summaryDates = analysis.summaryDates,
                segments = analysis.segments,
                extractedItems = analysis.extractedItems
            )
            if (analysis.suggestedReminders.isNotEmpty()) {
                val updatedDrafts = analysis.suggestedReminders.map {
                    it.copy(recordingTitle = finalTitle)
                }
                _pendingReminderDrafts.value = (_pendingReminderDrafts.value + updatedDrafts).take(20)
            }
            _feedbackBanner.value = if (saveRes.isSuccess) {
                "Transcript and memory extraction complete."
            } else {
                saveRes.exceptionOrNull()?.localizedMessage ?: "Failed to store transcript."
            }
        } else {
            repository.updateRecordingTranscriptionStatus(recordingId, "FAILED")
            _feedbackBanner.value = aiRes.exceptionOrNull()?.localizedMessage
                ?: "Transcription failed. Tap Retry in recording details."
        }

        _transcribingIds.value = _transcribingIds.value - recordingId
    }

    fun playRecordingAt(recording: Recording, startMs: Long = 0L) {
        val file = recorderController.getRecordingFile(recording.localAudioFileName)
        playerController.playFromTimestamp(
            recordingId = recording.id,
            audioFile = file,
            startPositionMs = startMs,
            knownDurationMs = recording.durationMs
        )
    }

    fun playRecordingByIdAt(recordingId: String, startMs: Long) {
        val recordings = (recordingsState.value as? UiState.Success)?.data.orEmpty()
        val match = recordings.firstOrNull { it.id == recordingId }
        if (match == null) {
            _feedbackBanner.value = "Referenced recording could not be found."
            return
        }
        playRecordingAt(match, startMs)
    }

    fun togglePlayPause(recording: Recording) {
        val file = recorderController.getRecordingFile(recording.localAudioFileName)
        playerController.togglePlayPause(recording.id, file, recording.durationMs)
    }

    fun seekAudio(positionMs: Long) = playerController.seekTo(positionMs)

    fun cyclePlaybackSpeed() = playerController.cycleSpeed()

    fun stopPlayback() = playerController.stop()

    fun updateRecordingMetadata(
        recordingId: String,
        title: String,
        notes: String,
        category: String,
        tagsCsv: String
    ) {
        val tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
        viewModelScope.launch {
            val res = repository.updateRecordingMetadata(recordingId, title, notes, category, tags)
            _feedbackBanner.value = if (res.isSuccess) "Recording updated." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun regenerateSummary(recording: Recording) {
        if (recording.fullTranscript.isBlank()) {
            _feedbackBanner.value = "Transcribe the recording first to generate a summary."
            return
        }
        viewModelScope.launch {
            _transcribingIds.value = _transcribingIds.value + recording.id
            val res = aiRepository.reSummarizeTranscript(recording.title, recording.fullTranscript)
            if (res.isSuccess) {
                val s = res.getOrThrow()
                repository.updateRecordingSummary(
                    recordingId = recording.id,
                    summaryTopics = s.topics,
                    summaryDecisions = s.decisions,
                    summaryTasks = s.tasks,
                    summaryLocations = s.locations,
                    summaryFinancials = s.financials,
                    summaryDates = s.dates
                )
                _feedbackBanner.value = "Summary refreshed."
            } else {
                _feedbackBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to refresh summary."
            }
            _transcribingIds.value = _transcribingIds.value - recording.id
        }
    }

    fun updateSegmentText(
        recordingId: String,
        segmentId: String,
        newText: String,
        newSpeaker: String,
        allSegments: List<TranscriptSegment>
    ) {
        val rebuilt = allSegments.joinToString("\n") { seg ->
            val t = if (seg.id == segmentId) newText.trim() else seg.text
            val sp = if (seg.id == segmentId) newSpeaker.trim() else seg.speaker
            "$sp: $t"
        }
        viewModelScope.launch {
            val res = repository.updateSegmentText(recordingId, segmentId, newText, newSpeaker, rebuilt)
            _feedbackBanner.value = if (res.isSuccess) "Transcript segment updated." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun deleteRecording(recordingId: String, onDeleted: () -> Unit = {}) {
        if (playbackState.value.recordingId == recordingId) {
            playerController.stop()
        }
        viewModelScope.launch {
            val res = repository.deleteRecording(recordingId, recorderController.getRecordingsDirectory())
            if (res.isSuccess) {
                _feedbackBanner.value = "Recording permanently deleted."
                onDeleted()
            } else {
                _feedbackBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to delete recording."
            }
        }
    }

    fun askMemoryQuestion(question: String, onAnswerTextReady: ((String) -> Unit)? = null) {
        val trimmed = question.trim()
        if (trimmed.isBlank() || _isAskingMemory.value) return

        viewModelScope.launch {
            _isAskingMemory.value = true
            try {
                repository.addMemoryMessage(
                    MemoryMessage(
                        userId = currentUserId,
                        role = "USER",
                        text = trimmed
                    )
                )

                val recordings = (recordingsState.value as? UiState.Success)?.data.orEmpty()
                val extracted = (extractedInfoState.value as? UiState.Success)?.data.orEmpty()
                val history = (memoryMessagesState.value as? UiState.Success)?.data.orEmpty()
                val segMap = repository.getAllSegmentsForUser(currentUserId, recordings)

                val answerRes = aiRepository.askMemory(
                    question = trimmed,
                    recordings = recordings,
                    segmentsByRecording = segMap,
                    extractedInfo = extracted,
                    recentHistory = history
                )

                if (answerRes.isSuccess) {
                    val ans = answerRes.getOrThrow()
                    repository.addMemoryMessage(
                        MemoryMessage(
                            userId = currentUserId,
                            role = "ASSISTANT",
                            text = ans.answer,
                            citedRecordingId = if (ans.foundInMemories) ans.citedRecordingId else "",
                            citedRecordingTitle = if (ans.foundInMemories) ans.citedRecordingTitle else "",
                            citedDateText = if (ans.foundInMemories) ans.citedDateText else "",
                            citedTimestampMs = if (ans.foundInMemories) ans.citedTimestampMs else 0L,
                            citedExcerpt = if (ans.foundInMemories) ans.citedExcerpt else "",
                            evidenceConfidence = ans.evidenceConfidence
                        )
                    )
                    onAnswerTextReady?.invoke(ans.answer)
                } else {
                    val err = answerRes.exceptionOrNull()?.localizedMessage
                        ?: "Unable to query saved memories right now."
                    repository.addMemoryMessage(
                        MemoryMessage(
                            userId = currentUserId,
                            role = "ASSISTANT",
                            text = "Error retrieving memory: $err",
                            evidenceConfidence = "NONE"
                        )
                    )
                }
            } finally {
                _isAskingMemory.value = false
            }
        }
    }

    fun clearMemoryChat() {
        viewModelScope.launch {
            val res = repository.clearMemoryChatHistory()
            _feedbackBanner.value = if (res.isSuccess) "Memory chat cleared." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun updateExtractedInfo(infoId: String, label: String, value: String, verified: Boolean) {
        viewModelScope.launch {
            val res = repository.updateExtractedInfo(infoId, label, value, verified)
            _feedbackBanner.value = if (res.isSuccess) "Detail updated." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun deleteExtractedInfo(infoId: String) {
        viewModelScope.launch {
            val res = repository.deleteExtractedInfo(infoId)
            _feedbackBanner.value = if (res.isSuccess) "Detail removed." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun confirmReminderDraft(
        draft: SuggestedReminderDraft,
        editedTitle: String,
        editedDueText: String
    ) {
        viewModelScope.launch {
            val res = repository.createConfirmedReminder(
                title = editedTitle.ifBlank { draft.title },
                dueDateTimeText = editedDueText.ifBlank { draft.dueDateTimeText },
                dueTimestampMs = System.currentTimeMillis() + 3_600_000L,
                recordingId = draft.recordingId,
                recordingTitle = draft.recordingTitle,
                timestampMs = draft.timestampMs
            )
            if (res.isSuccess) {
                _pendingReminderDrafts.value = _pendingReminderDrafts.value.filterNot { it.id == draft.id }
                _feedbackBanner.value = "Reminder confirmed and saved."
            } else {
                _feedbackBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Could not confirm reminder."
            }
        }
    }

    fun dismissReminderDraft(draftId: String) {
        _pendingReminderDrafts.value = _pendingReminderDrafts.value.filterNot { it.id == draftId }
    }

    fun createManualReminder(title: String, dueDateTimeText: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val res = repository.createConfirmedReminder(
                title = title,
                dueDateTimeText = dueDateTimeText.ifBlank { "Upcoming" },
                dueTimestampMs = System.currentTimeMillis() + 3_600_000L
            )
            _feedbackBanner.value = if (res.isSuccess) "Reminder created." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun toggleReminderCompleted(reminder: ReminderItem) {
        viewModelScope.launch {
            repository.updateReminder(
                reminderId = reminder.id,
                title = reminder.title,
                dueDateTimeText = reminder.dueDateTimeText,
                dueTimestampMs = reminder.dueTimestampMs,
                completed = !reminder.completed
            )
        }
    }

    fun editReminder(reminder: ReminderItem, newTitle: String, newDueText: String) {
        viewModelScope.launch {
            val res = repository.updateReminder(
                reminderId = reminder.id,
                title = newTitle,
                dueDateTimeText = newDueText,
                dueTimestampMs = reminder.dueTimestampMs,
                completed = reminder.completed
            )
            _feedbackBanner.value = if (res.isSuccess) "Reminder updated." else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            repository.deleteReminder(reminderId)
        }
    }

    fun buildExportPayload(): String {
        val recordings = (recordingsState.value as? UiState.Success)?.data.orEmpty()
        val extracted = (extractedInfoState.value as? UiState.Success)?.data.orEmpty()
        val reminders = (remindersState.value as? UiState.Success)?.data.orEmpty()
        return buildString {
            appendLine("MemoMind Personal Memory Export")
            appendLine("Exported: ${MemoTimeFormatter.formatFullDateTime(System.currentTimeMillis())}")
            appendLine("==================================================")
            appendLine()
            appendLine("RECORDINGS (${recordings.size})")
            recordings.forEach { rec ->
                appendLine("- ${rec.title} [${MemoTimeFormatter.formatFullDateTime(rec.recordedAtMs)}] (${MemoTimeFormatter.formatTimestamp(rec.durationMs)}, ${rec.language})")
                if (rec.summaryTopics.isNotBlank() && rec.summaryTopics != "None") appendLine("  Topics: ${rec.summaryTopics}")
                if (rec.summaryDecisions.isNotBlank() && rec.summaryDecisions != "None") appendLine("  Decisions: ${rec.summaryDecisions}")
                if (rec.summaryTasks.isNotBlank() && rec.summaryTasks != "None") appendLine("  Tasks: ${rec.summaryTasks}")
                if (rec.fullTranscript.isNotBlank()) appendLine("  Transcript:\n  ${rec.fullTranscript}")
                appendLine()
            }
            appendLine("EXTRACTED DETAILS (${extracted.size})")
            extracted.forEach { info ->
                appendLine("- [${info.category}] ${info.label}: ${info.value} (Verified: ${info.verified}, Source: ${info.recordingTitle} @ ${MemoTimeFormatter.formatTimestamp(info.timestampMs)})")
            }
            appendLine()
            appendLine("REMINDERS (${reminders.size})")
            reminders.forEach { rem ->
                appendLine("- [${if (rem.completed) "Done" else "Open"}] ${rem.title} (${rem.dueDateTimeText})")
            }
        }
    }

    fun deleteAllUserData(onComplete: () -> Unit) {
        playerController.stop()
        recorderController.discardActiveRecording()
        VoiceNoteForegroundService.stop(appContext)
        viewModelScope.launch {
            val res = repository.deleteAllUserData(recorderController.getRecordingsDirectory())
            if (res.isSuccess) {
                onComplete()
            } else {
                _feedbackBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Account data deletion failed."
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerController.stop()
    }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<UiState<T>>.emitStreamError(
        error: Throwable,
        fallbackMessage: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, fallbackMessage, rootError)
        emit(UiState.Error(rootError.localizedMessage ?: fallbackMessage))
    }
}
