package com.chronosflow.navigation

import com.chronosflow.core.ui.motion.ChronosMotionConfig
import com.chronosflow.core.ui.motion.ChronosMotionDefaults
import com.chronosflow.core.ui.motion.ChronosTransitionDirection
import com.chronosflow.core.ui.motion.ChronosTransitionFactory
import com.chronosflow.core.ui.motion.ChronosTransitionSet

internal enum class ChronosNavigationOperation {
    Push,
    Pop
}

internal enum class ChronosRouteTransitionKind {
    None,
    ShellPeerFade,
    MaterialSharedAxis
}

internal data class ChronosRouteTransitionSelection(
    val kind: ChronosRouteTransitionKind,
    val direction: ChronosTransitionDirection,
    val motionConfig: ChronosMotionConfig
)

internal fun chronosNavigationMotionConfig(reducedMotion: Boolean): ChronosMotionConfig =
    if (reducedMotion) {
        ChronosMotionDefaults.Navigation.reducedMotion()
    } else {
        ChronosMotionDefaults.Navigation
    }

internal fun chronosRouteTransitionSelection(
    from: String?,
    to: String?,
    operation: ChronosNavigationOperation,
    reducedMotion: Boolean
): ChronosRouteTransitionSelection {
    val config = chronosNavigationMotionConfig(reducedMotion)
    return when {
        isWithinDayRoute(from, to) -> ChronosRouteTransitionSelection(
            kind = ChronosRouteTransitionKind.None,
            direction = ChronosTransitionDirection.Neutral,
            motionConfig = config
        )
        operation == ChronosNavigationOperation.Push && isShellPeerNavigation(from, to) ->
            ChronosRouteTransitionSelection(
                kind = ChronosRouteTransitionKind.ShellPeerFade,
                direction = ChronosTransitionDirection.Neutral,
                motionConfig = config
            )
        else -> ChronosRouteTransitionSelection(
            kind = ChronosRouteTransitionKind.MaterialSharedAxis,
            direction = if (operation == ChronosNavigationOperation.Pop) {
                ChronosTransitionDirection.Backward
            } else {
                ChronosTransitionDirection.Forward
            },
            motionConfig = config
        )
    }
}

internal fun chronosRouteTransition(
    from: String?,
    to: String?,
    operation: ChronosNavigationOperation,
    reducedMotion: Boolean
): ChronosTransitionSet {
    val selection = chronosRouteTransitionSelection(
        from = from,
        to = to,
        operation = operation,
        reducedMotion = reducedMotion
    )
    val config = selection.motionConfig
    return when (selection.kind) {
        ChronosRouteTransitionKind.None -> ChronosTransitionFactory.none()
        ChronosRouteTransitionKind.ShellPeerFade -> ChronosTransitionFactory.fadeScale(
            durationMillis = config.durationMillis,
            easing = config.easing,
            direction = selection.direction,
            enterScale = 1f,
            exitScale = 1f
        )
        ChronosRouteTransitionKind.MaterialSharedAxis -> ChronosTransitionFactory.materialSharedAxis(
            durationMillis = config.durationMillis,
            easing = config.easing,
            direction = selection.direction,
            slideFraction = config.slideFraction,
            enterScale = config.enterScale,
            exitScale = config.exitScale
        )
    }
}
