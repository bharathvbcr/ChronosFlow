package com.ChronosFlow.VBCR.wear.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.AmbientTickEffect
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.ChronosFlow.VBCR.wear.WearFocusStateStore
import kotlinx.coroutines.delay

/** Quick-start lengths offered by the watch when no session is running (minutes). */
private val FOCUS_DURATIONS = listOf(15, 25, 45, 60)

/**
 * Live focus session control. A full-screen ring tracks the countdown; the time-left is
 * recomputed every second from the planned end time mirrored off the phone. When no session is
 * running it offers a duration picker so the wearer chooses the length right on the wrist.
 *
 * In ambient mode (wrist down) the screen degrades to a dimmed, minute-granularity countdown —
 * no ring, no controls — refreshed by the system's ambient tick, so a glance still answers
 * "how long is left" without burning the display.
 */
@Composable
fun FocusScreen(
    state: WearFocusStateStore.FocusState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onStart: (Int) -> Unit
) {
    if (!state.active) {
        IdleFocus(onStart = onStart)
        return
    }

    val ambientManager = LocalAmbientModeManager.current
    val inAmbient = ambientManager?.currentAmbientMode is AmbientMode.Ambient
    var ambientTick by remember { mutableIntStateOf(0) }
    ambientManager?.AmbientTickEffect { ambientTick++ }

    // Interactive: tick once a second so the countdown stays live while the screen is visible.
    // Ambient: re-read the clock only when the system's ambient tick restarts this producer.
    val nowMillis by produceState(
        initialValue = System.currentTimeMillis(),
        state, inAmbient, ambientTick
    ) {
        value = System.currentTimeMillis()
        while (!inAmbient) {
            delay(1000L)
            value = System.currentTimeMillis()
        }
    }
    val secondsLeft = if (state.paused) {
        state.pausedTimeLeftSeconds
    } else {
        ((state.plannedEndAtMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)
    }

    if (inAmbient) {
        AmbientFocus(state = state, secondsLeft = secondsLeft)
        return
    }

    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
    ) {
        val progress = if (state.totalSeconds > 0) {
            (secondsLeft.toFloat() / state.totalSeconds).coerceIn(0f, 1f)
        } else 0f
        // Glide instead of snap — the raw per-second value jumps hard on Resume (end time slides
        // out) and on the first tick after ambient. A fast-effects spec sweeps those without trying
        // to animate every 1s tick into visible motion, matching the two linear bars elsewhere.
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "focusRing"
        )

        CircularProgressIndicator(
            progress = { animatedProgress },
            startAngle = 135f,
            endAngle = 45f,
            modifier = Modifier
                .fillMaxSize()
                .padding(CircularProgressIndicatorDefaults.FullScreenPadding)
                .semantics { contentDescription = "Focus session progress" }
        )

        Column(
            // Scrollable so an overflowing stack at the largest font scale (h:mm:ss + title + ends +
            // large pause button + Stop) never traps the Stop action in the round bottom bezel.
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = WearFormat.mmss(secondsLeft),
                style = MaterialTheme.typography.displaySmall,
                // Never let "1:00:00" wrap mid-digit at large font scales — it's the whole point of
                // this screen and a wrap would also shove the Stop action off the round display.
                maxLines = 1,
                softWrap = false
            )
            state.title?.let {
                Text(
                    text = if (state.paused) "Paused · $it" else it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Wall-clock finish, so a glance answers "done by when". Hidden while paused, when the
            // end time is no longer fixed (it slides out as the session is resumed later).
            if (!state.paused) {
                val endLabel = remember(state.plannedEndAtMillis) {
                    val end = java.time.Instant.ofEpochMilli(state.plannedEndAtMillis)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime()
                    "%02d:%02d".format(end.hour, end.minute)
                }
                Text(
                    text = "ends $endLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Primary control: a single large Pause/Resume button. Stop is demoted to a quiet
            // text action below it so the obvious tap is "keep going / take a breath", not "end".
            FilledTonalIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (state.paused) onResume() else onPause()
                },
                modifier = Modifier.padding(top = 8.dp).size(IconButtonDefaults.LargeButtonSize)
            ) {
                if (state.paused) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                } else {
                    Icon(Icons.Filled.Pause, contentDescription = "Pause")
                }
            }
            TextButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                onStop()
            }) {
                Text("Stop", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Burn-in-friendly ambient rendering: dim gray text on pure black, minute granularity. */
@Composable
private fun AmbientFocus(state: WearFocusStateStore.FocusState, secondsLeft: Int) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (state.paused) "Paused" else "Focus",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            // Paused time is frozen, so the exact mm:ss stays truthful; a running session only
            // refreshes per ambient tick, so round up to whole minutes to avoid stale seconds.
            text = if (state.paused) {
                WearFormat.mmss(secondsLeft)
            } else {
                "${(secondsLeft + 59) / 60} min"
            },
            // Smaller than the interactive countdown: fewer lit pixels for a wrist-down glance that
            // only needs the number readable — the point of the ambient burn-in path.
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun IdleFocus(onStart: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    // Hoisted so the chosen custom length survives the row scrolling out of the lazy list.
    var customMinutes by remember { mutableIntStateOf(CUSTOM_DEFAULT) }
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            item { ListHeader { Text("Start focus") } }
            items(FOCUS_DURATIONS) { minutes ->
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onStart(minutes)
                    },
                    label = { Text("$minutes min") },
                    modifier = Modifier.fillMaxWidth()
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)
                )
            }
            // Any length the wearer wants: the phone already accepts an arbitrary duration
            // (startFocus -> WearActionContract.focusStart), so the watch isn't limited to presets.
            item {
                CustomDurationRow(
                    minutes = customMinutes,
                    onMinus = { customMinutes = (customMinutes - CUSTOM_STEP).coerceAtLeast(CUSTOM_MIN) },
                    onPlus = { customMinutes = (customMinutes + CUSTOM_STEP).coerceAtMost(CUSTOM_MAX) },
                    onStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onStart(customMinutes)
                    }
                )
            }
        }
    }
}

/** A compact +/- stepper (5-min steps, 5..120) plus a Start action for a custom focus length. */
@Composable
private fun CustomDurationRow(
    minutes: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onStart: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Custom",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            FilledTonalIconButton(onClick = onMinus) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer minutes")
            }
            Text(
                text = "$minutes min",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
            FilledTonalIconButton(onClick = onPlus) {
                Icon(Icons.Filled.Add, contentDescription = "More minutes")
            }
        }
        Button(
            onClick = onStart,
            label = { Text("Start $minutes min", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

private const val CUSTOM_MIN = 5
private const val CUSTOM_MAX = 120
private const val CUSTOM_STEP = 5
private const val CUSTOM_DEFAULT = 30
