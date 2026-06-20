package com.ChronosFlow.VBCR

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.ChronosFlow.VBCR.core.ui.shell.ChronosShellChromeSuppression
import com.ChronosFlow.VBCR.core.ui.shell.ChronosShellOverlayController
import com.ChronosFlow.VBCR.core.ui.shell.LocalChronosShellOverlayController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ChronosFlow.VBCR.navigation.rememberChronosNavigationState
import com.ChronosFlow.VBCR.core.ai.genai.GenAiAssistCopy
import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteDialog
import com.ChronosFlow.VBCR.core.ui.components.CommandPaletteItem
import com.ChronosFlow.VBCR.core.ui.motion.ChronosMotionDefaults
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionDirection
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionFactory
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionSet
import com.ChronosFlow.VBCR.core.ui.security.AppLockOverlay
import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiBooleanSetting
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.settings.resolveChronosDarkTheme
import com.ChronosFlow.VBCR.core.ui.settings.writeChronosUiBooleanSetting
import com.ChronosFlow.VBCR.core.ui.theme.ChronosTheme
import com.ChronosFlow.VBCR.onboarding.ChronosOnboarding
import com.ChronosFlow.VBCR.feature.daydial.dayDialCommandProvider
import com.ChronosFlow.VBCR.feature.focus.focusCommandProvider
import com.ChronosFlow.VBCR.feature.goals.goalCommandProvider
import com.ChronosFlow.VBCR.feature.habits.habitCommandProvider
import com.ChronosFlow.VBCR.feature.medication.medicationCommandProvider
import com.ChronosFlow.VBCR.feature.tasks.taskCommandProvider
import com.ChronosFlow.VBCR.core.notifications.EXTRA_INITIAL_SECTION
import com.ChronosFlow.VBCR.core.notifications.NotificationLaunch
import com.ChronosFlow.VBCR.core.notifications.consumeNotificationLaunchExtras
import com.ChronosFlow.VBCR.core.notifications.parseNotificationLaunch
import com.ChronosFlow.VBCR.core.notifications.parseSharedTextLaunch
import com.ChronosFlow.VBCR.navigation.ChronosNavigationShell
import com.ChronosFlow.VBCR.navigation.ChronosRoute
import com.ChronosFlow.VBCR.navigation.quickCreateCommandProvider
import com.ChronosFlow.VBCR.navigation.guardedMedicationOpener
import com.ChronosFlow.VBCR.navigation.navigateFromNotificationLaunch
import com.ChronosFlow.VBCR.navigation.navigateToMedication
import com.ChronosFlow.VBCR.security.AppLockLifecycleObserver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val STARTUP_SHELL_DEFER_MILLIS = 350L
internal const val LOCKED_APP_AUTH_DEFER_MILLIS = 700L
internal const val SHELL_BADGE_DATA_DEFER_MILLIS = 30_000L

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var appLockLifecycleObserver: AppLockLifecycleObserver

    private val launchIntentState = mutableStateOf<Intent?>(null)
    private val launchGeneration = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Branded cold-start splash (androidx SplashScreen API); hands off to Theme.ChronosFlow.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        launchIntentState.value = intent
        appLockLifecycleObserver.register()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkTheme = isNightMode == Configuration.UI_MODE_NIGHT_YES
        insetsController.isAppearanceLightStatusBars = !isDarkTheme
        insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setContent {
            var showFullShell by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(STARTUP_SHELL_DEFER_MILLIS)
                showFullShell = true
            }
            val appContext = applicationContext
            val onboardingScope = rememberCoroutineScope()
            val onboardingCompleted = rememberChronosUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_ONBOARDING_COMPLETED,
                false
            )
            val uiSettings = rememberChronosUiSettings()
            ChronosTheme(
                darkTheme = resolveChronosDarkTheme(uiSettings.appearanceMode),
                dynamicColor = uiSettings.dynamicColorEnabled,
                highContrastEnabled = uiSettings.highContrastEnabled,
                reducedMotion = uiSettings.reduceMotionEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    // Use opaque background — Color.Transparent let the white Android window
                    // bleed through during NavHost cross-fade transitions.
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    if (showFullShell) {
                        if (!onboardingCompleted) {
                            ChronosOnboarding(
                                onComplete = {
                                    onboardingScope.launch {
                                        appContext.writeChronosUiBooleanSetting(
                                            ChronosUiSettingsKeys.KEY_ONBOARDING_COMPLETED,
                                            true
                                        )
                                    }
                                }
                            )
                        } else {
                            ChronosFlowApp(
                                launchIntent = launchIntentState.value,
                                launchGeneration = launchGeneration.intValue,
                                featureFlags = uiSettings.featureFlags,
                                reduceMotionEnabled = uiSettings.reduceMotionEnabled,
                                onNotificationLaunchHandled = ::consumeNotificationLaunch
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchIntentState.value = intent
        launchGeneration.intValue++
    }

    private fun consumeNotificationLaunch() {
        val consumed = consumeNotificationLaunchExtras(intent)
        intent = consumed
        launchIntentState.value = consumed
    }
}

@SuppressLint("ContextCastToActivity")
@Composable
private fun ChronosFlowApp(
    launchIntent: Intent?,
    launchGeneration: Int,
    featureFlags: ChronosFeatureFlags,
    reduceMotionEnabled: Boolean,
    onNotificationLaunchHandled: () -> Unit
) {
    val appContext = LocalContext.current
    val appContentResolver = appContext.contentResolver
    val notificationPlan = remember(launchGeneration, launchIntent) {
        val basePlan = buildNotificationNavigationPlan(launchIntent, appContentResolver)
        // Attach the calling app's human-readable label when the launch originates from a share
        // (ACTION_SEND / ACTION_PROCESS_TEXT). The referrer URI has the form
        // android-app://com.example.app; we extract the host as the package name and resolve the
        // label via PackageManager. This is API 22+ (minSdk is already above that).
        val sourceAppLabel: String? = if (
            launchIntent?.action == android.content.Intent.ACTION_SEND ||
            launchIntent?.action == android.content.Intent.ACTION_PROCESS_TEXT
        ) {
            val activity = appContext as? android.app.Activity
            val pkg = activity?.referrer?.host
            pkg?.let { packageName ->
                runCatching {
                    val pm = appContext.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrNull()
            }
        } else null
        if (sourceAppLabel != null && basePlan.notificationLaunch != null) {
            basePlan.copy(
                notificationLaunch = basePlan.notificationLaunch.copy(sourceAppLabel = sourceAppLabel)
            )
        } else {
            basePlan
        }
    }
    val navState = rememberChronosNavigationState(
        startDayTarget = initialDayTargetForNotificationLaunch(notificationPlan.notificationLaunch)
    )
    val appLockViewModel: AppLockViewModel = hiltViewModel()
    val shellState = rememberDeferredShellState()
    val appLockState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity
    val openMedication = remember(navState, activity, appLockViewModel) {
        guardedMedicationOpener(activity, appLockViewModel, navState)
    }
    val openMedicationIfEnabled = remember(navState, openMedication, featureFlags) {
        {
            if (featureFlags.medicationEnabled) {
                openMedication()
            } else {
                navState.navigate(ChronosRoute.Day.createRoute())
            }
        }
    }
    val openTasksSidebar = remember(navState) {
        {
            navState.navigate(ChronosRoute.topLevelRouteFor(ChronosRoute.Tasks.section))
        }
    }
    val openHabitsSidebarIfEnabled = remember(navState, featureFlags) {
        {
            if (featureFlags.habitsEnabled) {
                navState.navigate(ChronosRoute.topLevelRouteFor(ChronosRoute.Habits.section))
            } else {
                navState.navigate(ChronosRoute.Day.createRoute())
            }
        }
    }
    val openMedicationSidebarIfEnabled = remember(navState, featureFlags) {
        {
            if (featureFlags.medicationEnabled) {
                navState.navigate(ChronosRoute.topLevelRouteFor(ChronosRoute.Medication.section))
            } else {
                navState.navigate(ChronosRoute.Day.createRoute())
            }
        }
    }
    val appLockTransition = remember(reduceMotionEnabled) {
        appLockVisibilityTransition(reduceMotionEnabled)
    }
    val assistantActions = remember(
        navState,
        activity,
        appLockViewModel,
        openTasksSidebar,
        openHabitsSidebarIfEnabled,
        openMedicationSidebarIfEnabled,
        featureFlags
    ) {
        AssistantCommandActions(
            onOpenDay = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY)) },
            onOpenTasks = openTasksSidebar,
            onOpenReview = {
                navState.navigate(
                    ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS)
                )
            },
            onOpenFocus = {
                navState.navigate(
                    ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
                )
            },
            onOpenHabits = openHabitsSidebarIfEnabled,
            onOpenMedication = openMedicationSidebarIfEnabled,
            onCaptureTask = { capture ->
                navState.navigate(
                    ChronosRoute.Tasks.createRoute(
                        target = ChronosRoute.TARGET_ADD,
                        capture = capture
                    )
                )
            },
            onCaptureHabit = { capture ->
                if (featureFlags.habitsEnabled) {
                    navState.navigate(
                        ChronosRoute.Habits.createRoute(
                            target = ChronosRoute.TARGET_ADD,
                            capture = capture
                        )
                    )
                } else {
                    navState.navigate(ChronosRoute.Day.createRoute())
                }
            },
            onCaptureMedication = { capture ->
                if (featureFlags.medicationEnabled) {
                    navState.navigateToMedication(
                        activity = activity,
                        appLockViewModel = appLockViewModel,
                        target = ChronosRoute.TARGET_ADD,
                        capture = capture
                    )
                } else {
                    navState.navigate(ChronosRoute.Day.createRoute())
                }
            },
            onCaptureFocus = { capture ->
                navState.navigate(
                    ChronosRoute.Day.createRoute(
                        target = ChronosRoute.Day.TARGET_FOCUS_PLANNER,
                        capture = capture
                    )
                )
            },
            habitsEnabled = featureFlags.habitsEnabled,
            medicationEnabled = featureFlags.medicationEnabled
        )
    }
    val commandPaletteCommands = remember(
        navState,
        activity,
        appLockViewModel,
        openTasksSidebar,
        openHabitsSidebarIfEnabled,
        openMedicationSidebarIfEnabled,
        featureFlags
    ) {
        {
            buildList {
                add(quickCreateCommandProvider(
                    onNewBlock = {
                        navState.navigate(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_ADD_BLOCK)
                        )
                    },
                    onNewTask = {
                        navState.navigate(
                            ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD)
                        )
                    },
                    onNewFocus = {
                        navState.navigate(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
                        )
                    },
                    onNewHabit = {
                        navState.navigate(
                            ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD)
                        )
                    },
                    onNewGoal = {
                        navState.navigate(
                            ChronosRoute.Goals.createRoute(ChronosRoute.TARGET_ADD)
                        )
                    },
                    onNewMedication = {
                        navState.navigateToMedication(
                            activity,
                            appLockViewModel,
                            ChronosRoute.TARGET_ADD
                        )
                    },
                    onNewJournal = {
                        navState.navigate(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_JOURNAL)
                        )
                    },
                    onLogSleep = {
                        navState.navigate(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_SLEEP)
                        )
                    },
                    onOpenRoutines = {
                        navState.navigate(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TEMPLATES)
                        )
                    },
                    featureFlags = featureFlags
                ))
                add(dayDialCommandProvider(
                    onOpenDayDial = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY)) },
                    onOpenPlan = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PLAN)) },
                    onOpenFocusPlanner = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)) },
                    onOpenReview = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS)) },
                    onOpenPlanningTools = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_DAY_TOOLS)) },
                    onOpenTemplates = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TEMPLATES)) },
                    onOpenAiSettings = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_AI_SETTINGS)) },
                    onOpenPrivacySync = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PRIVACY_SYNC)) },
                    onOpenNotificationSettings = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_NOTIFICATIONS)) },
                    onOpenAppearanceSettings = { navState.navigate(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_APPEARANCE)) },
                    featureFlags = featureFlags
                ))
                add(focusCommandProvider {
                    navState.navigate(
                        ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
                    )
                })
                add(taskCommandProvider(openTasksSidebar))
                if (featureFlags.habitsEnabled) {
                    add(habitCommandProvider(openHabitsSidebarIfEnabled))
                }
                if (featureFlags.goalsEnabled) {
                    add(goalCommandProvider {
                        navState.navigate(ChronosRoute.Goals())
                    })
                }
                if (featureFlags.medicationEnabled) {
                    add(medicationCommandProvider(onOpenMedication = openMedicationSidebarIfEnabled))
                }
            }.flatMap { provider -> provider.commands() }
        }
    }
    var activeCommandPaletteCommands by remember { mutableStateOf(emptyList<CommandPaletteItem>()) }
    var commandPaletteVisible by remember { mutableStateOf(false) }
    val openCommandPalette = {
        activeCommandPaletteCommands = commandPaletteCommands()
        commandPaletteVisible = true
    }

    LaunchedEffect(Unit) {
        appLockViewModel.onColdStart()
        if (launchIntent?.getStringExtra(EXTRA_INITIAL_SECTION) == "palette") {
            openCommandPalette()
        }
    }

    LaunchedEffect(activity, appLockViewModel, appLockState.appLockEnabled, appLockState.isAppLocked) {
        if (appLockState.appLockEnabled && appLockState.isAppLocked) {
            refreshDeviceAuthAfterFirstFrame(activity, appLockViewModel)
        }
    }

    LaunchedEffect(launchGeneration) {
        val launch = notificationPlan.notificationLaunch ?: return@LaunchedEffect
        if (launch.section == ChronosRoute.Medication.section && featureFlags.medicationEnabled) {
            navState.navigateToMedication(activity, appLockViewModel)
        } else {
            navState.navigateFromNotificationLaunch(launch, featureFlags)
        }
        onNotificationLaunchHandled()
    }

    val shellOverlayController = remember { ChronosShellOverlayController() }
    val appUnlocked = !(appLockState.appLockEnabled && appLockState.isAppLocked)

    CompositionLocalProvider(
        LocalChronosShellOverlayController provides shellOverlayController
    ) {
    ChronosShellChromeSuppression("command-palette", commandPaletteVisible && appUnlocked)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.K) {
                    openCommandPalette()
                    true
                } else {
                    false
                }
            }
    ) {
        ChronosNavigationShell(
            navState = navState,
            currentSection = navState.topLevelSection,
            currentDayTarget = navState.requestedDayTarget,
            shellState = shellState,
            onOpenCommandPalette = openCommandPalette,
            onQuickCapturePreview = { capture ->
                previewQuickCaptureCommand(capture, assistantActions)?.label
            },
            onQuickCaptureCommand = { capture ->
                runQuickCaptureCommand(capture, assistantActions)
            },
            onOpenMedication = openMedicationIfEnabled,
            activity = activity,
            appLockViewModel = appLockViewModel
        )
    }
    AnimatedVisibility(
        visible = appLockState.appLockEnabled && appLockState.isAppLocked,
        enter = appLockTransition.enter,
        exit = appLockTransition.exit
    ) {
        AppLockOverlay(
            title = stringResource(R.string.app_lock_title),
            subtitle = stringResource(R.string.app_lock_subtitle),
            canAuthenticate = appLockState.canAuthenticate,
            errorMessage = appLockState.authError,
            onUnlock = { appLockViewModel.unlockApp(activity) }
        )
    }
    if (commandPaletteVisible && appUnlocked) {
        CommandPaletteHost(
            commands = activeCommandPaletteCommands,
            assistantActions = assistantActions,
            onClosed = {
                commandPaletteVisible = false
                activeCommandPaletteCommands = emptyList()
            }
        )
    }
    }
}

