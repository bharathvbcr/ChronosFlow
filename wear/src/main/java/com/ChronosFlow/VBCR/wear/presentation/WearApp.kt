package com.ChronosFlow.VBCR.wear.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Undo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.PagerDefaults
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CheckboxButton
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.PagerScaffoldDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.ChronosFlow.VBCR.core.domain.model.BlockCategories
import com.ChronosFlow.VBCR.wear.PhoneHandoff
import com.ChronosFlow.VBCR.wear.WearFocusStateStore
import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import com.ChronosFlow.VBCR.wear.model.WearFoldedReminderKind
import com.ChronosFlow.VBCR.wear.model.currentBlock
import com.ChronosFlow.VBCR.wear.model.dedupeFoldedRemindersAgainstTopTask
import com.ChronosFlow.VBCR.wear.model.sortMedsForGlance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalTime

private object Routes {
    const val HOME = "home"
    const val FOCUS = "focus"
}

/**
 * Identifiers for the page the app should open on, passed as an intent extra when launched from a
 * tile (or any deep entry point). Kept here, next to the pages they map to, so the tiles and
 * [MainActivity] share one vocabulary.
 */
object WearStartPage {
    const val EXTRA = "com.ChronosFlow.VBCR.wear.START_PAGE"
    const val NOW = "now"
    const val HABITS = "habits"
    const val TASKS = "tasks"
    const val MEDS = "meds"
    const val FOCUS = "focus"
}

/** The horizontally-paged primary destinations; Medication only earns a page on dose days. */
private enum class HomePage { NOW, HABITS, TASKS, MEDS }

/** Maps a [WearStartPage] launch hint to the home page it should land on (default: Now). */
private fun homePageFor(startPage: String?): HomePage = when (startPage) {
    WearStartPage.HABITS -> HomePage.HABITS
    WearStartPage.TASKS -> HomePage.TASKS
    WearStartPage.MEDS -> HomePage.MEDS
    else -> HomePage.NOW
}

/**
 * The watch app: a pager-first Wear Material3 experience. Swiping left/right moves between the
 * primary pages (Now / Habits / Tasks / Medication); the rotary crown scrolls within a page.
 * The live Focus session is the single stacked destination, dismissed by swiping right.
 */
