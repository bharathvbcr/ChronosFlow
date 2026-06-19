package com.ChronosFlow.VBCR.feature.daydial

import androidx.compose.ui.geometry.Offset
import com.ChronosFlow.VBCR.core.domain.planner.DialHit
import com.ChronosFlow.VBCR.core.domain.planner.DialGeometry
import com.ChronosFlow.VBCR.core.domain.planner.DialRing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosDialTapPolicyTest {
    @Test
    fun `tap on outer ring triggers add block`() {
        assertTrue(shouldTriggerAddFromTap(DialHit.Ring(DialRing.OUTER, 60)))
    }

    @Test
    fun `tap on middle ring does not trigger add block`() {
        assertFalse(shouldTriggerAddFromTap(DialHit.Ring(DialRing.MIDDLE, 60)))
    }

    @Test
    fun `tap on inner ring does not trigger add block`() {
        assertFalse(shouldTriggerAddFromTap(DialHit.Ring(DialRing.INNER, 60)))
    }

    @Test
    fun `non ring hit does not trigger add block`() {
        assertFalse(shouldTriggerAddFromTap(DialHit.BlockMove("block-1", 60)))
        assertFalse(shouldTriggerAddFromTap(DialHit.ResizeStart("block-1", 60)))
        assertFalse(shouldTriggerAddFromTap(DialHit.ResizeEnd("block-1", 60)))
        assertFalse(shouldTriggerAddFromTap(DialHit.Outside))
    }

    @Test
    fun `outside edge of visible outer ring is treated as outer ring tap`() {
        val dialHitRadius = 200f
        val outerRingTapRadius = 230f

        assertTrue(
            shouldTreatOutsideRadiusAsOuterRingTap(
                distanceFromCenter = 205f,
                dialHitRadius = dialHitRadius,
                outerRingTapRadius = outerRingTapRadius
            )
        )
    }

    @Test
    fun `far outside tap is not treated as outer ring tap`() {
        assertFalse(
            shouldTreatOutsideRadiusAsOuterRingTap(
                distanceFromCenter = 240f,
                dialHitRadius = 200f,
                outerRingTapRadius = 230f
            )
        )
    }

    @Test
    fun `tap just outside old hit radius resolves to outer ring`() {
        val hit = dialHitForOffset(
            geometry = DialGeometry(),
            offset = Offset(100f, 16f),
            center = Offset(100f, 100f),
            maxRadius = 80f,
            outerRingTapRadius = 92f,
            blocks = emptyList(),
            selectedBlockId = null,
            compactMode = false,
            compactWindowStart = 0,
            windowMinutes = 1440,
            enableThreeRingMode = true
        )

        assertEquals(DialHit.Ring(DialRing.OUTER, 0), hit)
    }
}
