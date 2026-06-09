package com.chronosflow.core.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronosTransitionMotionTest {
    @Test
    fun `fade scale timing avoids transparent route gap`() {
        assertTrue(
            "Entering route must begin fading before the leaving route finishes fading.",
            ChronosMotionDefaults.EnterFadeDelayFraction < ChronosMotionDefaults.ExitFadeDurationFraction
        )
        assertTrue(
            "Entering route should appear early enough to avoid a visible blank flash.",
            ChronosMotionDefaults.EnterFadeDelayFraction <= 0.15f
        )
        assertTrue(
            "Leaving route should remain visible for nearly the full transition.",
            ChronosMotionDefaults.ExitFadeDurationFraction >= 0.95f
        )
    }

    @Test
    fun `primary tab motion is faster and lighter than full route motion`() {
        assertTrue(
            "Primary tabs should not use full route duration.",
            ChronosMotionDefaults.PrimaryTabDurationMillis < ChronosMotionDefaults.DefaultDurationMillis
        )
        assertTrue(
            "Route transitions should stay inside the Material baseline duration.",
            ChronosMotionDefaults.DefaultDurationMillis <= 300
        )
        assertTrue(
            "Primary tab switching needs enough settle time for a perceptible spring rebound.",
            ChronosMotionDefaults.PrimaryTabDurationMillis in 220..260
        )
        assertTrue(
            "Shell chrome feedback should settle before the page slide completes.",
            ChronosMotionDefaults.ChromeDurationMillis < ChronosMotionDefaults.PrimaryTabDurationMillis
        )
        assertTrue(
            "Primary tab motion should keep travel distance lower than route transitions.",
            ChronosMotionDefaults.PrimaryTabSlideFraction < ChronosMotionDefaults.SharedAxisSlideFraction
        )
        assertTrue(
            "Primary tab switching should avoid scaling heavy tab bodies.",
            ChronosMotionDefaults.PrimaryTabScale == 1f
        )
    }

    @Test
    fun `primary tab motion exposes underdamped spring tuning`() {
        val dampingRatio = primaryTabSpringConstant("PrimaryTabSpringDampingRatio")
        val stiffness = primaryTabSpringConstant("PrimaryTabSpringStiffness")

        assertTrue(
            "Primary tab page travel should use an underdamped spring, not a flat tween.",
            dampingRatio in 0.72f..0.9f
        )
        assertTrue(
            "Primary tab spring stiffness should settle quickly without snapping.",
            stiffness in 650f..900f
        )
        assertEquals(
            "Primary tabs should use a slightly larger directional travel before spring settle.",
            0.09f,
            ChronosMotionDefaults.PrimaryTabSlideFraction,
            0.0001f
        )
    }

    private fun primaryTabSpringConstant(name: String): Float =
        runCatching {
            val field = ChronosMotionDefaults::class.java.getDeclaredField(name)
            field.isAccessible = true
            (field.get(null) as Number).toFloat()
        }.getOrElse { Float.NaN }
}
