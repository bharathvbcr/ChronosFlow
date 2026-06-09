package com.chronosflow.core.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import com.chronosflow.core.ui.settings.ChronosBackdropTheme
import com.chronosflow.core.ui.theme.ChronosColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun ChronosBackdrop(
    modifier: Modifier = Modifier,
    theme: ChronosBackdropTheme = ChronosBackdropTheme.Default,
    darkTheme: Boolean = false,
    reduceMotionEnabled: Boolean = false
) {
    when (theme) {
        ChronosBackdropTheme.LIQUID -> LiquidBackdrop(
            modifier = modifier,
            darkTheme = darkTheme,
            reduceMotionEnabled = reduceMotionEnabled
        )
        ChronosBackdropTheme.SMOKE -> SmokeBackdrop(
            modifier = modifier,
            darkTheme = darkTheme,
            reduceMotionEnabled = reduceMotionEnabled
        )
        ChronosBackdropTheme.WATER_DROPS -> WaterDropBackdrop(
            modifier = modifier,
            darkTheme = darkTheme,
            reduceMotionEnabled = reduceMotionEnabled
        )
        ChronosBackdropTheme.AURORA -> AuroraBackdrop(
            modifier = modifier,
            darkTheme = darkTheme,
            reduceMotionEnabled = reduceMotionEnabled
        )
        ChronosBackdropTheme.SUNSET_GLOW -> SunsetGlowBackdrop(
            modifier = modifier,
            darkTheme = darkTheme,
            reduceMotionEnabled = reduceMotionEnabled
        )
        ChronosBackdropTheme.NEBULA -> NebulaBackdrop(
            modifier = modifier,
            darkTheme = darkTheme,
            reduceMotionEnabled = reduceMotionEnabled
        )
    }
}

/**
 * A global ambient backdrop that features morphing liquid blobs.
 * Used to provide the foundational "liquid" feel for the Hybrid Liquid Glass aesthetic.
 *
 * @param darkTheme Whether to use dark theme alphas for the blobs.
 * @param reduceMotionEnabled If true, animations are disabled and blobs are static.
 */
@Composable
fun LiquidBackdrop(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
    reduceMotionEnabled: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_backdrop")
    val animationsEnabled = !reduceMotionEnabled

    val t1 = rememberLiquidBackdropFloatState(
        infiniteTransition = infiniteTransition,
        animationsEnabled = animationsEnabled,
        staticValue = 0f,
        initialValue = 0f,
        targetValue = FullCircleRadians,
        durationMillis = 25000,
        easing = LinearEasing,
        repeatMode = RepeatMode.Restart,
        label = "t1"
    )

    val t2 = rememberLiquidBackdropFloatState(
        infiniteTransition = infiniteTransition,
        animationsEnabled = animationsEnabled,
        staticValue = 0f,
        initialValue = 0f,
        targetValue = FullCircleRadians,
        durationMillis = 35000,
        easing = LinearEasing,
        repeatMode = RepeatMode.Restart,
        label = "t2"
    )

    val morph = rememberLiquidBackdropFloatState(
        infiniteTransition = infiniteTransition,
        animationsEnabled = animationsEnabled,
        staticValue = 1f,
        initialValue = 0.9f,
        targetValue = 1.1f,
        durationMillis = 8000,
        easing = SineEaseInOut,
        repeatMode = RepeatMode.Reverse,
        label = "morph"
    )

    val violetAlpha = if (darkTheme) 0.32f else 0.16f
    val tealAlpha = if (darkTheme) 0.12f else 0.08f
    val coralAlpha = if (darkTheme) 0.14f else 0.08f

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LiquidBackdropBlobLayer(
            color = colorScheme.primary.copy(alpha = violetAlpha),
            radiusFraction = 0.85f,
            centerXFraction = {
                if (reduceMotionEnabled) {
                    0.1f
                } else {
                    0.25f + 0.2f * cos(t1.value.toDouble()).toFloat()
                }
            },
            centerYFraction = {
                if (reduceMotionEnabled) {
                    0.2f
                } else {
                    0.3f + 0.15f * sin(t1.value.toDouble() * 0.8).toFloat()
                }
            },
            scale = { morph.value }
        )
        LiquidBackdropBlobLayer(
            color = colorScheme.secondary.copy(alpha = tealAlpha),
            radiusFraction = 0.95f,
            centerXFraction = {
                if (reduceMotionEnabled) {
                    0.9f
                } else {
                    0.75f + 0.25f * sin(t2.value.toDouble()).toFloat()
                }
            },
            centerYFraction = {
                if (reduceMotionEnabled) {
                    0.8f
                } else {
                    0.7f + 0.2f * cos(t2.value.toDouble() * 0.7).toFloat()
                }
            },
            scale = { 2f - morph.value }
        )
        LiquidBackdropBlobLayer(
            color = colorScheme.tertiary.copy(alpha = coralAlpha),
            radiusFraction = 0.6f,
            centerXFraction = {
                if (reduceMotionEnabled) {
                    0.8f
                } else {
                    0.6f + 0.3f * cos(t2.value.toDouble() * 0.4).toFloat()
                }
            },
            centerYFraction = {
                if (reduceMotionEnabled) {
                    0.4f
                } else {
                    0.5f + 0.25f * sin(t1.value.toDouble() * 0.6).toFloat()
                }
            },
            scale = { morph.value }
        )
    }
}

