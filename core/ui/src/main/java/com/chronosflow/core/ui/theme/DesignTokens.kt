package com.chronosflow.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ChronosColors {
    // Primary palette
    val SoftViolet = Color(0xFF7257B8)
    val DeepViolet = Color(0xFF4A2D8C)
    val LightViolet = Color(0xFFE9DDFF)
    val BrightViolet = Color(0xFFB9A5FF) // dark mode primary
    val DarkOnPrimary = Color(0xFF28105E)
    val DarkPrimaryContainer = Color(0xFF554294)
    val DarkOnPrimaryContainer = Color(0xFFF0E9FF)
    val LightOnPrimaryContainer = Color(0xFF25134D)

    // Secondary palette
    val Teal = Color(0xFF4E9A9A)
    val DeepTeal = Color(0xFF2C6B6B)
    val LightTeal = Color(0xFFBFEDEA)
    val BrightTeal = Color(0xFF75D6D2) // dark mode secondary
    val DarkOnSecondary = Color(0xFF003735)
    val LightOnSecondaryContainer = Color(0xFF00201F)

    // Accent palette
    val Coral = Color(0xFFD86F5F)
    val BrightCoral = Color(0xFFFFB4A8) // dark mode tertiary
    val Amber = Color(0xFFF5A623)
    val SageGreen = Color(0xFF7DB87D)
    val Indigo = Color(0xFF5C6BC0)

    // Neutral palette
    val MistBlue = Color(0xFFEEF5F8)
    val WarmOffWhite = Color(0xFFF6F4EF)
    val Ink = Color(0xFF15131C)
    val Night = Color(0xFF0F111A)
    val NightPanel = Color(0xFF1A1D29)
    val NightSurfaceDim = Color(0xFF2A2E3D)
    val NightOnSurface = Color(0xFFF6F2FF)
    val NightOnSurfaceVariant = Color(0xFFD5CEE3)
    val NightOutline = Color(0xFF9F98AC)
    val NightSurfaceContainer = Color(0xFF222633)
    val HighContrastDarkSurface = Color(0xFF111111)
    val HighContrastDarkSurfaceHigh = Color(0xFF1A1A1A)
    val LightSurface = Color(0xFFFFFBFE)
    val LightSurfaceVariant = Color(0xFFEDE7F3)
    val LightOnSurfaceVariant = Color(0xFF4B4658)
    val LightOutline = Color(0xFF797187)
    val HighContrastLightSurface = Color(0xFFF0F0F0)
    val HighContrastLightSurfaceHigh = Color(0xFFE8E8E8)

    // Semantic alert roles
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)

    // Glass tokens
    val GlassWhite = Color(0x8CFFFFFF)
    val GlassDark = Color(0x731A1D29)
    val GlassBorder = Color(0xA6FFFFFF)
    val GlassBorderDark = Color(0x42FFFFFF)

    // Category colors — used for block accent bars and chips
    val CategoryWork = SoftViolet
    val CategoryStudy = Teal
    val CategoryExercise = Amber
    val CategoryMeal = SageGreen
    val CategorySleep = Indigo
    val CategoryBreak = Color(0xFF9E9E9E)
    val CategoryRoutine = SageGreen
    val CategoryCalendar = Color(0xFF4A90D9)

    // Backdrop colors
    val BackdropAuroraGreen = Color(0xFF10B981)
    val BackdropAuroraCyan = Color(0xFF06B6D4)
    val BackdropAuroraPurple = Color(0xFF8B5CF6)
    val BackdropSunsetAmber = Color(0xFFFBBF24)
    val BackdropSunsetRose = Color(0xFFF43F5E)
    val BackdropSunsetGold = Color(0xFFF97316)
    val BackdropSunsetIndigo = Color(0xFF4C1D95)
    val BackdropNebulaIndigo = Color(0xFF4F46E5)
    val BackdropNebulaMagenta = Color(0xFFD946EF)
    val BackdropNebulaCyan = Color(0xFF06B6D4)
}

/** Standardized spacing scale used across all screens. */
object ChronosSpacing {
    val Micro = 4.dp
    val Small = 8.dp
    val Compact = 12.dp
    val Standard = 16.dp
    val Medium = 24.dp
    val Large = 32.dp
    val Hero = 48.dp
}

object ChronosGlassTokens {
    val BaseOpacity = 0.85f
    val QuietOpacity = 0.94f
    val ElevatedOpacity = 0.98f
    val HighRefractionBorderWidth = 1.5.dp
    val HairlineBorderWidth = 0.7.dp
    val StandardBlur = 25.dp
    val AmbientBlur = 60.dp
    val CompactRadius = 18.dp
    val StandardRadius = 24.dp
    val PanelRadius = 32.dp

    @Composable
    fun borderBrush(primary: Color) = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.45f),
            primary.copy(alpha = 0.20f),
            Color.White.copy(alpha = 0.05f),
            primary.copy(alpha = 0.12f)
        )
    )
}

/** Maps a category string to its accent color. */
fun categoryColor(category: String): Color = when (category.uppercase()) {
    "WORK" -> ChronosColors.CategoryWork
    "STUDY" -> ChronosColors.CategoryStudy
    "EXERCISE", "WORKOUT" -> ChronosColors.CategoryExercise
    "MEAL", "FOOD" -> ChronosColors.CategoryMeal
    "SLEEP" -> ChronosColors.CategorySleep
    "BREAK" -> ChronosColors.CategoryBreak
    "ROUTINE" -> ChronosColors.CategoryRoutine
    "CALENDAR" -> ChronosColors.CategoryCalendar
    else -> ChronosColors.SoftViolet
}

