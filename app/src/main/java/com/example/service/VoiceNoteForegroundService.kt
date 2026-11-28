package com.example.service

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioRecorderController
import com.example.model.MemoTimeFormatter
import com.example.model.RecorderState
import com.example.model.RecorderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val TAG = "VoiceNoteFGS"

class VoiceNoteForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var recorderController: AudioRecorderController
    private var stateObserverJob: Job? = null
    private var lastNotificationSecond: Long = -1L
    private var lastStatusRendered: RecorderStatus? = null
    private var lastFrameCountRendered: Int = -1

    override fun onCreate() {
        super.onCreate()
        recorderController = AudioRecorderController.getInstance(applicationContext)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_START -> handleStartForeground()
            ACTION_PAUSE -> {
                recorderController.pauseRecording()
                updateNotificationNow(recorderController.state.value)
            }
            ACTION_RESUME -> {
                recorderController.resumeRecording()
                updateNotificationNow(recorderController.state.value)
            }
            ACTION_STOP_AND_SAVE -> handleStopAndSaveFromNotification()
            ACTION_DISCARD -> handleDiscardFromNotification()
            else -> handleStartForeground()
        }
        return START_NOT_STICKY
    }

    private fun handleStartForeground() {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasCamera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            Log.w(TAG, "RECORD_AUDIO permission missing; cannot promote foreground service.")
            recorderController.setStatusNotice("Microphone permission is required for the foreground capture service.")
            recorderController.setForegroundServiceState(false)
            stopSelf()
            return
        }

        val currentStatus = recorderController.state.value.status
        if (currentStatus != RecorderStatus.RECORDING && currentStatus != RecorderStatus.PAUSED) {
            val started = recorderController.startRecording()
            if (started.isFailure) {
                Log.e(TAG, "Recorder failed inside service start", started.exceptionOrNull())
                recorderController.setForegroundServiceState(false)
                stopSelf()
                return
            }
        }

        val fgsTypes = computeForegroundServiceTypes(hasMic = hasMic, hasCamera = hasCamera)
        val typesLabel = if (hasCamera) "Mic + Camera" else "Mic Only"

        val initialNotification = buildNotification(recorderController.state.value, typesLabel)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                initialNotification,
                fgsTypes
            )
            recorderController.setForegroundServiceState(active = true, typesLabel = typesLabel)
            observeRecorderState()
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException promoting foreground service", se)
            recorderController.setForegroundServiceState(false)
            recorderController.setStatusNotice("Foreground service permission missing: ${se.localizedMessage}")
            stopSelf()
        } catch (ise: IllegalStateException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ise is ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "ForegroundServiceStartNotAllowedException from background", ise)
                recorderController.setStatusNotice("Open MemoMind to start real-time voice capture.")
            } else {
                Log.e(TAG, "IllegalStateException promoting foreground service", ise)
            }
            recorderController.setForegroundServiceState(false)
            stopSelf()
        }
    }

    private fun computeForegroundServiceTypes(hasMic: Boolean, hasCamera: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return 0
        }
        var types = 0
        if (hasMic) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (hasCamera) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        return types
    }

    private fun observeRecorderState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            recorderController.state.collectLatest { state ->
                when (state.status) {
                    RecorderStatus.RECORDING, RecorderStatus.PAUSED -> {
                        val elapsedSecond = state.elapsedMs / 1000L
                        if (elapsedSecond != lastNotificationSecond ||
                            state.status != lastStatusRendered ||
                            state.capturedFrameCount != lastFrameCountRendered
                        ) {
                            lastNotificationSecond = elapsedSecond
                            lastStatusRendered = state.status
                            lastFrameCountRendered = state.capturedFrameCount
                            updateNotificationNow(state)
                        }
                    }
                    RecorderStatus.IDLE, RecorderStatus.STOPPED -> {
                        stopForegroundAndSelf()
                    }
                }
            }
        }
    }

    private fun handleStopAndSaveFromNotification() {
        val finalizeResult = recorderController.stopAndFinalize()
        if (finalizeResult.isSuccess) {
            recorderController.publishNotificationSave(finalizeResult.getOrThrow())
            // Bring MainActivity forward so ViewModel immediately persists and runs AI transcription
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_RECORD_STUDIO, false)
            }
            try {
                startActivity(openIntent)
            } catch (_: Exception) {
            }
        }
        stopForegroundAndSelf()
    }

    private fun handleDiscardFromNotification() {
        recorderController.discardActiveRecording()
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        recorderController.setForegroundServiceState(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotificationNow(state: RecorderState) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val typesLabel = state.activeFgsTypesLabel.ifBlank { "Mic Active" }
        manager.notify(NOTIFICATION_ID, buildNotification(state, typesLabel))
    }

    private fun buildNotification(state: RecorderState, typesLabel: String): Notification {
        val isPaused = state.status == RecorderStatus.PAUSED
        val title = getString(
            if (isPaused) R.string.fgs_notification_title_paused else R.string.fgs_notification_title_recording
        )
        val elapsedStr = MemoTimeFormatter.formatTimestamp(state.elapsedMs)
        val visualSuffix = if (state.capturedFrameCount > 0) {
            " • ${state.capturedFrameCount} visual snap(s)"
        } else {
            ""
        }
        val body = "$elapsedStr • $typesLabel$visualSuffix"

        val contentIntent = PendingIntent.getActivity(
            this,
            REQ_OPEN_APP,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_RECORD_STUDIO, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleAction = if (isPaused) {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                getString(R.string.fgs_action_resume),
                servicePendingIntent(ACTION_RESUME, REQ_RESUME)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                getString(R.string.fgs_action_pause),
                servicePendingIntent(ACTION_PAUSE, REQ_PAUSE)
            ).build()
        }

        val stopSaveAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            getString(R.string.fgs_action_stop_save),
            servicePendingIntent(ACTION_STOP_AND_SAVE, REQ_STOP_SAVE)
        ).build()

        val discardAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_delete,
            getString(R.string.fgs_action_discard),
            servicePendingIntent(ACTION_DISCARD, REQ_DISCARD)
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(!isPaused || state.status == RecorderStatus.PAUSED)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent)
            .addAction(toggleAction)
            .addAction(stopSaveAction)
            .addAction(discardAction)
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, VoiceNoteForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fgs_channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateObserverJob?.cancel()
        recorderController.setForegroundServiceState(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "memomind_voice_capture_channel"
        const val NOTIFICATION_ID = 4102

        const val ACTION_START = "com.example.memomind.action.FGS_START"
        const val ACTION_PAUSE = "com.example.memomind.action.FGS_PAUSE"
        const val ACTION_RESUME = "com.example.memomind.action.FGS_RESUME"
        const val ACTION_STOP_AND_SAVE = "com.example.memomind.action.FGS_STOP_AND_SAVE"
        const val ACTION_DISCARD = "com.example.memomind.action.FGS_DISCARD"

        const val EXTRA_OPEN_RECORD_STUDIO = "extra_open_record_studio"

        private const val REQ_OPEN_APP = 100
        private const val REQ_PAUSE = 101
        private const val REQ_RESUME = 102
        private const val REQ_STOP_SAVE = 103
        private const val REQ_DISCARD = 104

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, VoiceNoteForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start VoiceNoteForegroundService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, VoiceNoteForegroundService::class.java)
            context.applicationContext.stopService(intent)
        }
    }
}
