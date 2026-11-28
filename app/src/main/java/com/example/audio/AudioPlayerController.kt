package com.example.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import com.example.model.PlaybackState
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

private const val TAG = "AudioPlayerController"

class AudioPlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var currentFilePath: String? = null

    fun playFromTimestamp(
        recordingId: String,
        audioFile: File,
        startPositionMs: Long = 0L,
        knownDurationMs: Long = 0L
    ) {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            _state.value = PlaybackState(
                recordingId = recordingId,
                isPlaying = false,
                errorMessage = "Original audio file is not stored on this device."
            )
            return
        }

        val current = _state.value
        if (current.recordingId == recordingId && mediaPlayer != null && currentFilePath == audioFile.absolutePath) {
            seekAndResume(startPositionMs)
            return
        }

        stop()
        try {
            val player = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                val actualDuration = duration.toLong().coerceAtLeast(knownDurationMs).coerceAtLeast(1L)
                val targetMs = startPositionMs.coerceIn(0L, actualDuration)
                if (targetMs > 0L) {
                    seekTo(targetMs.toInt())
                }
                val speed = _state.value.playbackSpeed
                if (speed != 1.0f) {
                    playbackParams = PlaybackParams().setSpeed(speed)
                }
                setOnCompletionListener {
                    progressJob?.cancel()
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        currentPositionMs = 0L
                    )
                }
                start()
            }

            mediaPlayer = player
            currentFilePath = audioFile.absolutePath
            val actualDuration = player.duration.toLong().coerceAtLeast(knownDurationMs)
            _state.value = _state.value.copy(
                recordingId = recordingId,
                isPlaying = true,
                currentPositionMs = startPositionMs.coerceIn(0L, actualDuration),
                durationMs = actualDuration,
                errorMessage = null
            )
            startProgressTicker()
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback failed", e)
            stop()
            _state.value = PlaybackState(
                recordingId = recordingId,
                isPlaying = false,
                errorMessage = e.localizedMessage ?: "Unable to play audio."
            )
        }
    }

    fun togglePlayPause(recordingId: String, audioFile: File, knownDurationMs: Long = 0L) {
        val current = _state.value
        if (current.recordingId == recordingId && mediaPlayer != null) {
            if (current.isPlaying) {
                pause()
            } else {
                resume()
            }
        } else {
            playFromTimestamp(recordingId, audioFile, 0L, knownDurationMs)
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        try {
            val maxDuration = player.duration.toLong().coerceAtLeast(0L)
            val clamped = positionMs.coerceIn(0L, maxDuration)
            player.seekTo(clamped.toInt())
            _state.value = _state.value.copy(currentPositionMs = clamped)
        } catch (e: Exception) {
            Log.e(TAG, "Seek failed", e)
        }
    }

    private fun seekAndResume(positionMs: Long) {
        val player = mediaPlayer ?: return
        try {
            val maxDuration = player.duration.toLong().coerceAtLeast(0L)
            val clamped = positionMs.coerceIn(0L, maxDuration)
            player.seekTo(clamped.toInt())
            if (!player.isPlaying) {
                player.start()
            }
            _state.value = _state.value.copy(
                isPlaying = true,
                currentPositionMs = clamped,
                errorMessage = null
            )
            startProgressTicker()
        } catch (e: Exception) {
            Log.e(TAG, "Seek and resume failed", e)
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
            }
            progressJob?.cancel()
            _state.value = _state.value.copy(
                isPlaying = false,
                currentPositionMs = player.currentPosition.toLong()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Pause failed", e)
        }
    }

    fun resume() {
        val player = mediaPlayer ?: return
        try {
            player.start()
            _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
            startProgressTicker()
        } catch (e: Exception) {
            Log.e(TAG, "Resume failed", e)
        }
    }

    fun cycleSpeed() {
        val nextSpeed = when (_state.value.playbackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            else -> 1.0f
        }
        _state.value = _state.value.copy(playbackSpeed = nextSpeed)
        mediaPlayer?.let { player ->
            try {
                val wasPlaying = player.isPlaying
                player.playbackParams = PlaybackParams().setSpeed(nextSpeed)
                if (!wasPlaying) {
                    player.pause()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update playback speed", e)
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            }
            try {
                player.reset()
                player.release()
            } catch (_: Exception) {
            }
        }
        mediaPlayer = null
        currentFilePath = null
        _state.value = PlaybackState(playbackSpeed = _state.value.playbackSpeed)
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _state.value.isPlaying) {
                val pos = try {
                    mediaPlayer?.currentPosition?.toLong() ?: _state.value.currentPositionMs
                } catch (_: Exception) {
                    _state.value.currentPositionMs
                }
                _state.value = _state.value.copy(currentPositionMs = pos)
                delay(100L)
            }
        }
    }
}
