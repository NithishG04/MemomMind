package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "VoiceAssistantHelper"

data class VoiceUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val partialTranscript: String = "",
    val errorMessage: String? = null,
    val speechAvailable: Boolean = true
)

class VoiceAssistantHelper(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(
        VoiceUiState(speechAvailable = SpeechRecognizer.isRecognitionAvailable(appContext))
    )
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var ttsReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val res = tts?.setLanguage(Locale.US)
            ttsReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
        } else {
            ttsReady = false
        }
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = _state.value.copy(
                isListening = false,
                speechAvailable = false,
                errorMessage = "Speech recognition service is not available on this device. You can type your question below."
            )
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(
                        isListening = true,
                        partialTranscript = "",
                        errorMessage = null
                    )
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(isListening = false)
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please try speaking again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap the mic to try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice input."
                        else -> "Voice input stopped (code $error). You can type or retry."
                    }
                    _state.value = _state.value.copy(isListening = false, errorMessage = msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    _state.value = _state.value.copy(isListening = false, partialTranscript = "")
                    if (spoken.isNotBlank()) {
                        onFinalResult(spoken)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = matches?.firstOrNull()?.trim().orEmpty()
                    if (partial.isNotBlank()) {
                        _state.value = _state.value.copy(partialTranscript = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            _state.value = _state.value.copy(isListening = true, errorMessage = null, partialTranscript = "")
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer start failed", e)
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = e.localizedMessage ?: "Failed to start voice input."
            )
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(isListening = false)
    }

    fun speak(text: String) {
        if (!ttsReady || text.isBlank()) return
        try {
            _state.value = _state.value.copy(isSpeaking = true)
            tts?.speak(text.take(800), TextToSpeech.QUEUE_FLUSH, null, "memomind_tts")
        } catch (e: Exception) {
            Log.w(TAG, "TTS speak failed", e)
            _state.value = _state.value.copy(isSpeaking = false)
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _state.value = _state.value.copy(isSpeaking = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {
        }
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }
}
