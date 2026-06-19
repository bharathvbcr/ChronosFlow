package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class DialUtilsTest {

    @Test
    fun `minuteToAngle - midnight is -90 degrees`() {
        assertEquals(-90f, DialUtils.minuteToAngle(0), 0.01f)
    }

    @Test
    fun `minuteToAngle - noon is 90 degrees`() {
        assertEquals(90f, DialUtils.minuteToAngle(720), 0.01f)
    }

    @Test
    fun `minuteToAngle - 6 PM is 180 degrees`() {
        assertEquals(180f, DialUtils.minuteToAngle(1080), 0.01f)
    }

    @Test
    fun `durationToSweep - 6 hours is 90 degrees`() {
        assertEquals(90f, DialUtils.durationToSweep(360), 0.01f)
    }

    @Test
    fun `snapToIncrement - snaps to nearest 15 mins`() {
        assertEquals(0, DialUtils.snapToIncrement(7))
        assertEquals(15, DialUtils.snapToIncrement(8))
        assertEquals(15, DialUtils.snapToIncrement(22))
        assertEquals(30, DialUtils.snapToIncrement(23))
    }

    @Test
    fun `snapToIncrement - wraps 1440 to 0`() {
        assertEquals(0, DialUtils.snapToIncrement(1435))
    }

    @Test
    fun `offsetToMinute - top center is midnight`() {
        val center = Offset(100f, 100f)
        val midnightOffset = Offset(100f, 50f)
        assertEquals(0, DialUtils.offsetToMinute(midnightOffset, center))
    }

    @Test
    fun `offsetToMinute - bottom center is noon`() {
        val center = Offset(100f, 100f)
        val noonOffset = Offset(100f, 150f)
        assertEquals(720, DialUtils.offsetToMinute(noonOffset, center))
    }

    @Test
    fun `offsetToMinuteInWindow inverts compact window angle mapping`() {
        val center = Offset(100f, 100f)
        val targetMinute = 19 * 60
        val angle = DialUtils.minuteToAngleInWindow(
            minute = targetMinute,
            windowStartMinute = 8 * 60,
            windowMinutes = 720
        )
        val radians = Math.toRadians(angle.toDouble())
        val point = Offset(
            x = center.x + cos(radians).toFloat() * 48f,
            y = center.y + sin(radians).toFloat() * 48f
        )

        val resolvedMinute = DialUtils.offsetToMinuteInWindow(
            offset = point,
            center = center,
            windowStart = 8 * 60,
            windowMinutes = 720
        )

        assertEquals(targetMinute, resolvedMinute)
    }
}
