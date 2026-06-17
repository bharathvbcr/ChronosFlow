package com.chronosflow.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class RelativeTimeFormatTest {
    private val now = 1_700_000_000_000L

    private fun agoMillis(amount: Long, unit: TimeUnit): Long = now - unit.toMillis(amount)

    @Test
    fun `null timestamp reads as not synced yet`() {
        assertEquals("Not synced yet", formatLastSyncedLabel(null, now))
    }

    @Test
    fun `under a minute reads as just now`() {
        assertEquals("Synced just now", formatLastSyncedLabel(agoMillis(30, TimeUnit.SECONDS), now))
    }

    @Test
    fun `future timestamp from clock skew reads as just now`() {
        assertEquals("Synced just now", formatLastSyncedLabel(now + 5_000L, now))
    }

    @Test
    fun `minutes are reported individually`() {
        assertEquals("Synced 1 min ago", formatLastSyncedLabel(agoMillis(1, TimeUnit.MINUTES), now))
        assertEquals("Synced 45 min ago", formatLastSyncedLabel(agoMillis(45, TimeUnit.MINUTES), now))
    }

    @Test
    fun `hours are reported below a day`() {
        assertEquals("Synced 1 hr ago", formatLastSyncedLabel(agoMillis(1, TimeUnit.HOURS), now))
        assertEquals("Synced 23 hr ago", formatLastSyncedLabel(agoMillis(23, TimeUnit.HOURS), now))
    }

    @Test
    fun `a single day reads as yesterday and more reads as days ago`() {
        assertEquals("Synced yesterday", formatLastSyncedLabel(agoMillis(1, TimeUnit.DAYS), now))
        assertEquals("Synced 4 days ago", formatLastSyncedLabel(agoMillis(4, TimeUnit.DAYS), now))
    }
}