@Composable
private fun rememberDeferredShellState(): ChronosShellState {
    var collectShellState by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(SHELL_BADGE_DATA_DEFER_MILLIS)
        collectShellState = true
    }
    if (!collectShellState) {
        return ChronosShellState()
    }
    val shellViewModel: ChronosShellViewModel = hiltViewModel()
    val shellState by shellViewModel.state.collectAsStateWithLifecycle()
    return shellState
}

private suspend fun refreshDeviceAuthAfterFirstFrame(
    activity: FragmentActivity,
    appLockViewModel: AppLockViewModel
) {
    withFrameNanos { }
    delay(LOCKED_APP_AUTH_DEFER_MILLIS)
    withContext(Dispatchers.IO) {
        appLockViewModel.refreshDeviceAuth(activity)
    }
}

@Composable
private fun CommandPaletteHost(
    commands: List<CommandPaletteItem>,
    assistantActions: AssistantCommandActions,
    onClosed: () -> Unit
) {
    val commandSearchViewModel = hiltViewModel<CommandSearchViewModel>()
    val commandUiState by commandSearchViewModel.uiState.collectAsStateWithLifecycle()
    val assistantPanel by commandSearchViewModel.assistantPanel.collectAsStateWithLifecycle()

    LaunchedEffect(commands, assistantActions) {
        commandSearchViewModel.dispatch(LauncherAction.OnOpen, assistantActions, commands)
    }

    CommandPaletteDialog(
        commands = commands,
        query = commandUiState.query,
        displayCommands = commandUiState.results,
        isExecuting = commandUiState.isExecuting,
        onQueryChange = {
            commandSearchViewModel.dispatch(LauncherAction.OnQueryChanged(it), assistantActions, commands)
        },
        onRunCommand = {
            commandSearchViewModel.dispatch(LauncherAction.OnExecute(it.id), assistantActions, commands)
            // The ask row converses in-place; every other command closes the palette.
            if (it.id != ASSISTANT_ASK_COMMAND_ID) onClosed()
        },
        onSubmitQuery = { query ->
            // Keyboard Go: run the best quick-capture create for capture-shaped text, otherwise
            // the top-ranked visible command.
            val ranCapture = commandSearchViewModel.runBestCaptureCommand(query, assistantActions)
            if (ranCapture) {
                onClosed()
            } else {
                commandUiState.results.maxByOrNull { it.priority }?.let { top ->
                    commandSearchViewModel.dispatch(LauncherAction.OnExecute(top.id), assistantActions, commands)
                    if (top.id != ASSISTANT_ASK_COMMAND_ID) onClosed()
                }
            }
        },
        onAskAssistant = { query ->
            commandSearchViewModel.askAssistant(query, commands, assistantActions)
        },
        assistantIsAsking = assistantPanel.isAsking,
        assistantStreamingReply = assistantPanel.streamingReply,
        assistantQuestion = assistantPanel.question,
        assistantHasConversation = assistantPanel.history.isNotEmpty(),
        assistantReply = assistantPanel.reply?.reply,
        assistantSourceLabel = assistantPanel.reply?.source?.let { GenAiAssistCopy.assistSourceLabel(it) },
        assistantProposalLabels = assistantPanel.reply?.proposedCommands?.map { it.title }.orEmpty(),
        onConfirmAssistantProposal = { index ->
            assistantPanel.reply?.proposedCommands?.getOrNull(index)?.let { command ->
                commandSearchViewModel.runAssistantProposal(command)
            }
            onClosed()
        },
        onDismissAssistantReply = { commandSearchViewModel.clearAssistant() },
        onDismiss = {
            commandSearchViewModel.dispatch(LauncherAction.OnClose, assistantActions, commands)
            onClosed()
        }
    )
}

