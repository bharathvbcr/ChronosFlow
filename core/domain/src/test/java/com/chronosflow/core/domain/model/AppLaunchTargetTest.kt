package com.chronosflow.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchTargetTest {

    @Test
    fun `normalizeAppLaunchValue accepts package names`() {
        assertEquals("com.example.journal", normalizeAppLaunchValue("package:com.example.journal"))
    }

    @Test
    fun `normalizeAppLaunchValue normalizes launcher components`() {
        val value = normalizeAppLaunchValue("component:com.example.journal/.MainActivity")

        assertEquals("component:com.example.journal/com.example.journal.MainActivity", value)
    }

    @Test
    fun `parseAppLaunchComponent expands relative class names`() {
        val component = parseAppLaunchComponent("component:com.example.journal/.MainActivity")

        assertEquals("com.example.journal", component?.packageName)
        assertEquals("com.example.journal.MainActivity", component?.className)
        assertTrue(isComponentLaunchValue(component?.launchValue.orEmpty()))
    }

    @Test
    fun `parseAppLaunchComponent rejects malformed component values`() {
        assertNull(parseAppLaunchComponent("component:com.example.journal"))
        assertNull(parseAppLaunchComponent("component:/MainActivity"))
    }
}
