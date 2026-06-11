package com.chronosflow.feature.daydial

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosflow.core.domain.planner.DialDragMode
import com.chronosflow.core.domain.planner.DialGeometry
import com.chronosflow.core.domain.planner.DialHit
import com.chronosflow.core.domain.planner.DialPoint
import com.chronosflow.core.domain.planner.DialRing
import com.chronosflow.core.ui.motion.ChronosValueAnimationFactory
import com.chronosflow.core.ui.theme.ChronosGlassTokens
import com.chronosflow.feature.daydial.DialUtils.durationToSweep
import com.chronosflow.feature.daydial.DialUtils.durationToSweepInWindow
import com.chronosflow.feature.daydial.DialUtils.minuteToAngleInWindow
import com.chronosflow.feature.daydial.DialUtils.minuteToAngle
import com.chronosflow.feature.daydial.DialUtils.offsetToMinuteInWindow
import com.chronosflow.feature.daydial.dial.ChronosDialRenderModelBuilder
import com.chronosflow.feature.daydial.dial.visibleWindowSlice
import com.chronosflow.feature.daydial.dial.visibleWindowSlices
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val OUTER_RING_RADIUS_FRACTION = 0.98f
private const val MIDDLE_RING_RADIUS_FRACTION = 0.72f
private const val INNER_RING_RADIUS_FRACTION = 0.40f
private const val DIAL_OUTER_DIAMETER_DIVISOR = 1.5f
private const val DIAL_HIT_RADIUS_DIVISOR = 3f
private const val OUTER_RING_TAP_HALO_CANVAS_FRACTION = 0.06f
private const val MIN_DIAL_RADIUS_SCALE = 0.1f
// Night band tint. A mid-tone indigo (not a black scrim) so the band stays visible on every
// surface — the near-black dark panel, a light surface, and pure-black/white high-contrast
// modes alike — while reading unmistakably as "night".
private val DialNightBandColor = Color(0xFF302A5E)

internal fun scaledDialOuterDiameter(canvasSize: Float, dialRadiusScale: Float): Float =
    canvasSize / DIAL_OUTER_DIAMETER_DIVISOR * dialRadiusScale.coerceAtLeast(MIN_DIAL_RADIUS_SCALE)

internal fun scaledDialHitRadius(canvasSize: Float, dialRadiusScale: Float): Float =
    canvasSize / DIAL_HIT_RADIUS_DIVISOR * dialRadiusScale.coerceAtLeast(MIN_DIAL_RADIUS_SCALE)

internal fun scaledOuterRingTapRadius(canvasSize: Float, dialRadiusScale: Float): Float =
    scaledDialHitRadius(canvasSize, dialRadiusScale) + canvasSize * OUTER_RING_TAP_HALO_CANVAS_FRACTION

internal fun shouldDrawRingGuides(enableThreeRingMode: Boolean, showRingGuide: Boolean): Boolean {
    return enableThreeRingMode && showRingGuide
}

internal fun shouldDrawOffWindowNowMarker(
    showNowHand: Boolean,
    isWindowed: Boolean,
    relativeMinute: Int,
    windowMinutes: Int
): Boolean = showNowHand && isWindowed && relativeMinute >= windowMinutes

internal fun shouldTriggerAddFromTap(hit: DialHit): Boolean {
    return hit is DialHit.Ring && hit.ring == DialRing.OUTER
}

internal fun shouldTreatOutsideRadiusAsOuterRingTap(
    distanceFromCenter: Float,
    dialHitRadius: Float,
    outerRingTapRadius: Float
): Boolean {
    return dialHitRadius > 0f &&
        outerRingTapRadius > dialHitRadius &&
        distanceFromCenter > dialHitRadius * OUTER_RING_RADIUS_FRACTION &&
        distanceFromCenter <= outerRingTapRadius
}

