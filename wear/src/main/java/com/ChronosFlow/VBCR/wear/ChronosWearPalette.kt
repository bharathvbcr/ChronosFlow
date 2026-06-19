package com.ChronosFlow.VBCR.wear

/**
 * The watch's brand teal as ARGB ints — the single source shared by the Compose theme
 * ([com.ChronosFlow.VBCR.wear.presentation.ChronosWearTheme]) and the protolayout tiles, which
 * cannot read MaterialTheme. Change it here and both surfaces stay in step.
 *
 * The value mirrors the phone's `ChronosColors.BrightTeal` (core:ui DesignTokens.kt, the
 * dark-mode secondary) so the watch reads as the same product. Copied, not referenced:
 * the wear module deliberately depends only on core:domain.
 */
internal object ChronosWearPalette {
    val PRIMARY = 0xFF75D6D2.toInt()
}
