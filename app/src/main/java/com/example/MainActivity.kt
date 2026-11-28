package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ai.MemoMindAiRepository
import com.example.audio.AudioPlayerController
import com.example.audio.AudioRecorderController
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.MemoMindRepository
import com.example.firebase.configureAppCheck
import com.example.local.LocalAudioDatabase
import com.example.model.UiState
import com.example.service.VoiceNoteForegroundService
import com.example.ui.components.MiniAudioPlayerBar
import com.example.ui.screens.AiMemoryChatScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExtractedInfoVaultScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.OnboardingConsentScreen
import com.example.ui.screens.RecordingDetailsScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SavedConversationsScreen
import com.example.ui.screens.SettingsAndPrivacyScreen
import com.example.ui.screens.VoiceRecordingScreen
import com.example.ui.theme.MemoMindTheme
import com.example.viewmodel.MemoMindViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private object Routes {
    const val HOME = "home"
    const val RECORDINGS = "recordings"
    const val AI_MEMORY = "ai_memory"
    const val REMINDERS = "reminders"
    const val SETTINGS = "settings"
    const val RECORD_STUDIO = "record_studio"
    const val VAULT = "vault"
    const val DETAILS = "details/{recordingId}"

    fun detailsRoute(recordingId: String) = "details/$recordingId"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    private var pendingOpenStudio by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check synchronously before setContent or any Firebase call
        configureAppCheck(this, intent)
        pendingOpenStudio = intent?.getBooleanExtra(VoiceNoteForegroundService.EXTRA_OPEN_RECORD_STUDIO, false) == true

        enableEdgeToEdge()
        setContent {
            MemoMindTheme {
                MemoMindRootApp(
                    openStudioTrigger = pendingOpenStudio,
                    onConsumeOpenStudio = { pendingOpenStudio = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(VoiceNoteForegroundService.EXTRA_OPEN_RECORD_STUDIO, false)) {
            pendingOpenStudio = true
        }
    }
}

@Composable
private fun MemoMindRootApp(
    openStudioTrigger: Boolean,
    onConsumeOpenStudio: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var checkingAutoSignIn by remember { mutableStateOf(currentUser == null) }

    LaunchedEffect(Unit) {
        if (Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { checkingAutoSignIn = false },
                onUnauthenticated = { checkingAutoSignIn = false },
                scope = scope
            )
        } else {
            checkingAutoSignIn = false
        }
    }

    val user = currentUser
    when {
        checkingAutoSignIn && user == null -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        }
        user == null -> {
            AuthScreen(
                onAuthSuccess = { checkingAutoSignIn = false }
            )
        }
        else -> {
            val uid = user.uid
            key(uid) {
                MemoMindAuthenticatedShell(
                    currentUserId = uid,
                    displayName = user.displayName.orEmpty(),
                    email = user.email.orEmpty(),
                    openStudioTrigger = openStudioTrigger,
                    onConsumeOpenStudio = onConsumeOpenStudio,
                    onSignOut = {
                        signOutUser(credentialManager, scope)
                    }
                )
            }
        }
    }
}

