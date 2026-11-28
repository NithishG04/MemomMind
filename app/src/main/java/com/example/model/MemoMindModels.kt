package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val consentAccepted: Boolean = false,
    val aiProcessingConsent: Boolean = false,
    val defaultLanguage: String = "Auto",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Recording(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val category: String = "General",
    val tags: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val recordedAtMs: Long = 0L,
    val language: String = "Auto",
    val transcriptStatus: String = "PENDING",
    val fullTranscript: String = "",
    val summaryTopics: String = "",
    val summaryDecisions: String = "",
    val summaryTasks: String = "",
    val summaryLocations: String = "",
    val summaryFinancials: String = "",
    val summaryDates: String = "",
    val localAudioFileName: String = "",
    val waveformPeaks: List<Int> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TranscriptSegment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recordingId: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val text: String = "",
    val speaker: String = "Speaker 1",
    val confidence: Double = 0.9,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExtractedInfo(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recordingId: String = "",
    val recordingTitle: String = "",
    val segmentId: String = "",
    val timestampMs: Long = 0L,
    val category: String = "OTHER",
    val label: String = "",
    val value: String = "",
    val verified: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReminderItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recordingId: String = "",
    val recordingTitle: String = "",
    val timestampMs: Long = 0L,
    val title: String = "",
    val dueDateTimeText: String = "",
    val dueTimestampMs: Long = 0L,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class MemoryMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = "USER",
    val text: String = "",
    val citedRecordingId: String = "",
    val citedRecordingTitle: String = "",
    val citedDateText: String = "",
    val citedTimestampMs: Long = 0L,
    val citedExcerpt: String = "",
    val evidenceConfidence: String = "NONE",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class SuggestedReminderDraft(
    val id: String,
    val recordingId: String,
    val recordingTitle: String,
    val timestampMs: Long,
    val title: String,
    val dueDateTimeText: String
)

enum class RecorderStatus {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

data class RecorderState(
    val status: RecorderStatus = RecorderStatus.IDLE,
    val elapsedMs: Long = 0L,
    val currentAmplitude: Int = 0,
    val liveWaveform: List<Int> = emptyList(),
    val activeFileName: String = "",
    val statusNotice: String? = null,
    val isForegroundServiceActive: Boolean = false,
    val activeFgsTypesLabel: String = "",
    val isCameraPreviewEnabled: Boolean = true,
    val capturedVisualContextPath: String? = null,
    val capturedFrameCount: Int = 0,
    val isLiveProcessing: Boolean = false,
    val liveNotesPreview: String = "",
    val isSpeechActiveNow: Boolean = false
)

data class PlaybackState(
    val recordingId: String? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null
)

object MemoTimeFormatter {
    fun formatTimestamp(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFullDateTime(epochMs: Long): String {
        val safeMs = if (epochMs > 0L) epochMs else System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault())
        return sdf.format(Date(safeMs))
    }

    fun formatShortDate(epochMs: Long): String {
        val safeMs = if (epochMs > 0L) epochMs else System.currentTimeMillis()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(safeMs))
    }
}
