package com.chronosflow.core.domain.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialGeometryTest {
    private val geometry = DialGeometry()

    @Test
    fun `minuteToAngle - midnight is -90 degrees`() {
        assertEquals(-90f, geometry.minuteToAngle(0), 0.01f)
    }

    @Test
    fun `minuteToAngle - noon is 90 degrees`() {
        assertEquals(90f, geometry.minuteToAngle(720), 0.01f)
    }

    @Test
    fun `angleToMinute - -90 degrees is midnight`() {
        assertEquals(0, geometry.angleToMinute(-90f))
    }

    @Test
    fun `angleToMinute - 90 degrees is noon`() {
        assertEquals(720, geometry.angleToMinute(90f))
    }

    @Test
    fun `ringForRadius - correct rings based on distance`() {
        val maxRadius = 100f
        assertEquals(DialRing.CENTER, geometry.ringForRadius(5f, maxRadius))
        assertEquals(DialRing.INNER, geometry.ringForRadius(20f, maxRadius))
        assertEquals(DialRing.MIDDLE, geometry.ringForRadius(50f, maxRadius))
        assertEquals(DialRing.OUTER, geometry.ringForRadius(80f, maxRadius))
        assertEquals(DialRing.OUTSIDE, geometry.ringForRadius(99f, maxRadius))
    }

    @Test
    fun `hitTest - correctly identifies ring and minute`() {
        val center = DialPoint(100f, 100f)
        val maxRadius = 100f
        // Top center point (midnight) in Middle ring
        val point = DialPoint(100f, 50f)
        val hit = geometry.hitTest(point, center, maxRadius)
        
        assertTrue(hit is DialHit.Ring)
        val ringHit = hit as DialHit.Ring
        assertEquals(DialRing.MIDDLE, ringHit.ring)
        assertEquals(0, ringHit.minute)
    }

    @Test
    fun `snap - snaps to grid`() {
        assertEquals(0, geometry.snap(7, 15))
        assertEquals(15, geometry.snap(8, 15))
        assertEquals(0, geometry.snap(1435, 15))
    }
}