@Composable
private fun rememberLiquidBackdropFloatState(
    infiniteTransition: InfiniteTransition,
    animationsEnabled: Boolean,
    staticValue: Float,
    initialValue: Float,
    targetValue: Float,
    durationMillis: Int,
    easing: Easing,
    repeatMode: RepeatMode,
    label: String
): State<Float> =
    if (animationsEnabled) {
        infiniteTransition.animateFloat(
            initialValue = initialValue,
            targetValue = targetValue,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = easing),
                repeatMode = repeatMode
            ),
            label = label
        )
    } else {
        remember(staticValue) { mutableFloatStateOf(staticValue) }
    }

@Composable
private fun LiquidBackdropBlobLayer(
    color: Color,
    radiusFraction: Float,
    centerXFraction: () -> Float,
    centerYFraction: () -> Float,
    scale: () -> Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = size.width * liquidBackdropLayerTranslationFraction(centerXFraction())
                translationY = size.height * liquidBackdropLayerTranslationFraction(centerYFraction())
                val layerScale = scale()
                scaleX = layerScale
                scaleY = layerScale
            }
            .drawWithCache {
                val brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.width * radiusFraction
                )
                onDrawBehind {
                    drawRect(brush)
                }
            }
    )
}

internal fun liquidBackdropLayerTranslationFraction(centerFraction: Float): Float =
    centerFraction - 0.5f

@Composable
private fun SmokeBackdrop(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    reduceMotionEnabled: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val infiniteTransition = rememberInfiniteTransition(label = "smoke_backdrop")
    val phaseState = rememberLiquidBackdropFloatState(
        infiniteTransition = infiniteTransition,
        animationsEnabled = !reduceMotionEnabled,
        staticValue = 0f,
        initialValue = 0f,
        targetValue = FullCircleRadians,
        durationMillis = 35000,
        easing = LinearEasing,
        repeatMode = RepeatMode.Restart,
        label = "smokePhase"
    )
    val phase = phaseState.value
    val smokeColors = listOf(
        colorScheme.primary.copy(alpha = if (darkTheme) 0.16f else 0.08f),
        colorScheme.secondary.copy(alpha = if (darkTheme) 0.14f else 0.07f),
        colorScheme.tertiary.copy(alpha = if (darkTheme) 0.12f else 0.06f)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    val shortestSide = min(size.width, size.height)
                    
                    SmokePuffSpecs.forEachIndexed { index, spec ->
                        val wave = sin((phase + spec.phase).toDouble()).toFloat()
                        
                        // Drift puff position dynamically
                        val x = spec.xFraction + 0.06f * sin((phase * spec.xFreq + spec.phase).toDouble()).toFloat()
                        val y = spec.yFraction + 0.04f * cos((phase * spec.yFreq + spec.phase).toDouble()).toFloat()
                        
                        val center = Offset(size.width * x, size.height * y)
                        val radius = shortestSide * spec.radiusFraction * (1f + 0.12f * wave)
                        val puffColor = smokeColors[index % smokeColors.size]
                        
                        // Draw a soft, elongated, slightly rotated smoke puff/wisp
                        rotate(degrees = spec.rotation + 12f * wave, pivot = center) {
                            scale(scaleX = 1.6f, scaleY = 0.8f, pivot = center) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(puffColor, Color.Transparent),
                                        center = center,
                                        radius = radius
                                    ),
                                    radius = radius,
                                    center = center
                                )
                            }
                        }
                    }
                }
            }
    )
}