@Composable
fun WearApp(
    viewModel: WearViewModel = viewModel(),
    startPage: String? = null,
    routeNonce: Int = 0
) {
    val summary by viewModel.daySummary.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val confirmation by viewModel.confirmation.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val syncPhase by viewModel.syncPhase.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()
    val haptics = LocalHapticFeedback.current

    // Pull a fresh mirror on every foreground so a never-synced or stale watch reconciles right
    // away rather than waiting for the phone's next background push — the phone's other publish
    // triggers don't fire while its app is simply open.
    LifecycleResumeEffect(Unit) {
        viewModel.requestSync()
        onPauseOrDispose { }
    }

    // Route each tile launch. Keyed on routeNonce (bumped per intent) so re-tapping a tile
    // re-routes even when the requested page is unchanged and even when the single-task activity
    // is reused via onNewIntent rather than recreated. The focus tile jumps to the live session;
    // any other tile collapses the focus screen so its target home page is what shows.
    LaunchedEffect(routeNonce) {
        if (startPage == WearStartPage.FOCUS && focus.active) {
            navController.navigate(Routes.FOCUS) { launchSingleTop = true }
        } else {
            navController.popBackStack(Routes.HOME, inclusive = false)
        }
    }

    // Pair each completing action with a confirming haptic so the wrist feels the result.
    fun confirmed(action: (String) -> Unit): (String) -> Unit = { id ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        action(id)
    }

    ChronosWearTheme {
        AppScaffold {
            SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomePager(
                        summary = summary,
                        focus = focus,
                        syncPhase = syncPhase,
                        startPage = startPage,
                        routeNonce = routeNonce,
                        onOpenFocus = { navController.navigate(Routes.FOCUS) },
                        onMarkHabitDone = confirmed(viewModel::markHabitDone),
                        onCompleteTask = confirmed(viewModel::completeTask),
                        onTakeDose = confirmed(viewModel::takeDose),
                        onCompleteNowBlock = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.completeNowBlock()
                        }
                    )
                }
                composable(Routes.FOCUS) {
                    FocusScreen(
                        state = focus,
                        onPause = viewModel::pauseFocus,
                        onResume = viewModel::resumeFocus,
                        onStop = {
                            viewModel.stopFocus()
                            navController.popBackStack()
                        },
                        onStart = { minutes -> viewModel.startFocus(minutes) }
                    )
                }
            }
        }

        // Brief success overlay that auto-dismisses; replaces the "item silently vanished" feel.
        val curvedTextStyle = ConfirmationDialogDefaults.curvedTextStyle
        SuccessConfirmationDialog(
            visible = confirmation != null,
            onDismissRequest = viewModel::dismissConfirmation,
            curvedText = { confirmationDialogCurvedText(confirmation.orEmpty(), curvedTextStyle) }
        )
        // Shown when a completion couldn't reach the phone, so the optimistic "Done" isn't trusted.
        FailureConfirmationDialog(
            visible = failure != null,
            onDismissRequest = viewModel::dismissFailure,
            curvedText = { confirmationDialogCurvedText(failure.orEmpty(), curvedTextStyle) },
            content = { ConfirmationDialogDefaults.ConnectionFailureIcon() }
        )
        // Task / habit / dose completions offer a brief "Undo" instead of the plain success flash —
        // a mis-tap on a small round screen is then recoverable in place (the phone reverses it).
        val undoable by viewModel.undoable.collectAsStateWithLifecycle()
        if (undoable != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Button(
                    onClick = viewModel::undo,
                    label = { Text("Undo", maxLines = 1) },
                    icon = {
                        Icon(
                            Icons.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    },
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HomePager(
    summary: WearDaySummary,
    focus: WearFocusStateStore.FocusState,
    syncPhase: WearViewModel.SyncPhase,
    startPage: String?,
    routeNonce: Int,
    onOpenFocus: () -> Unit,
    onMarkHabitDone: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onTakeDose: (String) -> Unit,
    onCompleteNowBlock: () -> Unit
) {
    val pages = remember(summary) {
        buildList {
            add(HomePage.NOW)
            add(HomePage.HABITS)
            add(HomePage.TASKS)
            if (summary.meds.isNotEmpty() || summary.medsDueCount > 0) add(HomePage.MEDS)
        }
    }
    // rememberPagerState caches its pageCount lambda, so route the live size through
    // rememberUpdatedState to keep the count current when the meds page appears/disappears.
    val pageCount by rememberUpdatedState(pages.size)
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(homePageFor(startPage)).coerceAtLeast(0),
        pageCount = { pageCount }
    )
    // Clamp the current page when the meds page disappears to avoid an out-of-bounds crash.
    LaunchedEffect(pages.size) {
        val clamped = pagerState.currentPage.coerceAtMost((pages.size - 1).coerceAtLeast(0))
        if (clamped != pagerState.currentPage) pagerState.scrollToPage(clamped)
    }
    // Honour a tile launch that lands here (or returns from focus): glide to the requested page.
    // Keyed on routeNonce so a fresh tap re-routes even after the user has swiped to another page.
    LaunchedEffect(routeNonce) {
        val target = pages.indexOf(homePageFor(startPage)).coerceAtLeast(0)
        if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
    }
    // Confirm each committed page change on the wrist. Every in-page action already buzzes; the
    // most frequent gesture — swiping between the primary pages — was the one silent interaction.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }
    // Let the Now page's overdue-med alert jump straight to the Meds page (present only on dose
    // days), so an urgent signal is actionable where it's seen instead of a 1–3 swipe hunt. Null
    // when there is no Meds page — which is exactly when there can be no overdue dose anyway.
    val scope = rememberCoroutineScope()
    val medsPageIndex = pages.indexOf(HomePage.MEDS)
    val onOpenMeds: (() -> Unit)? = if (medsPageIndex >= 0) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            scope.launch { pagerState.animateScrollToPage(medsPageIndex) }
        }
    } else {
        null
    }
    HorizontalPagerScaffold(pagerState = pagerState) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.snapFlingBehavior(
                state = pagerState,
                maxFlingPages = 1,
                snapPositionalThreshold = PagerScaffoldDefaults.HighSnapPositionalThreshold,
                snapAnimationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
            ),
            // The crown scrolls within a page; only swipes move between pages.
            rotaryScrollableBehavior = null
        ) { page ->
            AnimatedPage(pageIndex = page, pagerState = pagerState) {
                when (pages.getOrElse(page) { HomePage.NOW }) {
                    HomePage.NOW -> NowScreen(
                        summary,
                        focus,
                        syncPhase,
                        onOpenFocus,
                        onCompleteNowBlock,
                        onCompleteTask,
                        onTakeDose,
                        onMarkHabitDone,
                        onOpenMeds
                    )
                    HomePage.HABITS -> HabitsScreen(summary, focus, onOpenFocus, onMarkHabitDone)
                    HomePage.TASKS -> TasksScreen(summary, focus, onOpenFocus, onCompleteTask)
                    HomePage.MEDS -> MedsScreen(summary, focus, onOpenFocus, onTakeDose)
                }
            }
        }
    }
}

