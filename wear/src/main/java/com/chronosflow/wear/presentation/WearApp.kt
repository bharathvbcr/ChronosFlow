package com.chronosflow.wear.presentation

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.chronosflow.wear.PhoneHandoff
import com.chronosflow.wear.WearFocusStateStore
import com.chronosflow.wear.model.WearDaySummary
import kotlinx.coroutines.delay
import java.time.LocalTime

private object Routes {
    const val HOME = "home"
    const val FOCUS = "focus"
}

/** The horizontally-paged primary destinations; Medication only earns a page on dose days. */
private enum class HomePage { NOW, HABITS, TASKS, MEDS }

/**
 * The watch app: a pager-first Wear Material3 experience. Swiping left/right moves between the
 * primary pages (Now / Habits / Tasks / Medication); the rotary crown scrolls within a page.
 * The live Focus session is the single stacked destination, dismissed by swiping right.
 */
@Composable
fun WearApp(viewModel: WearViewModel = viewModel()) {
    val summary by viewModel.daySummary.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val confirmation by viewModel.confirmation.collectAsStateWithLifecycle()
    val navController = rememberSwipeDismissableNavController()
    val haptics = LocalHapticFeedback.current

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
                        onOpenFocus = { navController.navigate(Routes.FOCUS) },
                        onMarkHabitDone = confirmed(viewModel::markHabitDone),
                        onCompleteTask = confirmed(viewModel::completeTask),
                        onTakeDose = confirmed(viewModel::takeDose)
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
    }
}

@Composable
private fun HomePager(
    summary: WearDaySummary,
    focus: WearFocusStateStore.FocusState,
    onOpenFocus: () -> Unit,
    onMarkHabitDone: (String) -> Unit,
    onCompleteTask: (String) -> Unit,
    onTakeDose: (String) -> Unit
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
    val pagerState = rememberPagerState(pageCount = { pageCount })
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
                    HomePage.NOW -> NowScreen(summary, focus, onOpenFocus)
                    HomePage.HABITS -> HabitsScreen(summary, onMarkHabitDone)
                    HomePage.TASKS -> TasksScreen(summary, onCompleteTask)
                    HomePage.MEDS -> MedsScreen(summary, onTakeDose)
                }
            }
        }
    }
}

@Composable
private fun NowScreen(
    summary: WearDaySummary,
    focus: WearFocusStateStore.FocusState,
    onOpenFocus: () -> Unit
) {
    val state = rememberTransformingLazyColumnState()
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
    val focusLabel = when {
        !focus.active -> "Start focus"
        focus.paused -> "Paused ${WearFormat.mmss(focus.pausedTimeLeftSeconds)}"
        else -> {
            val secondsLeft = ((focus.plannedEndAtMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)
            "Focus ${WearFormat.mmss(secondsLeft)}"
        }
    }
    ScreenScaffold(
        scrollState = state,
        edgeButton = {
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
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            DayDialRing(blocks = summary.blocks, nowMinute = nowMinute)
            TransformingLazyColumn(state = state, contentPadding = contentPadding) {
                item {
                    ListHeader {
                        Text(
                            text = summary.nowTitle ?: "Nothing scheduled",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                summary.nowTitle?.let {
                    item { Caption("until ${WearFormat.minuteOfDay(summary.nowEndMinute)}") }
                }
                summary.nextTitle?.let {
                    item { Caption("Next ${WearFormat.minuteOfDay(summary.nextStartMinute)} · $it") }
                }
                dayLine(summary)?.let { line ->
                    item { Caption(line) }
                }
                summary.digest?.let { digest ->
                    item { Caption(digest) }
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

@Composable
private fun HabitsScreen(summary: WearDaySummary, onMarkDone: (String) -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text("Habits") } }
            if (summary.habitsTotal > 0) {
                item { Caption("${summary.habitsDone} of ${summary.habitsTotal} done") }
                item {
                    LinearProgressIndicator(
                        progress = { summary.habitsDone.toFloat() / summary.habitsTotal },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        { Text("🔥 ${habit.streak}", maxLines = 1) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)
                )
            }
        }
    }
}

@Composable
private fun TasksScreen(summary: WearDaySummary, onComplete: (String) -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
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
                    item { Caption("All clear") }
                }
            }
            items(summary.tasks, key = { it.id }) { task ->
                CheckboxButton(
                    checked = false,
                    onCheckedChange = { if (it) onComplete(task.id) },
                    label = { Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.fillMaxWidth()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)
                )
            }
        }
    }
}

@Composable
private fun MedsScreen(summary: WearDaySummary, onTake: (String) -> Unit) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val now = LocalTime.now()
    val nowMinute = now.hour * 60 + now.minute
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text("Medication") } }
            item { Caption(if (summary.medsDueCount == 0) "All taken" else "${summary.medsDueCount} due") }
            if (summary.meds.isEmpty()) {
                if (summary.medsDueCount > 0) {
                    item { Caption("Hidden for privacy") }
                    item { OpenOnPhoneButton() }
                } else {
                    item { Caption("No medication today") }
                }
            }
            items(summary.meds, key = { it.id }) { med ->
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
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)
                )
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
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

/** One-line day digest for the Now page, e.g. "2/5 habits · 3 tasks · 1 med due". */
private fun dayLine(summary: WearDaySummary): String? {
    val parts = buildList {
        if (summary.habitsTotal > 0) add("${summary.habitsDone}/${summary.habitsTotal} habits")
        if (summary.openTaskCount > 0) add("${summary.openTaskCount} tasks")
        if (summary.medsDueCount > 0) add("${summary.medsDueCount} meds due")
    }
    return parts.joinToString(" · ").ifBlank { null }
}