@Composable
private fun WaterDropBackdrop(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    reduceMotionEnabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "water_drop_backdrop")
    val phaseState = rememberLiquidBackdropFloatState(
        infiniteTransition = infiniteTransition,
        animationsEnabled = !reduceMotionEnabled,
        staticValue = 0f,
        initialValue = 0f,
        targetValue = FullCircleRadians,
        durationMillis = 24000,
        easing = LinearEasing,
        repeatMode = RepeatMode.Restart,
        label = "waterDropPhase"
    )
    val phase = phaseState.value

    // Subtle dark shadow underneath for depth
    val shadowColor = Color.Black.copy(alpha = 0.12f)
    // Refractive light color (bright translucent center)
    val refractionColor = Color.White.copy(alpha = if (darkTheme) 0.18f else 0.28f)
    // Droplet rim color
    val dropRimColor = (if (darkTheme) Color.White else Color.Black).copy(alpha = if (darkTheme) 0.15f else 0.20f)
    // Bright white highlight reflection
    val highlightColor = Color.White.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    val shortestSide = min(size.width, size.height)

                    WaterDropSpecs.forEach { spec ->
                        // Calculate positions
                        val yProgress = if (spec.isSliding && !reduceMotionEnabled) {
                            (phase / FullCircleRadians) * spec.screensPerCycle
                        } else {
                            0f
                        }
                        
                        // Modulo 1.0f vertically to wrap around nicely
                        val yFraction = (spec.yFraction + yProgress) % 1.0f
                        
                        // Add organic wiggle to sliding drops
                        val wiggle = if (spec.isSliding && !reduceMotionEnabled) {
                            0.012f * sin((phase * 5f + spec.phaseOffset).toDouble()).toFloat()
                        } else {
                            0f
                        }
                        
                        val xFraction = spec.xFraction + wiggle
                        val center = Offset(size.width * xFraction, size.height * yFraction)
                        val radius = shortestSide * spec.radiusFraction

                        // Draw wet trail for sliding drops
                        if (spec.isSliding && !reduceMotionEnabled) {
                            val trailLength = size.height * 0.12f
                            
                            // Make sure trail is only drawn on screen
                            val trailStartY = yFraction * size.height - trailLength
                            val trailEndY = yFraction * size.height
                            
                            drawLine(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, refractionColor.copy(alpha = refractionColor.alpha * 0.4f)),
                                    startY = trailStartY,
                                    endY = trailEndY
                                ),
                                start = Offset(center.x, trailStartY),
                                end = Offset(center.x, trailEndY),
                                strokeWidth = radius * 0.4f,
                                cap = StrokeCap.Round
                            )
                        }

                        // Draw the 3D refractive glass droplet
                        drawGlassDroplet(
                            center = center,
                            radius = radius,
                            dropRimColor = dropRimColor,
                            highlightColor = highlightColor,
                            shadowColor = shadowColor,
                            refractionColor = refractionColor
                        )
                    }
                }
            }
    )
}

private fun DrawScope.drawGlassDroplet(
    center: Offset,
    radius: Float,
    dropRimColor: Color,
    highlightColor: Color,
    shadowColor: Color,
    refractionColor: Color
) {
    // 1. Drop shadow (cast at bottom-right)
    drawCircle(
        color = shadowColor,
        radius = radius * 1.06f,
        center = Offset(center.x + radius * 0.08f, center.y + radius * 0.08f)
    )

    // 2. Refracted highlight inside (offset gradient toward bottom-right)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(refractionColor, Color.Transparent),
            center = Offset(center.x + radius * 0.25f, center.y + radius * 0.25f),
            radius = radius * 0.95f
        ),
        radius = radius * 0.95f,
        center = center
    )

    // 3. Main glass rim (crisp dark or light contour outline)
    drawCircle(
        color = dropRimColor,
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.08f)
    )

    // 4. White light point highlight (reflection on top-left surface)
    drawCircle(
        color = highlightColor,
        radius = radius * 0.20f,
        center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)
    )
}