@Composable
private fun MemoMindAuthenticatedShell(
    currentUserId: String,
    displayName: String,
    email: String,
    openStudioTrigger: Boolean,
    onConsumeOpenStudio: () -> Unit,
    onSignOut: () -> Unit
) {
    val viewModel: MemoMindViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localDb = LocalAudioDatabase.getInstance(app)
                val repository = MemoMindRepository(app, localDb.localAudioDao())
                val aiRepository = MemoMindAiRepository()
                val recorderController = AudioRecorderController.getInstance(app)
                val playerController = AudioPlayerController()
                MemoMindViewModel(
                    appContext = app,
                    repository = repository,
                    aiRepository = aiRepository,
                    recorderController = recorderController,
                    playerController = playerController,
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    initialEmail = email
                )
            }
        }
    )

    val userProfileState by viewModel.userProfileState.collectAsStateWithLifecycle()
    val recordingsState by viewModel.recordingsState.collectAsStateWithLifecycle()
    val extractedInfoState by viewModel.extractedInfoState.collectAsStateWithLifecycle()
    val remindersState by viewModel.remindersState.collectAsStateWithLifecycle()
    val memoryMessagesState by viewModel.memoryMessagesState.collectAsStateWithLifecycle()
    val activeSegmentsState by viewModel.activeSegmentsState.collectAsStateWithLifecycle()
    val recorderState by viewModel.recorderState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val pendingDrafts by viewModel.pendingReminderDrafts.collectAsStateWithLifecycle()
    val transcribingIds by viewModel.transcribingIds.collectAsStateWithLifecycle()
    val isAskingMemory by viewModel.isAskingMemory.collectAsStateWithLifecycle()
    val feedbackBanner by viewModel.feedbackBanner.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedbackBanner) {
        val msg = feedbackBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackBanner()
        }
    }

    val profile = (userProfileState as? UiState.Success)?.data
    if (userProfileState is UiState.Success && profile != null && !profile.consentAccepted) {
        OnboardingConsentScreen(
            onAcceptAndContinue = { viewModel.acceptOnboardingConsent() }
        )
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    LaunchedEffect(openStudioTrigger) {
        if (openStudioTrigger) {
            navController.navigate(Routes.RECORD_STUDIO) {
                launchSingleTop = true
            }
            onConsumeOpenStudio()
        }
    }

    val tabs = remember {
        listOf(
            BottomTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
            BottomTab(Routes.RECORDINGS, "Recordings", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
            BottomTab(Routes.AI_MEMORY, "AI Memory", Icons.Filled.Psychology, Icons.Outlined.Psychology),
            BottomTab(Routes.REMINDERS, "Reminders", Icons.Filled.Notifications, Icons.Outlined.Notifications),
            BottomTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    val showBottomBar = currentRoute in tabs.map { it.route }
    val recordingsList = (recordingsState as? UiState.Success)?.data.orEmpty()
    val playingRecording = recordingsList.firstOrNull { it.id == playbackState.recordingId }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column {
                if (playbackState.recordingId != null && currentRoute != Routes.RECORD_STUDIO && currentRoute != Routes.DETAILS) {
                    MiniAudioPlayerBar(
                        playbackState = playbackState,
                        recordingTitle = playingRecording?.title.orEmpty(),
                        onTogglePlayPause = {
                            playingRecording?.let { viewModel.togglePlayPause(it) }
                        },
                        onSeek = { viewModel.seekAudio(it) },
                        onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
                        onStop = { viewModel.stopPlayback() },
                        onOpenRecording = {
                            playingRecording?.let {
                                navController.navigate(Routes.detailsRoute(it.id))
                            }
                        }
                    )
                }

                if (showBottomBar) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Routes.HOME) {
                HomeDashboardScreen(
                    userProfile = profile,
                    recordingsState = recordingsState,
                    extractedInfoState = extractedInfoState,
                    remindersState = remindersState,
                    pendingReminderDrafts = pendingDrafts,
                    transcribingIds = transcribingIds,
                    recorderState = recorderState,
                    onStartRecordingClick = {
                        if (viewModel.startRecording()) {
                            navController.navigate(Routes.RECORD_STUDIO)
                        }
                    },
                    onResumeActiveStudio = {
                        navController.navigate(Routes.RECORD_STUDIO) {
                            launchSingleTop = true
                        }
                    },
                    onOpenRecordings = {
                        navController.navigate(Routes.RECORDINGS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenRecording = { recId ->
                        navController.navigate(Routes.detailsRoute(recId))
                    },
                    onOpenAiMemory = {
                        navController.navigate(Routes.AI_MEMORY)
                    },
                    onOpenReminders = {
                        navController.navigate(Routes.REMINDERS)
                    },
                    onOpenVault = {
                        navController.navigate(Routes.VAULT)
                    },
                    onPlayRecording = { rec ->
                        viewModel.playRecordingAt(rec, 0L)
                    }
                )
            }

            composable(Routes.RECORD_STUDIO) {
                VoiceRecordingScreen(
                    recorderState = recorderState,
                    onStart = { viewModel.startRecording() },
                    onPause = { viewModel.pauseRecording() },
                    onResume = { viewModel.resumeRecording() },
                    onStopAndSave = { title, category, tags ->
                        viewModel.stopAndSaveRecording(title, category, tags) { savedId ->
                            navController.navigate(Routes.detailsRoute(savedId)) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    },
                    onDiscardAndExit = {
                        viewModel.discardRecording()
                        navController.popBackStack()
                    },
                    onToggleCameraPreview = { enabled -> viewModel.toggleCameraPreview(enabled) },
                    onCreateVisualContextFile = { viewModel.createVisualContextFile() },
                    onCameraContextCaptured = { file -> viewModel.onCameraContextCaptured(file) },
                    onAddHighlight = { viewModel.addVoiceHighlightNote() }
                )
            }

            composable(Routes.RECORDINGS) {
                SavedConversationsScreen(
                    recordingsState = recordingsState,
                    extractedInfo = (extractedInfoState as? UiState.Success)?.data.orEmpty(),
                    transcribingIds = transcribingIds,
                    onOpenRecording = { recId ->
                        navController.navigate(Routes.detailsRoute(recId))
                    },
                    onPlayRecording = { rec ->
                        viewModel.playRecordingAt(rec, 0L)
                    },
                    onStartRecordingClick = {
                        navController.navigate(Routes.HOME)
                    }
                )
            }

            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument("recordingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val recordingId = backStackEntry.arguments?.getString("recordingId").orEmpty()
                DisposableEffect(recordingId) {
                    viewModel.selectRecordingForDetails(recordingId)
                    onDispose { viewModel.selectRecordingForDetails(null) }
                }
                val recording = recordingsList.firstOrNull { it.id == recordingId }
                RecordingDetailsScreen(
                    recording = recording,
                    segmentsState = activeSegmentsState,
                    extractedInfo = (extractedInfoState as? UiState.Success)?.data.orEmpty(),
                    playbackState = playbackState,
                    isTranscribing = transcribingIds.contains(recordingId),
                    onBack = { navController.popBackStack() },
                    onPlayAt = { rec, ms -> viewModel.playRecordingAt(rec, ms) },
                    onTogglePlayPause = { rec -> viewModel.togglePlayPause(rec) },
                    onSeek = { ms -> viewModel.seekAudio(ms) },
                    onCycleSpeed = { viewModel.cyclePlaybackSpeed() },
                    onRetryTranscription = { rec -> viewModel.retryTranscription(rec) },
                    onRegenerateSummary = { rec -> viewModel.regenerateSummary(rec) },
                    onUpdateMetadata = { id, title, notes, cat, tags ->
                        viewModel.updateRecordingMetadata(id, title, notes, cat, tags)
                    },
                    onUpdateSegment = { recId, segId, text, speaker, allSegs ->
                        viewModel.updateSegmentText(recId, segId, text, speaker, allSegs)
                    },
                    onDeleteRecording = { id ->
                        viewModel.deleteRecording(id) {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(Routes.AI_MEMORY) {
                AiMemoryChatScreen(
                    messagesState = memoryMessagesState,
                    isAskingMemory = isAskingMemory,
                    onAskQuestion = { q, cb -> viewModel.askMemoryQuestion(q, cb) },
                    onPlayCitedAudio = { recId, tsMs ->
                        viewModel.playRecordingByIdAt(recId, tsMs)
                        navController.navigate(Routes.detailsRoute(recId))
                    },
                    onClearHistory = { viewModel.clearMemoryChat() }
                )
            }

            composable(Routes.REMINDERS) {
                RemindersScreen(
                    remindersState = remindersState,
                    pendingDrafts = pendingDrafts,
                    onConfirmDraft = { draft, title, due ->
                        viewModel.confirmReminderDraft(draft, title, due)
                    },
                    onDismissDraft = { draftId -> viewModel.dismissReminderDraft(draftId) },
                    onCreateManualReminder = { title, due -> viewModel.createManualReminder(title, due) },
                    onToggleCompleted = { rem -> viewModel.toggleReminderCompleted(rem) },
                    onEditReminder = { rem, title, due -> viewModel.editReminder(rem, title, due) },
                    onDeleteReminder = { remId -> viewModel.deleteReminder(remId) },
                    onPlaySourceAudio = { recId, tsMs ->
                        viewModel.playRecordingByIdAt(recId, tsMs)
                        navController.navigate(Routes.detailsRoute(recId))
                    }
                )
            }

            composable(Routes.VAULT) {
                ExtractedInfoVaultScreen(
                    extractedInfoState = extractedInfoState,
                    onBack = { navController.popBackStack() },
                    onPlayAt = { recId, tsMs ->
                        viewModel.playRecordingByIdAt(recId, tsMs)
                        navController.navigate(Routes.detailsRoute(recId))
                    },
                    onUpdateInfo = { id, label, value, verified ->
                        viewModel.updateExtractedInfo(id, label, value, verified)
                    },
                    onDeleteInfo = { id -> viewModel.deleteExtractedInfo(id) }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsAndPrivacyScreen(
                    userProfile = profile,
                    userEmail = email,
                    onUpdateSettings = { name, aiConsent, lang ->
                        viewModel.updateSettings(name, aiConsent, lang)
                    },
                    onOpenVault = { navController.navigate(Routes.VAULT) },
                    onBuildExportPayload = { viewModel.buildExportPayload() },
                    onSignOut = onSignOut,
                    onDeleteAllData = {
                        viewModel.deleteAllUserData {
                            onSignOut()
                        }
                    }
                )
            }
        }
    }
}
