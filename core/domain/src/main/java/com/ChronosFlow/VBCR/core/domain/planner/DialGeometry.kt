package com.ChronosFlow.VBCR.core.domain.planner

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

data class DialPoint(val x: Float, val y: Float)

data class DialArc(
    val startAngleDegrees: Float,
    val sweepDegrees: Float,
    val ring: DialRing
)

enum class DialRing {
    OUTER,
    MIDDLE,
    INNER,
    CENTER,
    OUTSIDE
}

sealed interface DialHit {
    data class Ring(val ring: DialRing, val minute: Int) : DialHit
    data class BlockMove(val blockId: String, val minute: Int) : DialHit
    data class ResizeStart(val blockId: String, val minute: Int) : DialHit
    data class ResizeEnd(val blockId: String, val minute: Int) : DialHit
    data object Outside : DialHit
}

sealed interface DialDragMode {
    data class Move(val blockId: String, val anchorMinute: Int) : DialDragMode
    data class ResizeStart(val blockId: String) : DialDragMode
    data class ResizeEnd(val blockId: String) : DialDragMode
    data class Create(val startMinute: Int) : DialDragMode
}

class DialGeometry(
    private val innerRadiusFraction: Float = 0.40f,
    private val middleRadiusFraction: Float = 0.72f,
    private val outerRadiusFraction: Float = 0.98f
) {
    fun minuteToAngle(minute: Int): Float = (normalizeMinute(minute) / 1440f) * 360f - 90f

    fun angleToMinute(angleDegrees: Float): Int {
        val normalized = ((angleDegrees + 90f) % 360f + 360f) % 360f
        return ((normalized / 360f) * 1440f).roundToInt().coerceIn(0, 1439)
    }

    fun blockToArc(startMinute: Int, durationMinutes: Int, ring: DialRing = DialRing.MIDDLE): DialArc {
        return DialArc(
            startAngleDegrees = minuteToAngle(startMinute),
            sweepDegrees = (durationMinutes.coerceIn(1, 1440) / 1440f) * 360f,
            ring = ring
        )
    }

    fun ringForRadius(distance: Float, maxRadius: Float): DialRing {
        val fraction = if (maxRadius <= 0f) 0f else distance / maxRadius
        return when {
            fraction > outerRadiusFraction -> DialRing.OUTSIDE
            fraction > middleRadiusFraction -> DialRing.OUTER
            fraction > innerRadiusFraction -> DialRing.MIDDLE
            fraction > 0.08f -> DialRing.INNER
            else -> DialRing.CENTER
        }
    }

    fun hitTest(point: DialPoint, center: DialPoint, maxRadius: Float): DialHit {
        val dx = point.x - center.x
        val dy = point.y - center.y
        val ring = ringForRadius(hypot(dx, dy), maxRadius)
        if (ring == DialRing.OUTSIDE) return DialHit.Outside
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return DialHit.Ring(ring, angleToMinute(angle))
    }

    fun snap(minute: Int, grid: Int): Int {
        val increment = grid.coerceAtLeast(1)
        return (((normalizeMinute(minute) + increment / 2) / increment) * increment) % 1440
    }

    private fun normalizeMinute(minute: Int): Int = ((minute % 1440) + 1440) % 1440
}
