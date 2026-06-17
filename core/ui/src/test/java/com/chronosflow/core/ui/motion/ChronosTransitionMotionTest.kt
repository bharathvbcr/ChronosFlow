package com.chronosflow.core.ui.motion

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
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
            "Primary tab slide should read as a perceptible directional glide, not a full-width slide.",
            ChronosMotionDefaults.PrimaryTabSlideFraction in 0.10f..0.20f
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
            "Primary tab spring stiffness should stay soft enough to land with a gentle, fluid rebound.",
            stiffness in 350f..600f
        )
        assertEquals(
            "Primary tabs should use a perceptible directional travel before the spring settles.",
            0.16f,
            ChronosMotionDefaults.PrimaryTabSlideFraction,
            0.0001f
        )
    }

    @Test
    fun `nav selection pill mirrors the primary-tab spring and respects reduced motion`() {
        val motionSpec = ChronosValueAnimationFactory.navIndicator(reducedMotion = false)
        assertTrue(
            "The bottom-nav selection pill should glide with a spring, not a flat tween, " +
                "so it bounces in step with the page transition.",
            motionSpec is SpringSpec<*>
        )
        val spring = motionSpec as SpringSpec<*>
        assertEquals(
            "Pill glide damping should match the primary-tab spring for a unified feel.",
            ChronosMotionDefaults.PrimaryTabSpringDampingRatio,
            spring.dampingRatio,
            0.0001f
        )
        assertEquals(
            "Pill glide stiffness should match the primary-tab spring for a unified feel.",
            ChronosMotionDefaults.PrimaryTabSpringStiffness,
            spring.stiffness,
            0.0001f
        )
        assertTrue(
            "Reduced motion must snap the selection pill instantly (no spring).",
            ChronosValueAnimationFactory.navIndicator(reducedMotion = true) is SnapSpec<*>
        )
    }

    @Test
    fun `quick-add fab rotation bounces with a spring and snaps under reduced motion`() {
        val spec = ChronosValueAnimationFactory.quickAddRotation(reducedMotion = false)
        assertTrue(
            "The quick-add +/× toggle should rotate with a spring, matching the iOS bounce idiom " +
                "used by the page transition and nav pill — not a flat tween.",
            spec is SpringSpec<*>
        )
        assertTrue(
            "The toggle spring should be underdamped for a small playful overshoot.",
            (spec as SpringSpec<*>).dampingRatio < 1f
        )
        assertTrue(
            "Reduced motion must snap the icon instantly (no spring).",
            ChronosValueAnimationFactory.quickAddRotation(reducedMotion = true) is SnapSpec<*>
        )
    }

    @Test
    fun `press feedback is a spring so every button carries the bouncy press-scale`() {
        val spec = ChronosValueAnimationFactory.pressScale(reducedMotion = false)
        assertTrue(
            "Press feedback must be a spring — it's the bouncy press-scale every Chronos button " +
                "relies on (enforced by ChronosUxPrinciplesAuditTest) — not a flat tween.",
            spec is SpringSpec<*>
        )
        assertTrue(
            "The pressed target scale should shrink the surface so the press is felt.",
            ChronosMotionDefaults.PressedScale < 1f
        )
        assertTrue(
            "Reduced motion must snap the press feedback instantly (no spring).",
            ChronosValueAnimationFactory.pressScale(reducedMotion = true) is SnapSpec<*>
        )
    }

    @Test
    fun `every reduced-motion animation spec snaps instantly`() {
        // Accessibility contract: when the user opts out of motion, no chrome should animate.
        // A single regression in any factory's reduced-motion branch is a real a11y bug, so
        // assert the whole surface here rather than per-spec.
        val factory = ChronosValueAnimationFactory
        assertTrue("navigationChromeScale must snap", factory.navigationChromeScale(reducedMotion = true) is SnapSpec<*>)
        assertTrue("quickAddRotation must snap", factory.quickAddRotation(reducedMotion = true) is SnapSpec<*>)
        assertTrue("focusTimerProgress must snap", factory.focusTimerProgress(reducedMotion = true) is SnapSpec<*>)
        assertTrue("stateChange must snap", factory.stateChange<Float>(reducedMotion = true) is SnapSpec<*>)
        assertTrue("selection must snap", factory.selection<Float>(reducedMotion = true) is SnapSpec<*>)
        assertTrue("pressScale must snap", factory.pressScale(reducedMotion = true) is SnapSpec<*>)
        assertTrue("navIndicator must snap", factory.navIndicator(reducedMotion = true) is SnapSpec<*>)
    }

    private fun primaryTabSpringConstant(name: String): Float =
        runCatching {
            val field = ChronosMotionDefaults::class.java.getDeclaredField(name)
            field.isAccessible = true
            (field.get(null) as Number).toFloat()
        }.getOrElse { Float.NaN }
}
