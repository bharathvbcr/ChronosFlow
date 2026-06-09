package com.chronosflow.core.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.roundToInt

enum class ChronosTransitionDirection {
    Forward,
    Backward,
    Neutral
}

data class ChronosMotionConfig(
    val durationMillis: Int = ChronosMotionDefaults.DefaultDurationMillis,
    val reducedDurationMillis: Int = ChronosMotionDefaults.ReducedDurationMillis,
    val easing: Easing = ChronosMotionDefaults.MaterialStandardEasing,
    val slideFraction: Float = ChronosMotionDefaults.SharedAxisSlideFraction,
    val enterScale: Float = ChronosMotionDefaults.SharedAxisEnterScale,
    val exitScale: Float = ChronosMotionDefaults.SharedAxisExitScale
) {
    fun reducedMotion(): ChronosMotionConfig = copy(
        durationMillis = reducedDurationMillis,
        slideFraction = 0f,
        enterScale = 1f,
        exitScale = 1f
    )
}

object ChronosMotionDefaults {
    const val DefaultDurationMillis = 300
    const val ReducedDurationMillis = 150
    const val PrimaryTabDurationMillis = 240
    const val ChromeDurationMillis = 110
    // Increased from 0.08 — gives visible directional motion without feeling slow
    const val SharedAxisSlideFraction = 0.14f
    const val PrimaryTabSlideFraction = 0.09f
    const val PrimaryTabSpringDampingRatio = 0.78f
    const val PrimaryTabSpringStiffness = 760f
    // Wider scale range eliminates the flat fade that caused the white-flash percept
    const val SharedAxisEnterScale = 0.93f
    const val SharedAxisExitScale = 1.07f
    const val PrimaryTabScale = 1f
    // Enter alpha starts early so route transitions never fall through to the app/window backdrop.
    const val EnterFadeDelayFraction = 0.12f
    const val ExitFadeDurationFraction = 1f
    const val ChromeScaleDampingRatio = 0.86f
    const val ChromeScaleStiffness = 900f
    // Focus timer ring sweep — slower than navigation so per-second progress reads as a glide.
    const val FocusProgressDurationMillis = 500
    // Selection state changes (drawer pills, chips) — quicker than full-screen navigation.
    const val SelectionDurationMillis = 180
    // Dial hour hand — gentle, fully damped follow (matches Spring.StiffnessLow / DampingRatioNoBouncy).
    const val DialHandStiffness = 200f
    const val DialHandDampingRatio = 1f
    // Press feedback scale shared by tappable surfaces.
    const val PressedScale = 0.96f

    val MaterialStandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val ExitEasing: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val Navigation: ChronosMotionConfig = ChronosMotionConfig()
}

data class ChronosTransitionSet(
    val enter: EnterTransition,
    val exit: ExitTransition
) {
    fun asContentTransform(): ContentTransform =
        ContentTransform(
            targetContentEnter = enter,
            initialContentExit = exit,
            sizeTransform = SizeTransform(clip = false)
        )
}

object ChronosTransitionFactory {
    fun none(): ChronosTransitionSet =
        ChronosTransitionSet(
            enter = EnterTransition.None,
            exit = ExitTransition.None
        )