@Composable
private fun NowScreen(
    summary: WearDaySummary,
    focus: WearFocusStateStore.FocusState,
    syncPhase: WearViewModel.SyncPhase,
    onOpenFocus: () -> Unit,
    onCompleteNowBlock: () -> Unit,
    onCompleteTask: (String) -> Unit,
    onTakeDose: (String) -> Unit,
    onMarkHabitDone: (String) -> Unit,
    onOpenMeds: (() -> Unit)? = null
) {
    val neverSynced = summary.receivedAtMillis == 0L
    val syncing = neverSynced && syncPhase == WearViewModel.SyncPhase.SYNCING
    val state = rememberTransformingLazyColumnState()
    val taskSpec = rememberTransformationSpec()
    // Tick once a second while a session runs so the EdgeButton countdown stays live.
    val nowMillis by produceState(initialValue = System.currentTimeMillis(), focus) {
        while (focus.active && !focus.paused) {
            value = System.currentTimeMillis()
            delay(1000L)
        }
    }
    // Re-anchor the day dial's time marker twice a minute while the screen is composed.
    val nowMinute by produceState(initialValue = minuteOfDayNow()) {
        while (true) {
            delay(30_000L)
            value = minuteOfDayNow()
        }
    }
    // Warn when the mirror is old enough that "now"/"until"/the dial may no longer hold. Keyed to
    // the minute tick so it refreshes as the screen stays open without its own ticker.
    val staleLabel = remember(nowMinute, summary.receivedAtMillis) {
        WearFormat.syncAgeLabel(summary.receivedAtMillis, System.currentTimeMillis())
    }
    // The block spanning now (from the dial blocks — the now-block carries no start minute of its
    // own); backs both the progress bar and the start–end window line.
    val current = remember(summary.blocks, nowMinute) { currentBlock(summary.blocks, nowMinute) }
    // How far through the current block we are (0..1), for the at-a-glance progress bar; null in a gap.
    val blockProgress = remember(current, nowMinute) {
        current?.let { block ->
            val span = (block.endMinute - block.startMinute).coerceAtLeast(1)
            ((nowMinute - block.startMinute).toFloat() / span).coerceIn(0f, 1f)
        }
    }
    // Surface overdue doses on the home page so a missed med is seen without swiping to Meds.
    // Only computable when the meds list isn't privacy-redacted; the neutral day line still carries
    // the plain due count otherwise.
    val overdueMedCount = remember(summary.meds, nowMinute) {
        summary.meds.count { !it.taken && it.reminderMinute < nowMinute }
    }
    val focusLabel = when {
        !focus.active -> "Start focus"
        focus.paused -> "Paused ${WearFormat.mmss(focus.pausedTimeLeftSeconds)}"
        else -> {
            val secondsLeft = ((focus.plannedEndAtMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)
            "Focus ${WearFormat.mmss(secondsLeft)}"
        }
    }
    // Offer "Start focus" only where a session makes sense: a running session's countdown always
    // shows (to return to it), but during a rest block (break/sleep/meal) the invitation to start a
    // focus timer is suppressed — which is exactly what the mirrored nowCategory was carried for.
    // The Focus tile still routes to the picker, so this is a soft gate, not a lockout.
    val showFocusButton = focus.active || BlockCategories.supportsFocus(summary.nowCategory)
    ScreenScaffold(
        scrollState = state,
        edgeButton = {
            if (showFocusButton) {
                EdgeButton(
                    onClick = onOpenFocus,
                    modifier = Modifier.scrollable(
                        state,
                        orientation = Orientation.Vertical,
                        reverseDirection = true,
                        overscrollEffect = rememberOverscrollEffect()
                    )
                ) {
                    Text(focusLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            DayDialRing(blocks = summary.blocks, nowMinute = nowMinute)
            TransformingLazyColumn(state = state, contentPadding = contentPadding) {
                item {
                    ListHeader {
                        Text(
                            text = summary.nowTitle
                                ?: when {
                                    syncing -> "Syncing…"
                                    neverSynced -> "Not synced yet"
                                    else -> "Nothing scheduled"
                                },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (neverSynced) {
                    // Never synced on this watch (fresh install / cleared data) — distinct from a
                    // genuinely empty day, so the blank isn't mistaken for "nothing to do".
                    if (syncing) {
                        // A pull is in flight: show progress instead of the open-on-phone hint,
                        // so the screen doesn't look broken while the phone is answering.
                        item {
                            Caption(
                                "Getting today from your phone…",
                                modifier = Modifier.semantics {
                                    contentDescription = "Syncing with phone"
                                }
                            )
                        }
                    } else {
                        item { Caption("Open ChronosFlow on your phone to sync today.") }
                        item { OpenOnPhoneButton() }
                    }
                }
                if (summary.nowTitle != null) {
                    // Glanceable headline: how much longer the current block runs, with a bar
                    // showing how far through it is — answers "how much longer" without the
                    // mental math the bare end time used to need.
                    item { Prominent(WearFormat.remainingLabel(summary.nowEndMinute, nowMinute)) }
                    blockProgress?.let { progress ->
                        item {
                            val animated by animateFloatAsState(progress, label = "blockProgress")
                            LinearProgressIndicator(
                                progress = { animated },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Current block progress" }
                            )
                        }
                    }
                    // Full start–end window when the dial knows this block, else the bare end time.
                    item {
                        Caption(
                            current?.let { WearFormat.windowLabel(it.startMinute, it.endMinute) }
                                ?: "until ${WearFormat.minuteOfDay(summary.nowEndMinute)}"
                        )
                    }
                    // Mark the current block done from the wrist (e.g. when the focus timer was never
                    // started). Only when the phone sent the now-block id (absent if nothing's running).
                    summary.nowBlockId?.let {
                        item {
                            Button(
                                onClick = onCompleteNowBlock,
                                label = { Text("Complete", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    summary.nextTitle?.let {
                        item { Caption("Next ${WearFormat.startsInLabel(summary.nextStartMinute, nowMinute)} · $it") }
                    }
                    if (summary.nextBreakStartMinute > 0) {
                        item {
                            val breakTitle = summary.nextBreakTitle
                            val label = if (!breakTitle.isNullOrBlank() && !breakTitle.equals("Break", ignoreCase = true)) {
                                "Break ${WearFormat.startsInLabel(summary.nextBreakStartMinute, nowMinute)} · $breakTitle"
                            } else {
                                "Break ${WearFormat.startsInLabel(summary.nextBreakStartMinute, nowMinute)}"
                            }
                            Caption(label)
                        }
                    }
                } else summary.nextTitle?.let { next ->
                    // Idle: lead with how soon the next block starts, then its time + title.
                    item { Prominent("Next ${WearFormat.startsInLabel(summary.nextStartMinute, nowMinute)}") }
                    item { Caption("${WearFormat.minuteOfDay(summary.nextStartMinute)} · $next") }
                    if (summary.nextBreakStartMinute > 0) {
                        item {
                            val breakTitle = summary.nextBreakTitle
                            val label = if (!breakTitle.isNullOrBlank() && !breakTitle.equals("Break", ignoreCase = true)) {
                                "Break ${WearFormat.startsInLabel(summary.nextBreakStartMinute, nowMinute)} · $breakTitle"
                            } else {
                                "Break ${WearFormat.startsInLabel(summary.nextBreakStartMinute, nowMinute)}"
                            }
                            Caption(label)
                        }
                    }
                }
                // Warnings sit here, below the block info, so the title and its countdown stay
                // contiguous at the top of the glance. A missed dose carries real weight, so it
                // reads as a bold alert; a stale mirror is only a caveat, so it stays a quiet note.
                if (overdueMedCount > 0) {
                    item {
                        Prominent(
                            "💊 $overdueMedCount med${if (overdueMedCount == 1) "" else "s"} overdue",
                            color = MaterialTheme.colorScheme.error,
                            spoken = "$overdueMedCount medication${if (overdueMedCount == 1) "" else "s"} overdue",
                            onClick = onOpenMeds
                        )
                    }
                }
                staleLabel?.let { label ->
                    item {
                        Caption(
                            "⚠ $label",
                            style = MaterialTheme.typography.labelSmall,
                            spoken = label
                        )
                    }
                }
                // The single most-urgent open task, completable right here so you needn't swipe to
                // the Tasks page. Only when the phone sent task entries (privacy redaction strips
                // them; the day line below still carries the open count).
                summary.tasks.firstOrNull()?.let { task ->
                    item {
                        CheckboxButton(
                            checked = false,
                            onCheckedChange = { if (it) onCompleteTask(task.id) },
                            label = { Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text("Top task", maxLines = 1) },
                            modifier = Modifier.fillMaxWidth()
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                                .transformedHeight(this, taskSpec),
                            transformation = SurfaceTransformation(taskSpec)
                        )
                    }
                }
                val topTaskId = summary.tasks.firstOrNull()?.id
                dedupeFoldedRemindersAgainstTopTask(topTaskId, summary.foldedReminders).forEach { reminder ->
                    item {
                        val secondary = buildString {
                            if (reminder.isOverdue) append("Overdue · ")
                            append(reminder.detail)
                        }
                        CheckboxButton(
                            checked = false,
                            onCheckedChange = {
                                if (!it) return@CheckboxButton
                                when (reminder.kind) {
                                    WearFoldedReminderKind.MEDICATION -> onTakeDose(reminder.entityId)
                                    WearFoldedReminderKind.TASK -> onCompleteTask(reminder.entityId)
                                    WearFoldedReminderKind.HABIT -> onMarkHabitDone(reminder.entityId)
                                }
                            },
                            label = { Text(reminder.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            secondaryLabel = { Text(secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.fillMaxWidth()
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                                .transformedHeight(this, taskSpec),
                            transformation = SurfaceTransformation(taskSpec)
                        )
                    }
                }
                // The AI day digest is a whole-day glance — most useful when nothing is running,
                // and just noise below the block info during one — so surface it only when idle.
                // The day-line tally it used to sit beside (blocks/habits/tasks/meds counts) is
                // dropped: every part already lives on its own page and on the dial.
                if (summary.nowTitle == null) {
                    summary.digest?.let { digest -> item { Caption(digest) } }
                }
            }
        }
    }
}

/** Current wall-clock time as minute-of-day; the day dial's anchor. */
private fun minuteOfDayNow(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

/**
 * An [EdgeButton] that returns to the running focus session with its live countdown. Rendered as
 * the edge button on the Habits/Tasks/Meds pages only while a session is active, so a focus started
 * from Now (or a tile) stays visible and one tap away from anywhere — instead of a dead-end that a
 * wearer could only get back to by swiping all the way to Now.
 */
@Composable
private fun FocusReturnEdgeButton(focus: WearFocusStateStore.FocusState, onOpenFocus: () -> Unit) {
    // Tick each second while running so the countdown stays live; paused time is frozen.
    val nowMillis by produceState(initialValue = System.currentTimeMillis(), focus) {
        while (focus.active && !focus.paused) {
            value = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val label = if (focus.paused) {
        "Paused ${WearFormat.mmss(focus.pausedTimeLeftSeconds)}"
    } else {
        val secondsLeft = ((focus.plannedEndAtMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)
        "Focus ${WearFormat.mmss(secondsLeft)}"
    }
    EdgeButton(onClick = onOpenFocus) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HabitsScreen(
    summary: WearDaySummary,
    focus: WearFocusStateStore.FocusState,
    onOpenFocus: () -> Unit,
    onMarkDone: (String) -> Unit
) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = state,
        // A running session's return button; empty (the default) when idle, so it costs no space.
        edgeButton = { if (focus.active) FocusReturnEdgeButton(focus, onOpenFocus) }
    ) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text("Habits") } }
            if (summary.habitsTotal > 0) {
                if (summary.habitsDone >= summary.habitsTotal) {
                    item { Prominent("✓ All done", spoken = "All habits done") }
                } else {
                    item { Caption("${summary.habitsDone} of ${summary.habitsTotal} done") }
                    item {
                        val target = summary.habitsDone.toFloat() / summary.habitsTotal
                        val animated by animateFloatAsState(target, label = "habitsProgress")
                        LinearProgressIndicator(
                            progress = { animated },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription =
                                        "${summary.habitsDone} of ${summary.habitsTotal} habits done"
                                }
                        )
                    }
                }
            }
            if (summary.habits.isEmpty()) {
                if (summary.habitsTotal > 0) {
                    item { Caption("Hidden for privacy") }
                    item { OpenOnPhoneButton() }
                } else {
                    item { Caption("No habits today") }
                }
            }
            items(summary.habits, key = { it.id }) { habit ->
                CheckboxButton(
                    checked = habit.done,
                    onCheckedChange = { if (it) onMarkDone(habit.id) },
                    enabled = !habit.done,
                    label = { Text(habit.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = if (habit.streak > 0) {
                        {
                            Text(
                                "🔥 ${habit.streak}",
                                maxLines = 1,
                                modifier = Modifier.semantics {
                                    contentDescription = "${habit.streak} day streak"
                                }
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, spec)
                        .semantics { stateDescription = if (habit.done) "Completed" else "Not completed" }
                        .animateItem(),
                    transformation = SurfaceTransformation(spec)
                )
            }
        }
    }
}

@Composable
private fun TasksScreen(
    summary: WearDaySummary,
    focus: WearFocusStateStore.FocusState,
    onOpenFocus: () -> Unit,
    onComplete: (String) -> Unit
) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = state,
        // A running session's return button; empty (the default) when idle, so it costs no space.
        edgeButton = { if (focus.active) FocusReturnEdgeButton(focus, onOpenFocus) }
    ) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text("Tasks") } }
            if (summary.openTaskCount > 0) {
                item { Caption("${summary.openTaskCount} open") }
            }
            if (summary.tasks.isEmpty()) {
                if (summary.openTaskCount > 0) {
                    item { Caption("Hidden for privacy") }
                    item { OpenOnPhoneButton() }
                } else {
                    item { Prominent("✓ All clear", spoken = "All tasks clear") }
                }
            }
            items(summary.tasks, key = { it.id }) { task ->
                CheckboxButton(
                    checked = false,
                    onCheckedChange = { if (it) onComplete(task.id) },
                    label = { Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, spec)
                        .animateItem(),
                    transformation = SurfaceTransformation(spec)
                )
            }
        }
    }
}

@Composable
private fun MedsScreen(
    summary: WearDaySummary,
    focus: WearFocusStateStore.FocusState,
    onOpenFocus: () -> Unit,
    onTake: (String) -> Unit
) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val now = LocalTime.now()
    val nowMinute = now.hour * 60 + now.minute
    // The soonest still-to-take dose, so the page can look forward ("Next in 2h · Metformin") the
    // way the Now page does for blocks — not just report a backward-looking due tally.
    val nextDose = summary.meds
        .filter { !it.taken && it.reminderMinute > nowMinute }
        .minByOrNull { it.reminderMinute }
    ScreenScaffold(
        scrollState = state,
        // A running session's return button; empty (the default) when idle, so it costs no space.
        edgeButton = { if (focus.active) FocusReturnEdgeButton(focus, onOpenFocus) }
    ) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text("Medication") } }
            when {
                summary.medsDueCount > 0 -> item { Caption("${summary.medsDueCount} due") }
                summary.meds.isNotEmpty() -> item { Prominent("✓ All taken", spoken = "All medication taken") }
            }
            nextDose?.let { dose ->
                item { Caption("Next ${WearFormat.startsInLabel(dose.reminderMinute, nowMinute)} · ${dose.name}") }
            }
            if (summary.meds.isEmpty()) {
                if (summary.medsDueCount > 0) {
                    item { Caption("Hidden for privacy") }
                    item { OpenOnPhoneButton() }
                } else {
                    item { Caption("No medication today") }
                }
            }
            items(sortMedsForGlance(summary.meds), key = { it.id }) { med ->
                CheckboxButton(
                    checked = med.taken,
                    onCheckedChange = { if (it) onTake(med.id) },
                    enabled = !med.taken,
                    label = { Text(med.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        val overdue = !med.taken && med.reminderMinute < nowMinute
                        Text(
                            text = (if (overdue) "Overdue · " else "") +
                                "${med.doseLabel} · ${WearFormat.minuteOfDay(med.reminderMinute)}",
                            color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, spec)
                        .semantics { stateDescription = if (med.taken) "Taken" else "Not taken" }
                        .animateItem(),
                    transformation = SurfaceTransformation(spec)
                )
            }
        }
    }
}

@Composable
private fun Caption(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    spoken: String? = null
) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
    )
}

/**
 * The Now page's single at-a-glance headline (time remaining / time to next), in primary tint by
 * default. [color] lets an alert (e.g. overdue meds) reuse the same weight in the error tint, and
 * [spoken] gives TalkBack a clean phrase when the visible text leans on a glyph.
 */
@Composable
private fun Prominent(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    spoken: String? = null,
    onClick: (() -> Unit)? = null
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        textAlign = TextAlign.Center,
        // Two lines so a long value like "Next in 1h 45m" keeps its number at large font scales
        // instead of ellipsizing to "Next in 1h…" — the number is the whole point of this line.
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
    )
}

/** Hands off to the phone app (the escape hatch for lists hidden by privacy redaction). */
@Composable
private fun OpenOnPhoneButton() {
    val context = LocalContext.current
    var dialogVisible by remember { mutableStateOf(false) }
    Button(
        onClick = {
            PhoneHandoff.open(context)
            dialogVisible = true
        },
        label = { Text("Open on phone", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = {
            Icon(
                Icons.Filled.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    val text = OpenOnPhoneDialogDefaults.text
    val style = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = dialogVisible,
        onDismissRequest = { dialogVisible = false },
        curvedText = { openOnPhoneDialogCurvedText(text = text, style = style) }
    )
}

