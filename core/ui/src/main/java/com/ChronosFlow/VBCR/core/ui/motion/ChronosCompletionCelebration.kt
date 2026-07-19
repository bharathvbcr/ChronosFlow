package com.ChronosFlow.VBCR.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Peak scale for the completion spring pulse (settles back to 1f).
 * Shared by Focus session-complete and Today Complete / quick-item check-offs.
 */
const val ChronosCompletionCelebrationPeakScale = 1.08f

/**
 * One spring scale pulse (+ optional Confirm haptic) for meaningful completions.
 * No-op under reduced motion. Pair [scale] with `Modifier.graphicsLayer`.
 *
 * When the tap already goes through a Chronos*Button or chronosHapticClick, call
 * [celebrate] / [play] with `withHaptic = false` so Confirm does not double-fire.
 */
@Stable
class ChronosCompletionCelebration internal constructor(
    private val scaleAnimatable: Animatable<Float, AnimationVector1D>,
    private val haptics: HapticFeedback,
    private val scope: CoroutineScope,
    reduceMotionEnabled: Boolean,
) {
    var reduceMotionEnabled: Boolean = reduceMotionEnabled
        internal set

    val scale: Float
        get() = scaleAnimatable.value

    var isCelebrating: Boolean by mutableStateOf(false)
        private set

    fun celebrate(withHaptic: Boolean = true) {
        scope.launch { play(withHaptic = withHaptic) }
    }

    suspend fun play(withHaptic: Boolean = true) {
        if (reduceMotionEnabled) return
        isCelebrating = true
        if (withHaptic) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        scaleAnimatable.snapTo(1f)
        scaleAnimatable.animateTo(
            ChronosCompletionCelebrationPeakScale,
            ChronosValueAnimationFactory.navigationChromeScale(reducedMotion = false)
        )
        scaleAnimatable.animateTo(
            1f,
            ChronosValueAnimationFactory.navigationChromeScale(reducedMotion = false)
        )
        isCelebrating = false
    }
}

@Composable
fun rememberChronosCompletionCelebration(
    reduceMotionEnabled: Boolean = rememberChronosUiSettings().reduceMotionEnabled,
): ChronosCompletionCelebration {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scaleAnimatable = remember { Animatable(1f) }
    val celebration = remember(haptics, scope) {
        ChronosCompletionCelebration(
            scaleAnimatable = scaleAnimatable,
            haptics = haptics,
            scope = scope,
            reduceMotionEnabled = reduceMotionEnabled,
        )
    }
    celebration.reduceMotionEnabled = reduceMotionEnabled
    return celebration
}