private fun appLockVisibilityTransition(reduceMotionEnabled: Boolean): ChronosTransitionSet =
    ChronosTransitionFactory.fadeScale(
        durationMillis = if (reduceMotionEnabled) {
            ChronosMotionDefaults.ReducedDurationMillis
        } else {
            ChronosMotionDefaults.DefaultDurationMillis
        },
        easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction = ChronosTransitionDirection.Neutral,
        enterScale = 1f,
        exitScale = 1f
    )

internal data class NotificationNavigationPlan(
    val notificationLaunch: NotificationLaunch?
)

internal fun buildNotificationNavigationPlan(
    intent: Intent?,
    contentResolver: android.content.ContentResolver? = null
): NotificationNavigationPlan {
    // A notification/deep-link launch wins; otherwise treat an inbound Share / Process-text / file
    // (single OR bulk via SEND_MULTIPLE / .txt / .ics) intent from another app as a task capture.
    val launch = parseNotificationLaunch(intent) ?: parseSharedTaskImport(intent, contentResolver)
    // The shell starts on Day; Day-target notifications surface their tab in-place via
    // [initialDayTargetForNotificationLaunch], so no separate start route is needed.
    return NotificationNavigationPlan(
        notificationLaunch = launch
    )
}

internal fun initialDayTargetForNotificationLaunch(launch: NotificationLaunch?): String? =
    if (launch?.section == ChronosRoute.Day.section) launch.dayTarget else null
