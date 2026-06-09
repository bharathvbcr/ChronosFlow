package com.chronosflow.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MaterialDefaults = Typography()

/**
 * ChronosFlow type identity: semibold, slightly tight display/headline/title styles
 * (an iOS "SF Pro Display" feel on the system font) over Material's metrics, so
 * dynamic type and per-OEM fonts keep working. Body and label styles stay Material.
 */
val ChronosTypography = Typography(
    displayLarge = MaterialDefaults.displayLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = MaterialDefaults.displayMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp
    ),
    displaySmall = MaterialDefaults.displaySmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    headlineLarge = MaterialDefaults.headlineLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = MaterialDefaults.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = MaterialDefaults.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = MaterialDefaults.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = MaterialDefaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = MaterialDefaults.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = MaterialDefaults.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = MaterialDefaults.labelMedium.copy(fontWeight = FontWeight.Medium)
)
