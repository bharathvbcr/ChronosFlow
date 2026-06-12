package com.chronosflow

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.chronosflow.core.ui.shell.ChronosShellChromeSuppression
import com.chronosflow.core.ui.shell.ChronosShellOverlayController
import com.chronosflow.core.ui.shell.LocalChronosShellOverlayController
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import com.chronosflow.core.ui.components.CommandPaletteDialog
import com.chronosflow.core.ui.components.CommandPaletteItem
import com.chronosflow.core.ui.motion.ChronosMotionDefaults
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.core.ui.motion.ChronosTransitionFactory
import com.chronosflow.core.ui.motion.ChronosTransitionSet
import com.chronosflow.core.ui.security.AppLockOverlay
import com.chronosflow.core.ui.settings.ChronosFeatureFlags
import com.chronosflow.core.ui.settings.ChronosUiSettingsKeys
import com.chronosflow.core.ui.settings.rememberChronosUiBooleanSetting
import com.chronosflow.core.ui.settings.rememberChronosUiSettings
import com.chronosflow.core.ui.settings.resolveChronosDarkTheme
import com.chronosflow.core.ui.settings.writeChronosUiBooleanSetting
import com.chronosflow.core.ui.theme.ChronosTheme
import com.chronosflow.onboarding.ChronosOnboarding
import com.chronosflow.feature.daydial.dayDialCommandProvider
import com.chronosflow.feature.focus.focusCommandProvider
import com.chronosflow.feature.goals.goalCommandProvider
import com.chronosflow.feature.habits.habitCommandProvider
import com.chronosflow.feature.medication.medicationCommandProvider
import com.chronosflow.feature.tasks.taskCommandProvider
import com.chronosflow.core.notifications.EXTRA_INITIAL_SECTION
import com.chronosflow.core.notifications.NotificationLaunch
import com.chronosflow.core.notifications.consumeNotificationLaunchExtras
import com.chronosflow.core.notifications.parseNotificationLaunch
import com.chronosflow.navigation.ChronosNavigationShell
import com.chronosflow.navigation.ChronosRoute
import com.chronosflow.navigation.quickCreateCommandProvider
import com.chronosflow.navigation.guardedMedicationOpener
import com.chronosflow.navigation.navigateFromNotificationLaunch
import com.chronosflow.navigation.navigateSingleTop
import com.chronosflow.navigation.navigateToMedication
import com.chronosflow.security.AppLockLifecycleObserver
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
    val notificationPlan = remember(launchGeneration, launchIntent) {
        buildNotificationNavigationPlan(launchIntent)
    }
    val navController = rememberNavController()
    val appLockViewModel: AppLockViewModel = hiltViewModel()
    val shellState = rememberDeferredShellState()
    val appLockState by appLockViewModel.uiState.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val activity = LocalContext.current as FragmentActivity
    val openMedication = remember(navController, activity, appLockViewModel) {
        guardedMedicationOpener(activity, appLockViewModel, navController)
    }
    val openMedicationIfEnabled = remember(navController, openMedication, featureFlags) {
        {
            if (featureFlags.medicationEnabled) {
                openMedication()
            } else {
                navController.navigateSingleTop(ChronosRoute.Day.createRoute())
            }
        }
    }
    val openTasksSidebar = remember(navController) {
        {
            navController.navigateSingleTop(ChronosRoute.topLevelRouteFor(ChronosRoute.Tasks.section))
        }
    }
    val openHabitsSidebarIfEnabled = remember(navController, featureFlags) {
        {
            if (featureFlags.habitsEnabled) {
                navController.navigateSingleTop(ChronosRoute.topLevelRouteFor(ChronosRoute.Habits.section))
            } else {
                navController.navigateSingleTop(ChronosRoute.Day.createRoute())
            }
        }
    }
    val openMedicationSidebarIfEnabled = remember(navController, featureFlags) {
        {
            if (featureFlags.medicationEnabled) {
                navController.navigateSingleTop(ChronosRoute.topLevelRouteFor(ChronosRoute.Medication.section))
            } else {
                navController.navigateSingleTop(ChronosRoute.Day.createRoute())
            }
        }
    }
    val appLockTransition = remember(reduceMotionEnabled) {
        appLockVisibilityTransition(reduceMotionEnabled)
    }
    val assistantActions = remember(
        navController,
        activity,
        appLockViewModel,
        openTasksSidebar,
        openHabitsSidebarIfEnabled,
        openMedicationSidebarIfEnabled,
        featureFlags
    ) {
        AssistantCommandActions(
            onOpenDay = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY)) },
            onOpenTasks = openTasksSidebar,
            onOpenReview = {
                navController.navigateSingleTop(
                    ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS)
                )
            },
            onOpenFocus = {
                navController.navigateSingleTop(
                    ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
                )
            },
            onOpenHabits = openHabitsSidebarIfEnabled,
            onOpenMedication = openMedicationSidebarIfEnabled,
            onCaptureTask = { capture ->
                navController.navigateSingleTop(
                    ChronosRoute.Tasks.createRoute(
                        target = ChronosRoute.TARGET_ADD,
                        capture = capture
                    )
                )
            },
            onCaptureHabit = { capture ->
                if (featureFlags.habitsEnabled) {
                    navController.navigateSingleTop(
                        ChronosRoute.Habits.createRoute(
                            target = ChronosRoute.TARGET_ADD,
                            capture = capture
                        )
                    )
                } else {
                    navController.navigateSingleTop(ChronosRoute.Day.createRoute())
                }
            },
            onCaptureMedication = { capture ->
                if (featureFlags.medicationEnabled) {
                    navController.navigateToMedication(
                        activity = activity,
                        appLockViewModel = appLockViewModel,
                        target = ChronosRoute.TARGET_ADD,
                        capture = capture
                    )
                } else {
                    navController.navigateSingleTop(ChronosRoute.Day.createRoute())
                }
            },
            onCaptureFocus = { capture ->
                navController.navigateSingleTop(
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
        navController,
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
                        navController.navigateSingleTop(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_ADD_BLOCK)
                        )
                    },
                    onNewTask = {
                        navController.navigateSingleTop(
                            ChronosRoute.Tasks.createRoute(target = ChronosRoute.TARGET_ADD)
                        )
                    },
                    onNewFocus = {
                        navController.navigateSingleTop(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
                        )
                    },
                    onNewHabit = {
                        navController.navigateSingleTop(
                            ChronosRoute.Habits.createRoute(ChronosRoute.TARGET_ADD)
                        )
                    },
                    onNewGoal = {
                        navController.navigateSingleTop(
                            ChronosRoute.Goals.createRoute(ChronosRoute.TARGET_ADD)
                        )
                    },
                    onNewMedication = {
                        navController.navigateToMedication(
                            activity,
                            appLockViewModel,
                            ChronosRoute.TARGET_ADD
                        )
                    },
                    onNewJournal = {
                        navController.navigateSingleTop(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_JOURNAL)
                        )
                    },
                    onLogSleep = {
                        navController.navigateSingleTop(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_SLEEP)
                        )
                    },
                    onOpenRoutines = {
                        navController.navigateSingleTop(
                            ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TEMPLATES)
                        )
                    },
                    featureFlags = featureFlags
                ))
                add(dayDialCommandProvider(
                    onOpenDayDial = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TODAY)) },
                    onOpenPlan = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PLAN)) },
                    onOpenFocusPlanner = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)) },
                    onOpenReview = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_INSIGHTS)) },
                    onOpenPlanningTools = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_DAY_TOOLS)) },
                    onOpenTemplates = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_TEMPLATES)) },
                    onOpenAiSettings = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_AI_SETTINGS)) },
                    onOpenPrivacySync = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PRIVACY_SYNC)) },
                    onOpenNotificationSettings = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_NOTIFICATIONS)) },
                    onOpenAppearanceSettings = { navController.navigateSingleTop(ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_APPEARANCE)) },
                    featureFlags = featureFlags
                ))
                add(focusCommandProvider {
                    navController.navigateSingleTop(
                        ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_FOCUS_PLANNER)
                    )
                })
                add(taskCommandProvider(openTasksSidebar))
                if (featureFlags.habitsEnabled) {
                    add(habitCommandProvider(openHabitsSidebarIfEnabled))
                }
                if (featureFlags.goalsEnabled) {
                    add(goalCommandProvider {
                        navController.navigateSingleTop(ChronosRoute.Goals.route)
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
            navController.navigateToMedication(activity, appLockViewModel)
        } else {
            navController.navigateFromNotificationLaunch(launch, featureFlags)
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
            navController = navController,
            startRoute = notificationPlan.startRoute,
            currentSection = ChronosRoute.fromSection(
                currentBackStackEntry?.destination?.route?.substringBefore("?")
            ).section,
            currentDayTarget = currentBackStackEntry?.arguments?.getString("target"),
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
            appLockViewModel = appLockViewModel,
            initialDayTarget = initialDayTargetForNotificationLaunch(notificationPlan.notificationLaunch),
            externalDayTarget = initialDayTargetForNotificationLaunch(notificationPlan.notificationLaunch),
            externalDayTargetGeneration = launchGeneration
        )
    }
    AnimatedVisibility(
        visible = appLockState.appLockEnabled && appLockState.isAppLocked,
        enter = appLockTransition.enter,
        exit = appLockTransition.exit
    ) {
        AppLockOverlay(
            title = "ChronosFlow is locked",
            subtitle = "Use your fingerprint, face, or device PIN to continue",
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
    val startRoute: String,
    val notificationLaunch: NotificationLaunch?
)

internal fun buildNotificationNavigationPlan(intent: Intent?): NotificationNavigationPlan {
    val launch = parseNotificationLaunch(intent)
    // Start Day-target notifications on their routed Day destination so the first
    // composition can render the requested surface without a second navigation pass.
    return NotificationNavigationPlan(
        startRoute = startRouteForNotificationLaunch(launch),
        notificationLaunch = launch
    )
}

internal fun startRouteForNotificationLaunch(launch: NotificationLaunch?): String =
    if (launch?.section == ChronosRoute.Day.section && launch.dayTarget != null) {
        ChronosRoute.Day.createRoute(launch.dayTarget)
    } else {
        ChronosRoute.Day.createRoute()
    }

internal fun initialDayTargetForNotificationLaunch(launch: NotificationLaunch?): String? =
    if (launch?.section == ChronosRoute.Day.section) launch.dayTarget else null