@Composable
private fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    reduceMotionEnabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aurora_backdrop")
    val phaseState = rememberLiquidBackdropFloatState(
        infiniteTransition = infiniteTransition,
        animationsEnabled = !reduceMotionEnabled,
        staticValue = 0f,
        initialValue = 0f,
        targetValue = FullCircleRadians,
        durationMillis = 28000,
        easing = LinearEasing,
        repeatMode = RepeatMode.Restart,
        label = "auroraPhase"
    )
    val phase = phaseState.value
    
    // Glowing Aurora colors (Vibrant Green, Cyan, Purple)
    val baseAlpha = if (darkTheme) 0.22f else 0.12f
    val bandColors = listOf(
        ChronosColors.BackdropAuroraGreen.copy(alpha = baseAlpha),      // Emerald Green
        ChronosColors.BackdropAuroraCyan.copy(alpha = baseAlpha * 0.9f), // Cyan
        ChronosColors.BackdropAuroraPurple.copy(alpha = baseAlpha * 0.8f)  // Violet/Purple
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    AuroraBandSpecs.forEachIndexed { bandIndex, spec ->
                        val color = bandColors[bandIndex % bandColors.size]
                        
                        // Draw vertical curtain streaks along the sine wave path
                        val stepX = 18f
                        var x = -50f
                        while (x < size.width + 50f) {
                            val progressX = x / size.width
                            
                            // Base wave path for the curtain
                            val waveVal = sin((phase * spec.speed + progressX * 2.0 * Math.PI + spec.phase).toDouble()).toFloat()
                            val centerY = size.height * spec.yFraction + size.height * 0.08f * waveVal
                            
                            // Modulate curtain line height dynamically to create movement/folds
                            val heightVal = 0.7f + 0.3f * sin((phase * 1.8f + progressX * 8.0 * Math.PI + spec.phase).toDouble()).toFloat()
                            val curtainHeight = size.height * spec.heightFraction * heightVal
                            
                            // Modulate individual line opacity to create vertical streaks
                            val streakVal = 0.4f + 0.6f * sin((phase * 2.5f + progressX * 24.0 * Math.PI).toDouble()).toFloat()
                            val streakColor = color.copy(alpha = color.alpha * streakVal)
                            
                            val startY = centerY - curtainHeight / 2f
                            val endY = centerY + curtainHeight / 2f
                            
                            // Vertical fade gradient brush for the curtain line
                            val brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, streakColor, Color.Transparent),
                                startY = startY,
                                endY = endY
                            )
                            
                            drawLine(
                                brush = brush,
                                start = Offset(x, startY),
                                end = Offset(x, endY),
                                strokeWidth = 14f,
                                cap = StrokeCap.Round
                            )
                            
                            x += stepX
                        }
                    }
                }
            }
    )
}