@Composable
fun ChronosDial(
    blocks: List<TimeBlockUiModel>,
    freeTimeSegments: List<TimeRangeUi>,
    currentMinute: Int,
    showNowHand: Boolean = true,
    compactMode: Boolean,
    compactWindowStart: Int,
    selectedBlockId: String?,
    activeBlockId: String? = null,
    upcomingBlockId: String? = null,
    missedBlockIds: Set<String> = emptySet(),
    hapticCue: PlannerHapticCue,
    onBlockSelected: (String) -> Unit,
    onInnerRingBlockActivated: (String) -> Unit = onBlockSelected,
    onEmptyAreaLongPress: (Int) -> Unit = {},
    onEmptyRingLongPress: (Int, DialRing) -> Unit = { minute, _ -> onEmptyAreaLongPress(minute) },
    onBlockDragStarted: (String, Int) -> Unit,
    onBlockMoved: (String, Int) -> Unit,
    onBlockMoveCommitted: (String, Int) -> Unit,
    onBlockResize: (String, Int, Int) -> Unit = { _, _, _ -> },
    onBlockResizeCommitted: (String, Int, Int) -> Unit = { _, _, _ -> },
    onDragEnd: () -> Unit = {},
    enableThreeRingMode: Boolean = true,
    showRingGuide: Boolean = true,
    glassSurfacesEnabled: Boolean = true,
    canvasInset: Dp = 16.dp,
    dialRadiusScale: Float = 1f,
    nightStartMinute: Int = 21 * 60,
    nightEndMinute: Int = 7 * 60,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isWindowed = compactMode
    val windowMinutes = if (isWindowed) 720 else 1440
    val geometry = remember { DialGeometry() }
    val effectiveDialRadiusScale = dialRadiusScale.coerceAtLeast(MIN_DIAL_RADIUS_SCALE)
    val glassGlowBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.15f),
                Color.Transparent
            )
        )
    }

    val colorScheme = MaterialTheme.colorScheme
    val renderModel = remember(
        blocks,
        freeTimeSegments,
        selectedBlockId,
        activeBlockId,
        upcomingBlockId,
        missedBlockIds,
        compactMode,
        compactWindowStart,
        nightStartMinute,
        nightEndMinute
    ) {
        val conflictIds = conflictingBlockIds(blocks)
        ChronosDialRenderModelBuilder.build(
            blocks = blocks,
            freeTimeSegments = freeTimeSegments,
            selectedBlockId = selectedBlockId,
            activeBlockId = activeBlockId,
            upcomingBlockId = upcomingBlockId,
            missedBlockIds = missedBlockIds,
            compactMode = compactMode,
            compactWindowStart = compactWindowStart,
            conflictBlockIds = conflictIds,
            blockingConflictIds = blocks
                .filter { it.id in conflictIds && (it.isLocked || it.isProtected) }
                .map { it.id }
                .toSet(),
            nightStartMinute = nightStartMinute,
            nightEndMinute = nightEndMinute
        )
    }
    val hourLabelColor = colorScheme.onSurfaceVariant.copy(alpha = 0.82f).toArgb()
    val hourLabelTextSize = with(density) { 11.sp.toPx() }
    val hourLabelPaint = remember(hourLabelColor, hourLabelTextSize) {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
    }.apply {
        color = hourLabelColor
        textSize = hourLabelTextSize
    }
    val minorHourLabelColor = colorScheme.onSurfaceVariant.copy(alpha = 0.58f).toArgb()
    val minorHourLabelTextSize = with(density) { 9.sp.toPx() }
    val minorHourLabelPaint = remember(minorHourLabelColor, minorHourLabelTextSize) {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }.apply {
        color = minorHourLabelColor
        textSize = minorHourLabelTextSize
    }

    val liveHandMinute by produceState(initialValue = currentMinute.toFloat(), currentMinute, showNowHand) {
        if (!showNowHand) {
            value = currentMinute.toFloat()
            return@produceState
        }
        while (true) {
            value = deviceMinuteWithSeconds()
            delay(1_000L)
        }
    }

    val animatedMinute by animateFloatAsState(
        targetValue = liveHandMinute,
        animationSpec = ChronosValueAnimationFactory.dialHand(),
        label = "HandMovement"
    )

    var dragMode by remember { mutableStateOf<DialDragMode?>(null) }
    var draggingBlockId by remember { mutableStateOf<String?>(null) }
    var dragStartMinute by remember { mutableStateOf(0) }
    var blockStartMinuteAtDragStart by remember { mutableStateOf(0) }
    var blockDurationAtDragStart by remember { mutableStateOf(0) }
    var lastSnappedMinute by remember { mutableStateOf(-1) }
    var lastResizeDuration by remember { mutableStateOf(0) }
    var lastResizeStartMinute by remember { mutableStateOf(0) }
    var dragIndicatorMinute by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(hapticCue) {
        when (hapticCue) {
            PlannerHapticCue.SNAP -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            PlannerHapticCue.CONFLICT_BOUNDARY -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            PlannerHapticCue.LOCKED_COLLISION -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
            PlannerHapticCue.SUCCESSFUL_DROP -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            else -> {}
        }
    }

    fun visibleStartInWindow(start: Int): Int {
        if (!isWindowed) return start
        val relative = ((start - compactWindowStart + 1440) % 1440)
        return relative
    }

    fun relativeToScreenMinute(startMinute: Int): Int {
        if (!isWindowed) return startMinute
        return (startMinute - compactWindowStart + 1440) % 1440
    }

    Canvas(
        modifier = modifier
            .drawWithCache {
                val cachedCenter = Offset(size.width / 2f, size.height / 2f)
                val cachedOuterRadius = scaledDialOuterDiameter(size.minDimension, effectiveDialRadiusScale)
                val cachedRingStroke = size.minDimension * 0.1f
                val cachedRingSize = Size(cachedOuterRadius, cachedOuterRadius)
                val cachedRingTopLeft = Offset(
                    x = cachedCenter.x - cachedOuterRadius / 2f,
                    y = cachedCenter.y - cachedOuterRadius / 2f
                )
                val ringAlpha = if (glassSurfacesEnabled) ChronosGlassTokens.BaseOpacity else 1f
                // Flat track: the night band (drawn in the Canvas body) is now the only
                // intentional dark region, so the ring base stays uniform and non-directional.
                val dialTrackColor = colorScheme.surface.copy(alpha = ringAlpha)
                val dialStroke = Stroke(width = cachedRingStroke)
                val dialOutlineStroke = Stroke(width = 0.5.dp.toPx())
                val guideRingStroke = Stroke(width = cachedRingStroke * 0.1f)
                onDrawBehind {
                    val solidDial = !glassSurfacesEnabled
                    drawCircle(
                        color = dialTrackColor,
                        radius = cachedOuterRadius / 2f,
                        center = cachedCenter,
                        style = dialStroke
                    )
                    drawCircle(
                        color = colorScheme.outline.copy(alpha = 0.15f),
                        radius = cachedOuterRadius / 2f + cachedRingStroke / 2f + 1.dp.toPx(),
                        center = cachedCenter,
                        style = dialOutlineStroke
                    )
                    renderModel.hourTicks.forEach { tick ->
                        val rad = Math.toRadians(tick.angle.toDouble())
                        val markerPadding = if (tick.isMajor) 0.dp.toPx() else 3.dp.toPx()
                        val markerStart = Offset(
                            x = cachedCenter.x + cos(rad).toFloat() * (cachedOuterRadius / 2f - cachedRingStroke / 2f + markerPadding),
                            y = cachedCenter.y + sin(rad).toFloat() * (cachedOuterRadius / 2f - cachedRingStroke / 2f + markerPadding)
                        )
                        val markerEnd = Offset(
                            x = cachedCenter.x + cos(rad).toFloat() * (cachedOuterRadius / 2f + cachedRingStroke / 2f - markerPadding),
                            y = cachedCenter.y + sin(rad).toFloat() * (cachedOuterRadius / 2f + cachedRingStroke / 2f - markerPadding)
                        )
                        val tickAlpha = if (solidDial) {
                            if (tick.isMajor) 0.9f else 0.65f
                        } else {
                            if (tick.isMajor) 0.55f else 0.3f
                        }
                        drawLine(
                            color = colorScheme.onSurfaceVariant.copy(alpha = tickAlpha),
                            start = markerStart,
                            end = markerEnd,
                            strokeWidth = if (tick.isMajor) 1.8.dp.toPx() else 1.dp.toPx()
                        )
                    }
                    if (shouldDrawRingGuides(enableThreeRingMode, showRingGuide)) {
                        listOf(DialRing.OUTER, DialRing.MIDDLE, DialRing.INNER).forEach { ring ->
                            drawCircle(
                                color = ringGuideColor(ring, colorScheme).copy(alpha = 0.16f),
                                radius = ringRadius(cachedOuterRadius / 2f, ring),
                                center = cachedCenter,
                                style = guideRingStroke
                            )
                        }
                    }
                }
            }
            .aspectRatio(1f)
            .padding(canvasInset)
            .pointerInput(blocks, compactMode, compactWindowStart, effectiveDialRadiusScale) {
                detectTapGestures(
                    onTap = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val canvasSize = minOf(size.width, size.height).toFloat()
                        val hitRadius = scaledDialHitRadius(
                            canvasSize,
                            effectiveDialRadiusScale
                        )
                        val outerRingTapRadius = scaledOuterRingTapRadius(canvasSize, effectiveDialRadiusScale)
                        when (val hit = dialHitForOffset(
                            geometry = geometry,
                            offset = offset,
                            center = center,
                            maxRadius = hitRadius,
                            outerRingTapRadius = outerRingTapRadius,
                            blocks = blocks,
                            selectedBlockId = selectedBlockId,
                            compactMode = isWindowed,
                            compactWindowStart = compactWindowStart,
                            windowMinutes = windowMinutes,
                            enableThreeRingMode = enableThreeRingMode
                        )) {
                            is DialHit.BlockMove -> {
                                val block = blocks.firstOrNull { it.id == hit.blockId }
                                if (enableThreeRingMode && block?.isInnerRingActionBlock == true) {
                                    onInnerRingBlockActivated(hit.blockId)
                                } else {
                                    onBlockSelected(hit.blockId)
                                }
                            }
                            is DialHit.ResizeStart -> onBlockSelected(hit.blockId)
                            is DialHit.ResizeEnd -> onBlockSelected(hit.blockId)
                            is DialHit.Ring -> if (shouldTriggerAddFromTap(hit)) {
                                onEmptyRingLongPress(hit.minute, hit.ring)
                            }
                            DialHit.Outside -> Unit
                        }
                    },
                    onLongPress = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val canvasSize = minOf(size.width, size.height).toFloat()
                        val hitRadius = scaledDialHitRadius(
                            canvasSize,
                            effectiveDialRadiusScale
                        )
                        val outerRingTapRadius = scaledOuterRingTapRadius(canvasSize, effectiveDialRadiusScale)
                        val hit = dialHitForOffset(
                            geometry = geometry,
                            offset = offset,
                            center = center,
                            maxRadius = hitRadius,
                            outerRingTapRadius = outerRingTapRadius,
                            blocks = blocks,
                            selectedBlockId = selectedBlockId,
                            compactMode = isWindowed,
                            compactWindowStart = compactWindowStart,
                            windowMinutes = windowMinutes,
                            enableThreeRingMode = enableThreeRingMode
                        )
                        if (hit is DialHit.Ring) {
                            onEmptyRingLongPress(hit.minute, hit.ring)
                        }
                    }
                )
            }
            .pointerInput(blocks, compactMode, compactWindowStart, effectiveDialRadiusScale) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val canvasSize = minOf(size.width, size.height).toFloat()
                        val hitRadius = scaledDialHitRadius(
                            canvasSize,
                            effectiveDialRadiusScale
                        )
                        val outerRingTapRadius = scaledOuterRingTapRadius(canvasSize, effectiveDialRadiusScale)
                        when (val hit = dialHitForOffset(
                            geometry = geometry,
                            offset = offset,
                            center = center,
                            maxRadius = hitRadius,
                            outerRingTapRadius = outerRingTapRadius,
                            blocks = blocks,
                            selectedBlockId = selectedBlockId,
                            compactMode = isWindowed,
                            compactWindowStart = compactWindowStart,
                            windowMinutes = windowMinutes,
                            enableThreeRingMode = enableThreeRingMode
                        )) {
                            is DialHit.BlockMove -> {
                                val block = blocks.firstOrNull { it.id == hit.blockId } ?: return@detectDragGestures
                                dragMode = DialDragMode.Move(block.id, hit.minute)
                                draggingBlockId = block.id
                                dragStartMinute = hit.minute
                                blockStartMinuteAtDragStart = block.startMinuteOfDay
                                blockDurationAtDragStart = block.durationMinutes
                                lastSnappedMinute = block.startMinuteOfDay
                                dragIndicatorMinute = block.startMinuteOfDay
                                onBlockSelected(block.id)
                                onBlockDragStarted(block.id, block.startMinuteOfDay)
                            }
                            is DialHit.ResizeStart -> {
                                val block = blocks.firstOrNull { it.id == hit.blockId } ?: return@detectDragGestures
                                dragMode = DialDragMode.ResizeStart(block.id)
                                draggingBlockId = block.id
                                dragStartMinute = hit.minute
                                blockStartMinuteAtDragStart = block.startMinuteOfDay
                                blockDurationAtDragStart = block.durationMinutes
                                lastResizeDuration = block.durationMinutes
                                lastResizeStartMinute = block.startMinuteOfDay
                                dragIndicatorMinute = block.startMinuteOfDay
                                onBlockSelected(block.id)
                            }
                            is DialHit.ResizeEnd -> {
                                val block = blocks.firstOrNull { it.id == hit.blockId } ?: return@detectDragGestures
                                dragMode = DialDragMode.ResizeEnd(block.id)
                                draggingBlockId = block.id
                                dragStartMinute = hit.minute
                                blockStartMinuteAtDragStart = block.startMinuteOfDay
                                blockDurationAtDragStart = block.durationMinutes
                                lastResizeDuration = block.durationMinutes
                                lastResizeStartMinute = block.startMinuteOfDay
                                dragIndicatorMinute = block.startMinuteOfDay + block.durationMinutes
                                onBlockSelected(block.id)
                            }
                            else -> Unit
                        }
                    },
                    onDrag = { change, _ ->
                        val mode = dragMode ?: return@detectDragGestures
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val hitRadius = scaledDialHitRadius(
                            minOf(size.width, size.height).toFloat(),
                            effectiveDialRadiusScale
                        )
                        val currentMinuteDrag = minuteForOffset(
                            geometry = geometry,
                            offset = change.position,
                            center = center,
                            maxRadius = hitRadius,
                            compactMode = isWindowed,
                            compactWindowStart = compactWindowStart,
                            windowMinutes = windowMinutes
                        )
                        if (currentMinuteDrag == null) {
                            return@detectDragGestures
                        }
                        when (mode) {
                            is DialDragMode.Move -> {
                                val minuteDiff = circularMinuteDelta(dragStartMinute, currentMinuteDrag)
                                val projected = blockStartMinuteAtDragStart + minuteDiff
                                val snappedMinute = geometry.snap(projected, 15)
                                if (snappedMinute != lastSnappedMinute) {
                                    onBlockMoved(mode.blockId, snappedMinute)
                                    lastSnappedMinute = snappedMinute
                                }
                                dragIndicatorMinute = snappedMinute
                            }
                            is DialDragMode.ResizeStart -> {
                                val fixedEnd = blockStartMinuteAtDragStart + blockDurationAtDragStart
                                val snappedStart = geometry.snap(currentMinuteDrag, 15)
                                val newDuration = circularDuration(snappedStart, fixedEnd).coerceIn(10, 240)
                                if (snappedStart != lastResizeStartMinute || newDuration != lastResizeDuration) {
                                    onBlockResize(mode.blockId, snappedStart, newDuration)
                                    lastResizeStartMinute = snappedStart
                                    lastResizeDuration = newDuration
                                }
                                dragIndicatorMinute = snappedStart
                            }
                            is DialDragMode.ResizeEnd -> {
                                val snappedEnd = geometry.snap(currentMinuteDrag, 15)
                                val newDuration = circularDuration(blockStartMinuteAtDragStart, snappedEnd).coerceIn(10, 240)
                                if (newDuration != lastResizeDuration) {
                                    onBlockResize(mode.blockId, blockStartMinuteAtDragStart, newDuration)
                                    lastResizeStartMinute = blockStartMinuteAtDragStart
                                    lastResizeDuration = newDuration
                                }
                                dragIndicatorMinute = snappedEnd
                            }
                            is DialDragMode.Create -> Unit
                        }
                    },
                    onDragEnd = {
                        when (val mode = dragMode) {
                            is DialDragMode.Move -> draggingBlockId?.let { id ->
                                onBlockMoveCommitted(id, lastSnappedMinute)
                            }
                            is DialDragMode.ResizeStart,
                            is DialDragMode.ResizeEnd -> draggingBlockId?.let { id ->
                                onBlockResizeCommitted(id, lastResizeStartMinute, lastResizeDuration)
                            }
                            else -> Unit
                        }
                        onDragEnd()
                        draggingBlockId = null
                        dragMode = null
                        dragIndicatorMinute = null
                    },
                    onDragCancel = {
                        onDragEnd()
                        draggingBlockId = null
                        dragMode = null
                        dragIndicatorMinute = null
                    }
                )
            }
    ) {
        val canvasSize = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = scaledDialOuterDiameter(canvasSize, effectiveDialRadiusScale)
        val ringStroke = canvasSize * 0.1f
        val ringSize = Size(outerRadius, outerRadius)
        val ringTopLeft = Offset(
            x = center.x - outerRadius / 2f,
            y = center.y - outerRadius / 2f
        )

        // Night band: an indigo tint over the track for the sleep window. Drawn first so it
        // reads as part of the dial face, beneath blocks and the now-hand. Butt caps keep the
        // window edges crisp at the exact start/end times. Glass mode runs lighter so the band
        // sits behind translucent blocks; the opaque (incl. high-contrast) path runs stronger.
        val nightBandColor = DialNightBandColor.copy(alpha = if (glassSurfacesEnabled) 0.45f else 0.7f)
        renderModel.nightArcs.forEach { night ->
            if (night.startAngle.isNaN() || night.sweepAngle <= 0.1f) return@forEach
            drawArc(
                color = nightBandColor,
                startAngle = night.startAngle,
                sweepAngle = night.sweepAngle,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Butt)
            )
        }

        if (glassSurfacesEnabled) {
            drawArc(
                brush = glassGlowBrush,
                startAngle = -150f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )
        }

        // Dashed stroke reads as "open space" at a glance, distinct from solid blocks.
        val freeTimeDash = PathEffect.dashPathEffect(
            floatArrayOf(4.dp.toPx(), 7.dp.toPx())
        )
        renderModel.freeTimeArcs.forEach { free ->
            if (!free.startAngle.isNaN() && free.sweepAngle > 0.1f) {
                drawArc(
                    color = colorScheme.secondary.copy(alpha = 0.32f),
                    startAngle = free.startAngle,
                    sweepAngle = free.sweepAngle,
                    useCenter = false,
                    topLeft = ringTopLeft,
                    size = ringSize,
                    style = Stroke(
                        width = ringStroke * 0.34f,
                        cap = StrokeCap.Round,
                        pathEffect = freeTimeDash
                    )
                )
            }
        }

        renderModel.blockArcs.forEach { arc ->
            if (arc.startAngle.isNaN()) return@forEach

            val blockRingRadius = if (enableThreeRingMode) {
                ringRadius(outerRadius / 2f, arc.ring)
            } else {
                outerRadius / 2f
            }
            val blockRingStroke = if (enableThreeRingMode) ringStroke * 0.42f else ringStroke
            val blockRingSize = Size(blockRingRadius * 2f, blockRingRadius * 2f)
            val blockRingTopLeft = Offset(
                x = center.x - blockRingRadius,
                y = center.y - blockRingRadius
            )
            val arcColor = when {
                arc.isMissed -> lerp(arc.color, colorScheme.error, 0.55f)
                arc.isActive -> lerp(arc.color, colorScheme.primary, 0.18f)
                else -> arc.color
            }
            val glowAlpha = when {
                arc.isSelected -> 0.62f
                arc.isActive -> 0.46f
                arc.isUpcoming -> 0.3f
                else -> 0.26f
            }
            val coreStroke = when {
                arc.isSelected -> blockRingStroke * 0.82f
                arc.isActive -> blockRingStroke * 0.76f
                else -> blockRingStroke * 0.72f
            }

            drawArc(
                color = arcColor.copy(alpha = glowAlpha * 0.72f),
                startAngle = arc.startAngle,
                sweepAngle = arc.sweepAngle,
                useCenter = false,
                topLeft = blockRingTopLeft,
                size = blockRingSize,
                style = Stroke(width = blockRingStroke * 0.96f, cap = StrokeCap.Round)
            )

            drawArc(
                color = arcColor,
                startAngle = arc.startAngle,
                sweepAngle = arc.sweepAngle,
                useCenter = false,
                topLeft = blockRingTopLeft,
                size = blockRingSize,
                style = Stroke(width = coreStroke, cap = StrokeCap.Round)
            )

            if (arc.isUpcoming || arc.isProtected || arc.isLocked) {
                drawArc(
                    color = when {
                        arc.isLocked -> colorScheme.error.copy(alpha = 0.85f)
                        arc.isProtected -> colorScheme.tertiary.copy(alpha = 0.8f)
                        else -> colorScheme.onSurface.copy(alpha = 0.58f)
                    },
                    startAngle = arc.startAngle,
                    sweepAngle = arc.sweepAngle,
                    useCenter = false,
                    topLeft = blockRingTopLeft,
                    size = blockRingSize,
                    style = Stroke(width = blockRingStroke * 0.18f, cap = StrokeCap.Round)
                )
            }

            if (arc.isSelected) {
                drawCircle(
                    color = colorScheme.primary.copy(alpha = 0.18f),
                    radius = 15.dp.toPx(),
                    center = blockHandleOffset(
                        center,
                        blockRingRadius,
                        arc.startAngle,
                        blockRingStroke
                    )
                )
                drawCircle(
                    color = colorScheme.onSurface,
                    radius = 9.dp.toPx(),
                    center = blockHandleOffset(
                        center,
                        blockRingRadius,
                        arc.startAngle,
                        blockRingStroke
                    )
                )
                val endAngle = arc.startAngle + arc.sweepAngle
                drawCircle(
                    color = colorScheme.primary.copy(alpha = 0.18f),
                    radius = 15.dp.toPx(),
                    center = blockHandleOffset(
                        center,
                        blockRingRadius,
                        endAngle,
                        blockRingStroke
                    )
                )
                drawCircle(
                    color = colorScheme.onSurface,
                    radius = 9.dp.toPx(),
                    center = blockHandleOffset(
                        center,
                        blockRingRadius,
                        endAngle,
                        blockRingStroke
                    )
                )
            }
        }

        // Conflict overlays: dashed error arc over each clashing block so double-booked
        // time is visible at a glance without opening the block.
        if (renderModel.conflictOverlays.isNotEmpty()) {
            val conflictDash = PathEffect.dashPathEffect(
                floatArrayOf(5.dp.toPx(), 5.dp.toPx())
            )
            val conflictStroke = if (enableThreeRingMode) ringStroke * 0.42f else ringStroke
            renderModel.conflictOverlays.forEach { overlay ->
                if (overlay.startAngle.isNaN()) return@forEach
                val overlayRadius = if (enableThreeRingMode) {
                    ringRadius(outerRadius / 2f, overlay.ring)
                } else {
                    outerRadius / 2f
                }
                drawArc(
                    color = colorScheme.error.copy(alpha = if (overlay.isBlocking) 0.85f else 0.6f),
                    startAngle = overlay.startAngle,
                    sweepAngle = overlay.sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - overlayRadius, center.y - overlayRadius),
                    size = Size(overlayRadius * 2f, overlayRadius * 2f),
                    style = Stroke(
                        width = conflictStroke * 0.16f,
                        cap = StrokeCap.Round,
                        pathEffect = conflictDash
                    )
                )
            }
        }

        dragIndicatorMinute?.let { indicatorMinute ->
            val indicatorAngle = if (isWindowed) {
                minuteToAngleInWindow(visibleStartInWindow(indicatorMinute), 0, windowMinutes)
            } else {
                minuteToAngle(indicatorMinute)
            }
            val indicatorRadians = Math.toRadians(indicatorAngle.toDouble())
            val indicatorRadius = outerRadius / 2f + ringStroke / 2f
            val indicatorEnd = Offset(
                x = center.x + cos(indicatorRadians).toFloat() * indicatorRadius,
                y = center.y + sin(indicatorRadians).toFloat() * indicatorRadius
            )
            drawLine(
                color = colorScheme.primary.copy(alpha = 0.28f),
                start = center,
                end = indicatorEnd,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = colorScheme.primary.copy(alpha = 0.22f),
                radius = 10.dp.toPx(),
                center = indicatorEnd
            )
            drawCircle(
                color = colorScheme.primary,
                radius = 4.dp.toPx(),
                center = indicatorEnd
            )
        }

        val nowAngle = if (!showNowHand) {
            Float.NaN
        } else if (isWindowed) {
            minuteToAngleInWindow(visibleStartInWindow(animatedMinute.toInt()), 0, windowMinutes)
        } else {
            fractionalMinuteToAngle(animatedMinute)
        }
        if (!nowAngle.isNaN()) {
            val nowRadians = Math.toRadians(nowAngle.toDouble())
            val handLength = outerRadius / 2f + (ringStroke / 2f)
            val handEnd = Offset(
                x = center.x + cos(nowRadians).toFloat() * handLength,
                y = center.y + sin(nowRadians).toFloat() * handLength
            )

            drawLine(
                color = colorScheme.primary.copy(alpha = 0.32f),
                start = center,
                end = handEnd,
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawLine(
                color = colorScheme.primary,
                start = center,
                end = handEnd,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Tip dot anchors the current time on the ring for instant scanning.
            drawCircle(
                color = colorScheme.primary.copy(alpha = 0.25f),
                radius = 9.dp.toPx(),
                center = handEnd
            )
            drawCircle(
                color = colorScheme.primary,
                radius = 5.dp.toPx(),
                center = handEnd
            )
        } else if (shouldDrawOffWindowNowMarker(
                showNowHand = showNowHand,
                isWindowed = isWindowed,
                relativeMinute = visibleStartInWindow(animatedMinute.toInt()),
                windowMinutes = windowMinutes
            )
        ) {
            // "Now" is outside the 12h window; both window edges meet at the seam
            // (top of the dial), so a hollow marker there says "now is off-screen".
            val seamRadians = Math.toRadians(
                minuteToAngleInWindow(0, 0, windowMinutes).toDouble()
            )
            val seamRadius = outerRadius / 2f + ringStroke / 2f
            val seamCenter = Offset(
                x = center.x + cos(seamRadians).toFloat() * seamRadius,
                y = center.y + sin(seamRadians).toFloat() * seamRadius
            )
            drawCircle(
                color = colorScheme.primary.copy(alpha = 0.65f),
                radius = 5.dp.toPx(),
                center = seamCenter,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        renderModel.hourTicks.forEach { tick ->
            if (tick.angle.isNaN()) return@forEach
            tick.label?.let { label ->
                val paint = if (tick.isMajor) hourLabelPaint else minorHourLabelPaint
                val rad = Math.toRadians(tick.angle.toDouble())
                val labelRadius = outerRadius / 2f + ringStroke / 2f + 16.dp.toPx()
                val labelX = center.x + cos(rad).toFloat() * labelRadius
                val labelY = center.y + sin(rad).toFloat() * labelRadius + paint.textSize / 3f
                drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY, paint)
            }
        }

        // Center hub glow
        drawCircle(
            color = colorScheme.primary.copy(alpha = 0.3f),
            radius = 12.dp.toPx(),
            center = center
        )
        drawCircle(color = colorScheme.primary, radius = 6.dp.toPx(), center = center)
    }
}

private fun deviceMinuteWithSeconds(): Float {
    val now = LocalTime.now(ZoneId.systemDefault())
    return now.hour * 60f + now.minute + now.second / 60f
}

private fun fractionalMinuteToAngle(minute: Float): Float {
    val normalized = ((minute % 1440f) + 1440f) % 1440f
    return (normalized / 1440f) * 360f - 90f
}

private fun blockHandleOffset(
    center: Offset,
    radius: Float,
    angle: Float,
    strokeWidth: Float
): Offset {
    val radians = Math.toRadians(angle.toDouble())
    return Offset(
        x = center.x + cos(radians).toFloat() * (radius + strokeWidth / 2f),
        y = center.y + sin(radians).toFloat() * (radius + strokeWidth / 2f)
    )
}

private fun windowContainsSegment(
    segmentStart: Int,
    segmentEnd: Int,
    windowStart: Int,
    windowMinutes: Int
): Boolean {
    val startRelative = ((segmentStart - windowStart + 1440) % 1440)
    val endRelative = ((segmentEnd - windowStart + 1440) % 1440)
    return if (segmentEnd - segmentStart >= windowMinutes) {
        true
    } else if (startRelative <= endRelative) {
        startRelative < windowMinutes && endRelative <= windowMinutes
    } else {
        true
    }
}

private fun visibleDuration(
    startMinute: Int,
    endMinute: Int,
    isWindowed: Boolean,
    windowStart: Int,
    windowMinutes: Int
): Int {
    if (!isWindowed) return endMinute - startMinute
    val clippedStart = ((startMinute - windowStart + 1440) % 1440)
    val clippedEnd = ((endMinute - windowStart + 1440) % 1440)
    return if (clippedStart <= clippedEnd) {
        (clippedEnd - clippedStart).coerceAtLeast(0)
    } else {
        windowMinutes - clippedStart
    }
}

private fun findBlockAtMinute(
    blocks: List<TimeBlockUiModel>,
    minute: Int,
    ring: DialRing,
    compactMode: Boolean,
    compactWindowStart: Int,
    windowMinutes: Int,
    enableThreeRingMode: Boolean
): TimeBlockUiModel? {
    return blocks.find { block ->
        if (enableThreeRingMode && ringForBlock(block) != ring) return@find false
        if (compactMode && windowMinutes < 1440) {
            val normalizedMinute = ((minute - compactWindowStart + 1440) % 1440)
            if (normalizedMinute !in 0 until windowMinutes) return@find false
            val visibleSlices = visibleWindowSlices(
                startMinute = block.startMinuteOfDay,
                durationMinutes = block.durationMinutes,
                windowStart = compactWindowStart,
                windowMinutes = windowMinutes
            )
            return@find visibleSlices.any { visibleSlice ->
                normalizedMinute in visibleSlice.relativeStart until
                    (visibleSlice.relativeStart + visibleSlice.visibleDuration)
            }
        }
        val endFull = block.startMinuteOfDay + block.durationMinutes
        if (endFull <= 1440) minute in block.startMinuteOfDay until endFull else
            (minute >= block.startMinuteOfDay || minute < (endFull % 1440))
    }
}

internal fun dialHitForOffset(
    geometry: DialGeometry,
    offset: Offset,
    center: Offset,
    maxRadius: Float,
    outerRingTapRadius: Float,
    blocks: List<TimeBlockUiModel>,
    selectedBlockId: String?,
    compactMode: Boolean,
    compactWindowStart: Int,
    windowMinutes: Int,
    enableThreeRingMode: Boolean
): DialHit {
    val ringHit = geometry.hitTest(
        point = DialPoint(offset.x, offset.y),
        center = DialPoint(center.x, center.y),
        maxRadius = maxRadius
    )
    val distanceFromCenter = distanceFromCenter(offset, center)
    val hitRing = when {
        ringHit is DialHit.Ring && ringHit.ring != DialRing.CENTER && ringHit.ring != DialRing.OUTSIDE -> {
            ringHit.ring
        }
        ringHit is DialHit.Outside && shouldTreatOutsideRadiusAsOuterRingTap(
            distanceFromCenter = distanceFromCenter,
            dialHitRadius = maxRadius,
            outerRingTapRadius = outerRingTapRadius
        ) -> DialRing.OUTER
        else -> return DialHit.Outside
    }
    val minute = if (compactMode && windowMinutes < 1440) {
        offsetToMinuteInWindow(offset, center, compactWindowStart, windowMinutes).takeIf { it >= 0 }
    } else {
        val rawMinute = when (ringHit) {
            is DialHit.Ring -> ringHit.minute
            DialHit.Outside -> geometry.angleToMinute(angleDegreesForOffset(offset, center))
            else -> return DialHit.Outside
        }
        windowMinuteOrNull(rawMinute, compactMode, compactWindowStart, windowMinutes)
    } ?: return DialHit.Outside
    val selectedBlock = blocks.firstOrNull { it.id == selectedBlockId }
    if (selectedBlock != null) {
        val selectedRingMatches = !enableThreeRingMode || ringForBlock(selectedBlock) == hitRing
        val visibleSlices = if (compactMode && windowMinutes < 1440) {
            visibleWindowSlices(
                startMinute = selectedBlock.startMinuteOfDay,
                durationMinutes = selectedBlock.durationMinutes,
                windowStart = compactWindowStart,
                windowMinutes = windowMinutes
            )
        } else {
            emptyList()
        }
        val visibleStartMinutes = visibleSlices.map { (compactWindowStart + it.relativeStart) % 1440 }
            .ifEmpty { listOf(selectedBlock.startMinuteOfDay) }
        val visibleEndMinutes = visibleSlices.map {
            (compactWindowStart + it.relativeStart + it.visibleDuration) % 1440
        }.ifEmpty { listOf(selectedBlock.startMinuteOfDay + selectedBlock.durationMinutes) }
        if (selectedRingMatches && visibleStartMinutes.any { isNearMinute(minute, it) }) {
            return DialHit.ResizeStart(selectedBlock.id, minute)
        }
        if (selectedRingMatches && visibleEndMinutes.any { isNearMinute(minute, it) }) {
            return DialHit.ResizeEnd(selectedBlock.id, minute)
        }
    }
    val block = findBlockAtMinute(
        blocks = blocks,
        minute = minute,
        ring = hitRing,
        compactMode = compactMode,
        compactWindowStart = compactWindowStart,
        windowMinutes = windowMinutes,
        enableThreeRingMode = enableThreeRingMode
    )
    return if (block != null && (!enableThreeRingMode || hitRing == ringForBlock(block))) {
        DialHit.BlockMove(block.id, minute)
    } else {
        DialHit.Ring(hitRing, minute)
    }
}

private fun distanceFromCenter(offset: Offset, center: Offset): Float =
    hypot(offset.x - center.x, offset.y - center.y)

private fun angleDegreesForOffset(offset: Offset, center: Offset): Float =
    Math.toDegrees(atan2((offset.y - center.y).toDouble(), (offset.x - center.x).toDouble())).toFloat()

private fun minuteForOffset(
    geometry: DialGeometry,
    offset: Offset,
    center: Offset,
    maxRadius: Float,
    compactMode: Boolean,
    compactWindowStart: Int,
    windowMinutes: Int
): Int? {
    val hit = geometry.hitTest(
        point = DialPoint(offset.x, offset.y),
        center = DialPoint(center.x, center.y),
        maxRadius = maxRadius
    )
    return if (compactMode && windowMinutes < 1440) {
        (hit as? DialHit.Ring)?.let {
            offsetToMinuteInWindow(offset, center, compactWindowStart, windowMinutes).takeIf { minute -> minute >= 0 }
        }
    } else {
        (hit as? DialHit.Ring)?.minute?.let {
            windowMinuteOrNull(it, compactMode, compactWindowStart, windowMinutes)
        }
    }
}

private fun windowMinuteOrNull(minute: Int, compactMode: Boolean, compactWindowStart: Int, windowMinutes: Int): Int? {
    if (!compactMode || windowMinutes >= 1440) return minute
    val relative = ((minute - compactWindowStart + 1440) % 1440)
    return if (relative < windowMinutes) minute else null
}

private fun circularMinuteDelta(fromMinute: Int, toMinute: Int): Int {
    val delta = ((toMinute - fromMinute + 2160) % 1440) - 720
    return delta
}

private fun circularDuration(startMinute: Int, endMinute: Int): Int {
    return ((endMinute - startMinute + 1440) % 1440).let { if (it == 0) 1440 else it }
}

/**
 * Blocks on the same ring whose time ranges overlap. Different rings layer by design
 * (calendar vs plan vs actions), so only same-ring overlaps count as conflicts.
 */
internal fun conflictingBlockIds(blocks: List<TimeBlockUiModel>): Set<String> {
    val conflicts = mutableSetOf<String>()
    blocks.groupBy { ringForBlock(it) }.values.forEach { ringBlocks ->
        val sorted = ringBlocks.sortedBy { it.startMinuteOfDay }
        for (index in 0 until sorted.size - 1) {
            val current = sorted[index]
            val next = sorted[index + 1]
            if (current.startMinuteOfDay + current.durationMinutes > next.startMinuteOfDay) {
                conflicts += current.id
                conflicts += next.id
            }
        }
    }
    return conflicts
}

internal fun ringForBlock(block: TimeBlockUiModel): DialRing {
    return when {
        block.calendarEventId != null ||
            block.provenance == "CALENDAR_IMPORTED" ||
            block.category.equals("CALENDAR", ignoreCase = true) -> DialRing.OUTER
        block.taskId != null ||
            block.habitId != null ||
            block.medicationPlanId != null ||
            block.category.equals("ROUTINE", ignoreCase = true) ||
            block.category.equals("MEDICATION", ignoreCase = true) -> DialRing.INNER
        else -> DialRing.MIDDLE
    }
}

internal val TimeBlockUiModel.isInnerRingActionBlock: Boolean
    get() = taskId != null ||
        habitId != null ||
        medicationPlanId != null ||
        category.equals("ROUTINE", ignoreCase = true) ||
        category.equals("MEDICATION", ignoreCase = true)

private fun ringGuideColor(
    ring: DialRing,
    colorScheme: androidx.compose.material3.ColorScheme
): Color {
    return when (ring) {
        DialRing.OUTER -> colorScheme.tertiary
        DialRing.MIDDLE -> colorScheme.primary
        DialRing.INNER -> colorScheme.secondary
        DialRing.CENTER,
        DialRing.OUTSIDE -> colorScheme.outline
    }
}

private fun ringRadius(maxRadius: Float, ring: DialRing): Float {
    return when (ring) {
        DialRing.OUTER -> maxRadius * OUTER_RING_RADIUS_FRACTION
        DialRing.MIDDLE -> maxRadius * MIDDLE_RING_RADIUS_FRACTION
        DialRing.INNER -> maxRadius * INNER_RING_RADIUS_FRACTION
        DialRing.CENTER,
        DialRing.OUTSIDE -> maxRadius * MIDDLE_RING_RADIUS_FRACTION
    }
}

private fun isNearMinute(minute: Int, targetMinute: Int, toleranceMinutes: Int = 30): Boolean {
    val normalizedTarget = ((targetMinute % 1440) + 1440) % 1440
    val distance = kotlin.math.abs(circularMinuteDelta(normalizedTarget, minute))
    return distance <= toleranceMinutes
}
