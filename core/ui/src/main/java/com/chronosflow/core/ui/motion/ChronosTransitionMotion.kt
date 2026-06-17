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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.absoluteValue
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
    // iOS-style spring slide for primary tab switches — a visible directional glide.
    const val PrimaryTabSlideFraction = 0.16f
    // Smooth, lightly bouncy spring (~0.72 damping → a few % overshoot) that still reads as fast.
    const val PrimaryTabSpringDampingRatio = 0.72f
    const val PrimaryTabSpringStiffness = 480f
    // Wider scale range eliminates the flat fade that caused the white-flash percept
    const val SharedAxisEnterScale = 0.93f
    const val SharedAxisExitScale = 1.07f
    const val PrimaryTabScale = 1f
    // Enter alpha starts early so route transitions never fall through to the app/window backdrop.
    const val EnterFadeDelayFraction = 0.12f
    const val ExitFadeDurationFraction = 1f
    // Leaving page's opacity at a full predictive-back peek is (1 - this); shared by every
    // in-shell back gesture via chronosBackPeek.
    const val BackPeekFadeFraction = 0.6f
    const val ChromeScaleDampingRatio = 0.86f
    const val ChromeScaleStiffness = 900f
    // Quick-add FAB +/× icon rotation — snappy spring with a small playful overshoot,
    // so the toggle bounces in the same iOS idiom as the page/nav-pill springs.
    const val QuickAddSpringDampingRatio = 0.6f
    const val QuickAddSpringStiffness = 750f
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

/**
 * Single source of truth for ChronosFlow's cross-section navigation motion — the Material
 * shared-axis used by every full-screen destination switch, whether it is a Navigation 3
 * NavDisplay route or an in-shell page shown with AnimatedVisibility. Both paths must build
 * their transition from this so the forward push, the pop, and the predictive-back preview stay
 * identical and can never drift apart.
 */
fun chronosCrossSectionTransitionSet(
    direction: ChronosTransitionDirection,
    reducedMotion: Boolean
): ChronosTransitionSet {
    val config = if (reducedMotion) {
        ChronosMotionDefaults.Navigation.reducedMotion()
    } else {
        ChronosMotionDefaults.Navigation
    }
    return ChronosTransitionFactory.materialSharedAxis(
        durationMillis = config.durationMillis,
        easing = config.easing,
        direction = direction,
        slideFraction = config.slideFraction,
        enterScale = config.enterScale,
        exitScale = config.exitScale
    )
}

/**
 * Single source of truth for the manual predictive-back PEEK transform, applied inside a
 * graphicsLayer block to in-shell destinations whose back gesture is not driven by NavDisplay's
 * own predictive animation (the sidebar pages and the primary tabs). It mirrors the shared-axis
 * pop exit — a FIXED +X slide (never mirrored by swipe edge), a grow toward
 * [ChronosMotionDefaults.SharedAxisExitScale], and a fade — so the gesture is identical to the
 * route pop no matter which screen edge the swipe starts from. Only the magnitude of
 * [backProgress] is used; its sign is intentionally ignored.
 */
fun GraphicsLayerScope.chronosBackPeek(backProgress: Float, reducedMotion: Boolean) {
    val progress = backProgress.absoluteValue
    val slideFraction = if (reducedMotion) 0f else ChronosMotionDefaults.SharedAxisSlideFraction
    val peekScale = if (reducedMotion) 1f else ChronosMotionDefaults.SharedAxisExitScale
    translationX = size.width * slideFraction * progress
    val peek = 1f + (peekScale - 1f) * progress
    scaleX = peek
    scaleY = peek
    alpha = 1f - progress * ChronosMotionDefaults.BackPeekFadeFraction
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
            spring(
                dampingRatio = ChronosMotionDefaults.QuickAddSpringDampingRatio,
                stiffness = ChronosMotionDefaults.QuickAddSpringStiffness
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

    /**
     * Bouncy glide for the bottom-nav selection pill — mirrors the primary-tab spring so the
     * pill and the page content it accompanies settle with the same iOS-style motion.
     */
    fun navIndicator(reducedMotion: Boolean): FiniteAnimationSpec<Float> =
        if (reducedMotion) {
            snap()
        } else {
            spring(
                dampingRatio = ChronosMotionDefaults.PrimaryTabSpringDampingRatio,
                stiffness = ChronosMotionDefaults.PrimaryTabSpringStiffness
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