@Composable
private fun SunsetGlowBackdrop(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    reduceMotionEnabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sunset_glow_backdrop")
    val phaseState = rememberLiquidBackdropFloatState(
        infiniteTransition = infiniteTransition,
        animationsEnabled = !reduceMotionEnabled,
        staticValue = 0f,
        initialValue = 0f,
        targetValue = FullCircleRadians,
        durationMillis = 30000,
        easing = LinearEasing,
        repeatMode = RepeatMode.Restart,
        label = "sunsetPhase"
    )
    val phase = phaseState.value

    // Shifting sunset colors
    val amber = ChronosColors.BackdropSunsetAmber
    val rose = ChronosColors.BackdropSunsetRose
    val gold = ChronosColors.BackdropSunsetGold
    val indigo = ChronosColors.BackdropSunsetIndigo

    val color1 = if (darkTheme) indigo.copy(alpha = 0.25f) else amber.copy(alpha = 0.15f)
    val color2 = if (darkTheme) rose.copy(alpha = 0.20f) else gold.copy(alpha = 0.12f)
    val color3 = if (darkTheme) amber.copy(alpha = 0.15f) else rose.copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    val shortestSide = min(size.width, size.height)
                    
                    // Blob 1: Top Right Sunset light
                    val wave1 = sin(phase.toDouble()).toFloat()
                    val center1 = Offset(
                        x = size.width * (0.75f + 0.10f * cos(phase.toDouble()).toFloat()),
                        y = size.height * (0.25f + 0.08f * wave1)
                    )
                    val r1 = shortestSide * (0.7f + 0.1f * wave1)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color1, Color.Transparent),
                            center = center1,
                            radius = r1
                        ),
                        radius = r1,
                        center = center1
                    )

                    // Blob 2: Bottom Left Horizon glow
                    val wave2 = cos((phase * 0.8f).toDouble()).toFloat()
                    val center2 = Offset(
                        x = size.width * (0.25f + 0.15f * wave2),
                        y = size.height * (0.75f + 0.10f * sin((phase * 0.8f).toDouble()).toFloat())
                    )
                    val r2 = shortestSide * (0.8f + 0.12f * wave2)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color2, Color.Transparent),
                            center = center2,
                            radius = r2
                        ),
                        radius = r2,
                        center = center2
                    )

                    // Blob 3: Center ambient warm fill
                    val wave3 = sin((phase * 0.5f).toDouble()).toFloat()
                    val center3 = Offset(
                        x = size.width * (0.5f + 0.08f * wave3),
                        y = size.height * (0.5f + 0.08f * cos((phase * 0.5f).toDouble()).toFloat())
                    )
                    val r3 = shortestSide * (0.6f + 0.08f * wave3)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color3, Color.Transparent),
                            center = center3,
                            radius = r3
                        ),
                        radius = r3,
                        center = center3
                    )
                }
            }
    )
}

@Composable
private fun NebulaBackdrop(
    modifier: Modifier = Modifier,
    darkTheme: Boolean,
    reduceMotionEnabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nebula_backdrop")
    val phaseState = rememberLiquidBackdropFloatState(
        infiniteTransition = infiniteTransition,
        animationsEnabled = !reduceMotionEnabled,
        staticValue = 0f,
        initialValue = 0f,
        targetValue = FullCircleRadians,
        durationMillis = 40000,
        easing = LinearEasing,
        repeatMode = RepeatMode.Restart,
        label = "nebulaPhase"
    )
    val phase = phaseState.value

    // Cosmic Nebula colors
    val indigo = ChronosColors.BackdropNebulaIndigo
    val magenta = ChronosColors.BackdropNebulaMagenta
    val cyan = ChronosColors.BackdropNebulaCyan

    val color1 = indigo.copy(alpha = if (darkTheme) 0.28f else 0.12f)
    val color2 = magenta.copy(alpha = if (darkTheme) 0.18f else 0.08f)
    val color3 = cyan.copy(alpha = if (darkTheme) 0.15f else 0.06f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    val shortestSide = min(size.width, size.height)

                    // Nebula Cloud 1: Indigo Base
                    val center1 = Offset(
                        x = size.width * (0.3f + 0.12f * sin((phase * 0.4f).toDouble()).toFloat()),
                        y = size.height * (0.4f + 0.08f * cos((phase * 0.4f).toDouble()).toFloat())
                    )
                    val r1 = shortestSide * (0.8f + 0.1f * sin((phase * 0.3f).toDouble()).toFloat())
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color1, Color.Transparent),
                            center = center1,
                            radius = r1
                        ),
                        radius = r1,
                        center = center1
                    )

                    // Nebula Cloud 2: Magenta Glow
                    val center2 = Offset(
                        x = size.width * (0.7f + 0.15f * cos((phase * 0.5f).toDouble()).toFloat()),
                        y = size.height * (0.6f + 0.10f * sin((phase * 0.5f).toDouble()).toFloat())
                    )
                    val r2 = shortestSide * (0.7f + 0.12f * cos((phase * 0.4f).toDouble()).toFloat())
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color2, Color.Transparent),
                            center = center2,
                            radius = r2
                        ),
                        radius = r2,
                        center = center2
                    )

                    // Nebula Cloud 3: Cyan Light
                    val center3 = Offset(
                        x = size.width * (0.5f + 0.10f * sin((phase * 0.6f).toDouble()).toFloat()),
                        y = size.height * (0.25f + 0.12f * cos((phase * 0.6f).toDouble()).toFloat())
                    )
                    val r3 = shortestSide * (0.6f + 0.08f * sin((phase * 0.5f).toDouble()).toFloat())
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color3, Color.Transparent),
                            center = center3,
                            radius = r3
                        ),
                        radius = r3,
                        center = center3
                    )

                    // Draw Twinkling Stars
                    NebulaStarSpecs.forEach { star ->
                        val twinkle = if (reduceMotionEnabled) {
                            1f
                        } else {
                            0.3f + 0.7f * sin((phase * star.twinkleSpeed + star.phaseOffset).toDouble()).toFloat()
                        }
                        val starAlpha = star.baseAlpha * twinkle
                        val starColor = Color.White.copy(alpha = if (darkTheme) starAlpha else starAlpha * 0.6f)
                        val starRadius = shortestSide * star.radiusFraction
                        
                        drawCircle(
                            color = starColor,
                            radius = starRadius,
                            center = Offset(size.width * star.xFraction, size.height * star.yFraction)
                        )
                    }
                }
            }
    )
}