    fun slideRight(
        durationMillis: Int = ChronosMotionDefaults.DefaultDurationMillis,
        easing: Easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction: ChronosTransitionDirection = ChronosTransitionDirection.Forward,
        slideFraction: Float = ChronosMotionDefaults.SharedAxisSlideFraction
    ): ChronosTransitionSet {
        if (direction == ChronosTransitionDirection.Neutral || slideFraction == 0f) {
            return none()
        }

        val multiplier = direction.multiplier
        return ChronosTransitionSet(
            enter = slideInHorizontally(
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = easing
                ),
                initialOffsetX = { width -> (width * slideFraction * multiplier).roundToInt() }
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = easing
                ),
                targetOffsetX = { width -> (-width * slideFraction * multiplier).roundToInt() }
            )
        )
    }

    fun slideUp(
        durationMillis: Int = ChronosMotionDefaults.DefaultDurationMillis,
        easing: Easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction: ChronosTransitionDirection = ChronosTransitionDirection.Forward,
        slideFraction: Float = ChronosMotionDefaults.SharedAxisSlideFraction
    ): ChronosTransitionSet {
        if (direction == ChronosTransitionDirection.Neutral || slideFraction == 0f) {
            return none()
        }

        val multiplier = direction.multiplier
        return ChronosTransitionSet(
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = easing
                ),
                initialOffsetY = { height -> (height * slideFraction * multiplier).roundToInt() }
            ),
            exit = slideOutVertically(
                animationSpec = tween(
                    durationMillis = durationMillis,
                    easing = easing
                ),
                targetOffsetY = { height -> (-height * slideFraction * multiplier).roundToInt() }
            )
        )
    }

    fun fadeScale(
        durationMillis: Int = ChronosMotionDefaults.DefaultDurationMillis,
        easing: Easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction: ChronosTransitionDirection = ChronosTransitionDirection.Neutral,
        enterScale: Float = ChronosMotionDefaults.SharedAxisEnterScale,
        exitScale: Float = ChronosMotionDefaults.SharedAxisExitScale
    ): ChronosTransitionSet {
        val transformOrigin = direction.transformOrigin
        val enterDelayMillis = (durationMillis * ChronosMotionDefaults.EnterFadeDelayFraction).roundToInt()
        val enterDuration = durationMillis - enterDelayMillis
        val enter = fadeIn(
            animationSpec = tween(
                durationMillis = enterDuration,
                delayMillis = enterDelayMillis,
                easing = easing
            )
        ) + scaleIn(
            initialScale = enterScale,
            transformOrigin = transformOrigin,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = easing
            )
        )
        val exit = fadeOut(
            animationSpec = tween(
                durationMillis = (durationMillis * ChronosMotionDefaults.ExitFadeDurationFraction).roundToInt(),
                easing = ChronosMotionDefaults.ExitEasing
            )
        ) + scaleOut(
            targetScale = exitScale,
            transformOrigin = transformOrigin,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = ChronosMotionDefaults.ExitEasing
            )
        )
        return ChronosTransitionSet(enter = enter, exit = exit)
    }

    fun materialSharedAxis(
        durationMillis: Int = ChronosMotionDefaults.DefaultDurationMillis,
        easing: Easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction: ChronosTransitionDirection = ChronosTransitionDirection.Forward,
        slideFraction: Float = ChronosMotionDefaults.SharedAxisSlideFraction,
        enterScale: Float = ChronosMotionDefaults.SharedAxisEnterScale,
        exitScale: Float = ChronosMotionDefaults.SharedAxisExitScale
    ): ChronosTransitionSet {
        val slide = slideRight(
            durationMillis = durationMillis,
            easing = easing,
            direction = direction,
            slideFraction = slideFraction
        )
        val fadeScale = fadeScale(
            durationMillis = durationMillis,
            easing = easing,
            direction = direction,
            enterScale = enterScale,
            exitScale = exitScale
        )
        return ChronosTransitionSet(
            enter = slide.enter + fadeScale.enter,
            exit = slide.exit + fadeScale.exit
        )
    }

    fun materialSharedAxisY(
        durationMillis: Int = ChronosMotionDefaults.DefaultDurationMillis,
        easing: Easing = ChronosMotionDefaults.MaterialStandardEasing,
        direction: ChronosTransitionDirection = ChronosTransitionDirection.Forward,
        slideFraction: Float = ChronosMotionDefaults.SharedAxisSlideFraction,
        enterScale: Float = ChronosMotionDefaults.SharedAxisEnterScale,
        exitScale: Float = ChronosMotionDefaults.SharedAxisExitScale
    ): ChronosTransitionSet {
        val slide = slideUp(
            durationMillis = durationMillis,
            easing = easing,
            direction = direction,
            slideFraction = slideFraction
        )
        val fadeScale = fadeScale(
            durationMillis = durationMillis,
            easing = easing,
            direction = direction,
            enterScale = enterScale,
            exitScale = exitScale
        )
        return ChronosTransitionSet(
            enter = slide.enter + fadeScale.enter,
            exit = slide.exit + fadeScale.exit
        )
    }
}

object ChronosValueAnimationFactory {
    fun navigationChromeScale(reducedMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = ChronosMotionDefaults.ChromeScaleDampingRatio,
                stiffness = ChronosMotionDefaults.ChromeScaleStiffness
            )
        }

    fun quickAddRotation(reducedMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reducedMotion) {
            snap()
        } else {
            tween(
                durationMillis = ChronosMotionDefaults.ChromeDurationMillis,
                easing = ChronosMotionDefaults.MaterialStandardEasing
            )
        }

    fun focusTimerProgress(reducedMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reducedMotion) {
            snap()
        } else {
            tween(
                durationMillis = ChronosMotionDefaults.FocusProgressDurationMillis,
                easing = ChronosMotionDefaults.MaterialStandardEasing
            )
        }

    /** State changes (e.g. accent colors) that should match the navigation duration. */
    fun <T> stateChange(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) {
            snap()
        } else {
            tween(
                durationMillis = ChronosMotionDefaults.DefaultDurationMillis,
                easing = ChronosMotionDefaults.MaterialStandardEasing
            )
        }

    /** Quick selection feedback (drawer pills, chips, segmented controls). */
    fun <T> selection(reducedMotion: Boolean): FiniteAnimationSpec<T> =
        if (reducedMotion) {
            snap()
        } else {
            tween(
                durationMillis = ChronosMotionDefaults.SelectionDurationMillis,
                easing = ChronosMotionDefaults.MaterialStandardEasing
            )
        }

    fun dialHand(): FiniteAnimationSpec<Float> =
        spring(
            dampingRatio = ChronosMotionDefaults.DialHandDampingRatio,
            stiffness = ChronosMotionDefaults.DialHandStiffness
        )

    fun pressScale(reducedMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = ChronosMotionDefaults.ChromeScaleDampingRatio,
                stiffness = ChronosMotionDefaults.ChromeScaleStiffness
            )
        }
}

private val ChronosTransitionDirection.multiplier: Int
    get() = when (this) {
        ChronosTransitionDirection.Forward -> 1
        ChronosTransitionDirection.Backward -> -1
        ChronosTransitionDirection.Neutral -> 0
    }

private val ChronosTransitionDirection.transformOrigin: TransformOrigin
    get() = when (this) {
        ChronosTransitionDirection.Forward -> TransformOrigin(1f, 0.5f)
        ChronosTransitionDirection.Backward -> TransformOrigin(0f, 0.5f)
        ChronosTransitionDirection.Neutral -> TransformOrigin(0.5f, 0.5f)
    }
