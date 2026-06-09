package com.chronosflow.core.ui.shell

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Bottom clearance for compact shell chrome (pill + FAB + margin), excluding system nav bar inset. */
val ChronosCompactShellBottomClearance = 108.dp

@Stable
class ChronosShellOverlayController {
    private var suppressionTags by mutableStateOf(setOf<String>())

    val suppressBottomChrome: Boolean
        get() = suppressionTags.isNotEmpty()

    fun setSuppressed(tag: String, suppressed: Boolean) {
        suppressionTags = if (suppressed) {
            suppressionTags + tag
        } else {
            suppressionTags - tag
        }
    }
}

val LocalChronosShellOverlayController = staticCompositionLocalOf<ChronosShellOverlayController?> { null }

val LocalChronosShellBottomInset = staticCompositionLocalOf { 0.dp }

@Composable
fun ChronosShellChromeSuppression(
    tag: String,
    suppressed: Boolean
) {
    val controller = LocalChronosShellOverlayController.current ?: return
    DisposableEffect(tag, suppressed, controller) {
        controller.setSuppressed(tag, suppressed)
        onDispose {
            controller.setSuppressed(tag, false)
        }
    }
}

@Composable
fun ChronosSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    extraBottomInset: Dp = 0.dp
) {
    val shellBottomInset = LocalChronosShellBottomInset.current
    val bottomInset = shellBottomInset + extraBottomInset
    SnackbarHost(
        hostState = hostState,
        modifier = if (bottomInset > 0.dp) {
            modifier.padding(bottom = bottomInset)
        } else {
            modifier
        }
    )
}

@Composable
fun Modifier.chronosSheetBottomInsets(): Modifier {
    return navigationBarsPadding()
}

@Composable
fun Modifier.chronosShellBottomPadding(): Modifier {
    val shellBottomInset = LocalChronosShellBottomInset.current
    return if (shellBottomInset > 0.dp) {
        padding(bottom = shellBottomInset)
    } else {
        this
    }
}
