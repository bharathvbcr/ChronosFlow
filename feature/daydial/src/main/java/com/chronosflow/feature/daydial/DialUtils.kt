package com.chronosflow.feature.daydial

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.roundToInt

typealias TimeRangeUi = com.chronosflow.feature.daydial.model.TimeRangeUi
typealias TimeBlockUiModel = com.chronosflow.feature.daydial.model.TimeBlockUiModel

object DialUtils {
    fun minuteToAngle(minute: Int): Float {
        return (minute / 1440f) * 360f - 90f
    }

    fun minuteToAngleInWindow(minute: Int, windowStartMinute: Int, windowMinutes: Int): Float {
        if (windowMinutes >= 1440) {
            return minuteToAngle(minute)
        }
        val relative = ((minute - windowStartMinute + 1440) % 1440)
        if (relative >= windowMinutes) return Float.NaN
        return (relative / windowMinutes.toFloat()) * 360f - 90f
    }

    fun durationToSweep(durationMinutes: Int): Float {
        return (durationMinutes / 1440f) * 360f
    }

    fun durationToSweepInWindow(durationMinutes: Int, windowMinutes: Int): Float {
        if (windowMinutes >= 1440) return durationToSweep(durationMinutes)
        return ((durationMinutes.toFloat() / windowMinutes.toFloat()) * 360f)
            .coerceAtMost(360f)
    }

    fun offsetToMinute(offset: Offset, center: Offset): Int {
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val rawDegrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val clockDegrees = (rawDegrees + 90f + 360f) % 360f
        return ((clockDegrees / 360f) * 1440f).roundToInt().coerceIn(0, 1439)
    }

    fun offsetToMinuteInWindow(offset: Offset, center: Offset, windowStart: Int, windowMinutes: Int): Int {
        if (windowMinutes >= 1440) return offsetToMinute(offset, center)
        val dx = offset.x - center.x
        val dy = offset.y - center.y
        val rawDegrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val clockDegrees = (rawDegrees + 90f + 360f) % 360f
        val relative = ((clockDegrees / 360f) * windowMinutes.toFloat()).roundToInt()
            .coerceIn(0, windowMinutes - 1)
        return (windowStart + relative) % 1440
    }

    fun snapToIncrement(minute: Int, increment: Int = 15): Int {
        return (((minute + increment / 2) / increment) * increment) % 1440
    }
}
