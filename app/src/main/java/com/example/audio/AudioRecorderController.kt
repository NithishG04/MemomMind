package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.model.MemoTimeFormatter
import com.example.model.RecorderState
import com.example.model.RecorderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.min

private const val TAG = "AudioRecorderController"

data class CompletedRecordingAudio(
    val recordingId: String,
    val file: File,
    val durationMs: Long,
    val waveformPeaks: List<Int>,
    val visualContextFile: File? = null,
    val liveNotes: String = ""
)

class AudioRecorderController(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _pendingNotificationSave = MutableStateFlow<CompletedRecordingAudio?>(null)
    val pendingNotificationSave: StateFlow<CompletedRecordingAudio?> = _pendingNotificationSave.asStateFlow()

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var visualContextFile: File? = null
    private var activeRecordingId: String? = null
    private var tickerJob: Job? = null
    private var activeSegmentStartMs: Long = 0L
    private var accumulatedDurationMs: Long = 0L
    private val rawSamples = mutableListOf<Int>()
    private val liveNotesBuilder = mutableListOf<String>()
    private var focusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (_state.value.status == RecorderStatus.RECORDING) {
                    pauseRecording(notice = "Recording paused due to an incoming call or audio interruption.")
                }
            }
        }
    }

    fun getRecordingsDirectory(): File {
        val dir = File(appContext.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getRecordingFile(fileName: String): File {
        return File(getRecordingsDirectory(), fileName)
    }

    fun createVisualContextFile(): File {
        val id = activeRecordingId ?: "ctx_${System.currentTimeMillis()}"
        val file = File(getRecordingsDirectory(), "${id}_context.jpg")
        visualContextFile = file
        return file
    }

    fun setForegroundServiceState(active: Boolean, typesLabel: String = "") {
        _state.value = _state.value.copy(
            isForegroundServiceActive = active,
            activeFgsTypesLabel = if (active) typesLabel else ""
        )
    }

    fun setCameraPreviewEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isCameraPreviewEnabled = enabled)
    }

    fun setLiveProcessing(processing: Boolean) {
        _state.value = _state.value.copy(isLiveProcessing = processing)
    }

    fun setStatusNotice(notice: String?) {
        _state.value = _state.value.copy(statusNotice = notice)
    }

    fun recordCapturedVisualFrame(file: File) {
        visualContextFile = file
        _state.value = _state.value.copy(
            capturedVisualContextPath = file.absolutePath,
            capturedFrameCount = _state.value.capturedFrameCount + 1
        )
    }

    fun appendLiveNote(entry: String) {
        val clean = entry.trim()
        if (clean.isBlank()) return
        val timestamp = MemoTimeFormatter.formatTimestamp(_state.value.elapsedMs)
        val formatted = "[$timestamp] $clean"
        synchronized(liveNotesBuilder) {
            liveNotesBuilder.add(formatted)
            _state.value = _state.value.copy(
                liveNotesPreview = liveNotesBuilder.joinToString("\n")
            )
        }
    }

    fun markVoiceHighlight(label: String = "Voice highlight marked") {
        if (_state.value.status != RecorderStatus.RECORDING && _state.value.status != RecorderStatus.PAUSED) return
        appendLiveNote(label)
    }

    fun startRecording(): Result<String> {
        val current = _state.value.status
        if (current == RecorderStatus.RECORDING || current == RecorderStatus.PAUSED) {
            return Result.success(_state.value.activeFileName)
        }
        return try {
            stopAndDiscardInternal()
            val recordingId = "rec_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val fileName = "$recordingId.m4a"
            val file = File(getRecordingsDirectory(), fileName)

            requestAudioFocus()

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            outputFile = file
            activeRecordingId = recordingId
            rawSamples.clear()
            synchronized(liveNotesBuilder) { liveNotesBuilder.clear() }
            accumulatedDurationMs = 0L
            activeSegmentStartMs = System.currentTimeMillis()

            _state.value = RecorderState(
                status = RecorderStatus.RECORDING,
                elapsedMs = 0L,
                currentAmplitude = 0,
                liveWaveform = emptyList(),
                activeFileName = fileName,
                statusNotice = null,
                isForegroundServiceActive = _state.value.isForegroundServiceActive,
                activeFgsTypesLabel = _state.value.activeFgsTypesLabel,
                isCameraPreviewEnabled = _state.value.isCameraPreviewEnabled
            )

            startMeterTicker()
            Result.success(fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start microphone recording", e)
            stopAndDiscardInternal()
            _state.value = RecorderState(
                status = RecorderStatus.IDLE,
                statusNotice = e.localizedMessage ?: "Unable to access microphone."
            )
            Result.failure(e)
        }
    }

    fun pauseRecording(notice: String? = null) {
        if (_state.value.status != RecorderStatus.RECORDING) return
        try {
            mediaRecorder?.pause()
            accumulatedDurationMs += (System.currentTimeMillis() - activeSegmentStartMs).coerceAtLeast(0L)
            tickerJob?.cancel()
            tickerJob = null
            _state.value = _state.value.copy(
                status = RecorderStatus.PAUSED,
                elapsedMs = accumulatedDurationMs,
                currentAmplitude = 0,
                isSpeechActiveNow = false,
                statusNotice = notice
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause recording", e)
            _state.value = _state.value.copy(statusNotice = "Could not pause recording: ${e.localizedMessage}")
        }
    }

    fun resumeRecording() {
        if (_state.value.status != RecorderStatus.PAUSED) return
        try {
            requestAudioFocus()
            mediaRecorder?.resume()
            activeSegmentStartMs = System.currentTimeMillis()
            _state.value = _state.value.copy(
                status = RecorderStatus.RECORDING,
                statusNotice = null
            )
            startMeterTicker()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume recording", e)
            _state.value = _state.value.copy(statusNotice = "Could not resume recording: ${e.localizedMessage}")
        }
    }

    fun stopAndFinalize(): Result<CompletedRecordingAudio> {
        val currentStatus = _state.value.status
        if (currentStatus != RecorderStatus.RECORDING && currentStatus != RecorderStatus.PAUSED) {
            return Result.failure(IllegalStateException("No active recording to stop."))
        }
        return try {
            tickerJob?.cancel()
            tickerJob = null
            if (currentStatus == RecorderStatus.RECORDING) {
                accumulatedDurationMs += (System.currentTimeMillis() - activeSegmentStartMs).coerceAtLeast(0L)
            }
            val finalDuration = accumulatedDurationMs.coerceAtLeast(500L)
            val recorder = mediaRecorder
            val file = outputFile
            val recId = activeRecordingId ?: "rec_${System.currentTimeMillis()}"
            val capturedImage = visualContextFile?.takeIf { it.exists() && it.length() > 0L }
            val notesSnapshot = synchronized(liveNotesBuilder) { liveNotesBuilder.joinToString("\n") }

            recorder?.apply {
                try {
                    stop()
                } catch (e: RuntimeException) {
                    Log.w(TAG, "MediaRecorder stop warning (very short audio): ${e.message}")
                }
                reset()
                release()
            }
            mediaRecorder = null
            abandonAudioFocus()

            if (file == null || !file.exists() || file.length() == 0L) {
                return Result.failure(IllegalStateException("Recorded audio file is empty."))
            }

            val downsampledPeaks = downsampleWaveform(rawSamples)
            _state.value = RecorderState(
                status = RecorderStatus.STOPPED,
                elapsedMs = finalDuration,
                currentAmplitude = 0,
                liveWaveform = downsampledPeaks,
                activeFileName = file.name,
                statusNotice = null,
                isForegroundServiceActive = false,
                activeFgsTypesLabel = "",
                isCameraPreviewEnabled = _state.value.isCameraPreviewEnabled,
                capturedVisualContextPath = capturedImage?.absolutePath,
                capturedFrameCount = _state.value.capturedFrameCount,
                liveNotesPreview = notesSnapshot
            )
            outputFile = null
            visualContextFile = null
            activeRecordingId = null

            Result.success(
                CompletedRecordingAudio(
                    recordingId = recId,
                    file = file,
                    durationMs = finalDuration,
                    waveformPeaks = downsampledPeaks,
                    visualContextFile = capturedImage,
                    liveNotes = notesSnapshot
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize recording", e)
            stopAndDiscardInternal()
            Result.failure(e)
        }
    }

    fun publishNotificationSave(completed: CompletedRecordingAudio) {
        _pendingNotificationSave.value = completed
    }

    fun consumePendingNotificationSave(): CompletedRecordingAudio? {
        val current = _pendingNotificationSave.value
        _pendingNotificationSave.value = null
        return current
    }

    fun discardActiveRecording() {
        stopAndDiscardInternal()
        _pendingNotificationSave.value = null
        _state.value = RecorderState(isCameraPreviewEnabled = _state.value.isCameraPreviewEnabled)
    }

    fun resetToIdle() {
        if (_state.value.status == RecorderStatus.STOPPED) {
            _state.value = RecorderState(isCameraPreviewEnabled = _state.value.isCameraPreviewEnabled)
        }
    }

    private fun stopAndDiscardInternal() {
        tickerJob?.cancel()
        tickerJob = null
        mediaRecorder?.let { recorder ->
            try {
                recorder.stop()
            } catch (_: Exception) {
            }
            try {
                recorder.reset()
                recorder.release()
            } catch (_: Exception) {
            }
        }
        mediaRecorder = null
        outputFile?.let { file ->
            if (file.exists()) file.delete()
        }
        visualContextFile?.let { file ->
            if (file.exists()) file.delete()
        }
        outputFile = null
        visualContextFile = null
        activeRecordingId = null
        rawSamples.clear()
        synchronized(liveNotesBuilder) { liveNotesBuilder.clear() }
        accumulatedDurationMs = 0L
        abandonAudioFocus()
    }

    private fun startMeterTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _state.value.status == RecorderStatus.RECORDING) {
                val amp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }
                val normalized = ((amp / 32767.0) * 100.0).toInt().coerceIn(4, 100)
                rawSamples.add(normalized)
                val liveSlice = rawSamples.takeLast(48)
                val totalElapsed = accumulatedDurationMs + (System.currentTimeMillis() - activeSegmentStartMs).coerceAtLeast(0L)
                _state.value = _state.value.copy(
                    elapsedMs = totalElapsed,
                    currentAmplitude = normalized,
                    liveWaveform = liveSlice,
                    isSpeechActiveNow = normalized >= 18
                )
                delay(70L)
            }
        }
    }

    private fun downsampleWaveform(samples: List<Int>, targetBars: Int = 56): List<Int> {
        if (samples.isEmpty()) return List(24) { 12 }
        if (samples.size <= targetBars) return samples.take(79)
        val bucketSize = samples.size.toDouble() / targetBars
        return (0 until targetBars).map { idx ->
            val start = (idx * bucketSize).toInt()
            val end = min(samples.size, ((idx + 1) * bucketSize).toInt().coerceAtLeast(start + 1))
            val slice = samples.subList(start, end)
            (slice.maxOrNull() ?: 12).coerceIn(6, 100)
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusChangeListener)
        }
    }

    fun release() {
        stopAndDiscardInternal()
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioRecorderController? = null

        fun getInstance(context: Context): AudioRecorderController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioRecorderController(context).also { INSTANCE = it }
            }
        }
    }
}
