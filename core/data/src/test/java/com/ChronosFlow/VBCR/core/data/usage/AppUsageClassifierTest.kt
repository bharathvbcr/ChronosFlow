package com.ChronosFlow.VBCR.core.data.usage

import com.ChronosFlow.VBCR.core.domain.model.UsageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUsageClassifierTest {

    @Test
    fun `well-known social and video apps are distracting`() {
        assertEquals(UsageCategory.DISTRACTING, knownPackageCategory("com.instagram.android"))
        assertEquals(UsageCategory.DISTRACTING, knownPackageCategory("com.google.android.youtube"))
        assertEquals(UsageCategory.DISTRACTING, knownPackageCategory("com.zhiliaoapp.musically"))
    }

    @Test
    fun `well-known productivity and work apps are productive`() {
        assertEquals(UsageCategory.PRODUCTIVE, knownPackageCategory("com.google.android.gm"))
        assertEquals(UsageCategory.PRODUCTIVE, knownPackageCategory("com.slack"))
        assertEquals(UsageCategory.PRODUCTIVE, knownPackageCategory("com.todoist"))
    }

    @Test
    fun `child packages match a parent prefix`() {
        // Office sub-apps (word, excel, …) all live under com.microsoft.office.
        assertEquals(UsageCategory.PRODUCTIVE, knownPackageCategory("com.microsoft.office.word"))
        // Drive editors live under com.google.android.apps.docs.
        assertEquals(UsageCategory.PRODUCTIVE, knownPackageCategory("com.google.android.apps.docs.editors.sheets"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(UsageCategory.PRODUCTIVE, knownPackageCategory("com.Slack"))
    }

    @Test
    fun `unknown packages return null so the caller can fall back to neutral`() {
        assertNull(knownPackageCategory("com.example.someobscureapp"))
        // A bare prefix of a known package must not false-match (com.google would be ambiguous).
        assertNull(knownPackageCategory("com.google"))
    }
}