private data class SmokePuffSpec(
    val xFraction: Float,
    val yFraction: Float,
    val radiusFraction: Float,
    val phase: Float,
    val xFreq: Float,
    val yFreq: Float,
    val rotation: Float
)

private val SmokePuffSpecs = listOf(
    SmokePuffSpec(xFraction = 0.25f, yFraction = 0.25f, radiusFraction = 0.22f, phase = 0.0f, xFreq = 0.8f, yFreq = 0.6f, rotation = 25f),
    SmokePuffSpec(xFraction = 0.75f, yFraction = 0.35f, radiusFraction = 0.18f, phase = 1.5f, xFreq = 0.7f, yFreq = 0.9f, rotation = -35f),
    SmokePuffSpec(xFraction = 0.45f, yFraction = 0.55f, radiusFraction = 0.26f, phase = 3.0f, xFreq = 0.9f, yFreq = 0.5f, rotation = 45f),
    SmokePuffSpec(xFraction = 0.80f, yFraction = 0.75f, radiusFraction = 0.20f, phase = 4.2f, xFreq = 0.6f, yFreq = 0.8f, rotation = -15f),
    SmokePuffSpec(xFraction = 0.20f, yFraction = 0.78f, radiusFraction = 0.16f, phase = 2.1f, xFreq = 1.0f, yFreq = 0.7f, rotation = 15f)
)

private data class WaterDropSpec(
    val xFraction: Float,
    val yFraction: Float,
    val radiusFraction: Float,
    val isSliding: Boolean,
    val screensPerCycle: Int,
    val phaseOffset: Float
)

private val WaterDropSpecs = listOf(
    WaterDropSpec(xFraction = 0.15f, yFraction = 0.22f, radiusFraction = 0.024f, isSliding = false, screensPerCycle = 0, phaseOffset = 0f),
    WaterDropSpec(xFraction = 0.78f, yFraction = 0.15f, radiusFraction = 0.016f, isSliding = false, screensPerCycle = 0, phaseOffset = 0.8f),
    WaterDropSpec(xFraction = 0.85f, yFraction = 0.45f, radiusFraction = 0.030f, isSliding = false, screensPerCycle = 0, phaseOffset = 1.6f),
    WaterDropSpec(xFraction = 0.22f, yFraction = 0.65f, radiusFraction = 0.018f, isSliding = false, screensPerCycle = 0, phaseOffset = 2.4f),
    WaterDropSpec(xFraction = 0.55f, yFraction = 0.80f, radiusFraction = 0.026f, isSliding = false, screensPerCycle = 0, phaseOffset = 3.2f),
    WaterDropSpec(xFraction = 0.10f, yFraction = 0.50f, radiusFraction = 0.014f, isSliding = false, screensPerCycle = 0, phaseOffset = 1.0f),
    WaterDropSpec(xFraction = 0.90f, yFraction = 0.88f, radiusFraction = 0.020f, isSliding = false, screensPerCycle = 0, phaseOffset = 4.5f),

    WaterDropSpec(xFraction = 0.35f, yFraction = 0.08f, radiusFraction = 0.022f, isSliding = true, screensPerCycle = 1, phaseOffset = 0.0f),
    WaterDropSpec(xFraction = 0.62f, yFraction = 0.02f, radiusFraction = 0.025f, isSliding = true, screensPerCycle = 2, phaseOffset = 1.5f),
    WaterDropSpec(xFraction = 0.72f, yFraction = 0.40f, radiusFraction = 0.018f, isSliding = true, screensPerCycle = 1, phaseOffset = 3.0f),
    WaterDropSpec(xFraction = 0.48f, yFraction = 0.30f, radiusFraction = 0.021f, isSliding = true, screensPerCycle = 2, phaseOffset = 4.2f)
)

private data class AuroraBandSpec(
    val yFraction: Float,
    val heightFraction: Float,
    val speed: Float,
    val phase: Float
)

private val AuroraBandSpecs = listOf(
    AuroraBandSpec(yFraction = 0.28f, heightFraction = 0.24f, speed = 0.8f, phase = 0.0f),
    AuroraBandSpec(yFraction = 0.48f, heightFraction = 0.28f, speed = 0.6f, phase = 1.8f),
    AuroraBandSpec(yFraction = 0.68f, heightFraction = 0.22f, speed = 0.7f, phase = 3.2f)
)

private data class NebulaStarSpec(
    val xFraction: Float,
    val yFraction: Float,
    val radiusFraction: Float,
    val twinkleSpeed: Float,
    val phaseOffset: Float,
    val baseAlpha: Float
)

private val NebulaStarSpecs = listOf(
    NebulaStarSpec(0.12f, 0.15f, 0.003f, 1.8f, 0.5f, 0.9f),
    NebulaStarSpec(0.28f, 0.08f, 0.002f, 2.2f, 1.2f, 0.8f),
    NebulaStarSpec(0.85f, 0.18f, 0.0035f, 1.5f, 0.0f, 0.9f),
    NebulaStarSpec(0.68f, 0.05f, 0.0018f, 3.0f, 2.4f, 0.7f),
    NebulaStarSpec(0.45f, 0.22f, 0.0025f, 1.2f, 3.1f, 0.8f),
    NebulaStarSpec(0.92f, 0.32f, 0.003f, 2.0f, 0.8f, 0.9f),
    NebulaStarSpec(0.08f, 0.45f, 0.0022f, 2.5f, 1.7f, 0.8f),
    NebulaStarSpec(0.78f, 0.52f, 0.004f, 1.4f, 4.0f, 0.9f),
    NebulaStarSpec(0.22f, 0.60f, 0.0028f, 2.8f, 2.1f, 0.8f),
    NebulaStarSpec(0.15f, 0.82f, 0.002f, 1.9f, 3.5f, 0.7f),
    NebulaStarSpec(0.88f, 0.78f, 0.0032f, 2.4f, 0.3f, 0.9f),
    NebulaStarSpec(0.62f, 0.92f, 0.0025f, 1.7f, 1.9f, 0.8f),
    NebulaStarSpec(0.38f, 0.85f, 0.0035f, 2.1f, 2.7f, 0.9f),
    NebulaStarSpec(0.50f, 0.70f, 0.0015f, 3.2f, 0.9f, 0.6f),
    NebulaStarSpec(0.70f, 0.30f, 0.0022f, 2.6f, 1.1f, 0.8f)
)

private val SineEaseInOut = Easing { fraction ->
    ((1 - cos(Math.PI * fraction)) / 2).toFloat()
}

private val FullCircleRadians = (2f * Math.PI).toFloat()

@Preview
@Composable
private fun LiquidBackdropPreview() {
    MaterialTheme {
        LiquidBackdrop()
    }
}

@Preview
@Composable
private fun LiquidBackdropDarkPreview() {
    MaterialTheme {
        LiquidBackdrop(darkTheme = true)
    }
}
