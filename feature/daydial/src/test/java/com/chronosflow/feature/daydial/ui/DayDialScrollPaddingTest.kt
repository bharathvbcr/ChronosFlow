package com.chronosflow.feature.daydial.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DayDialScrollPaddingTest {
    @Test
    fun `bottom padding keeps default page spacing without shell chrome`() {
        assertEquals(
            16.dp,
            dayDialScrollableBottomPadding(
                contentBottomPadding = 0.dp,
                pageBottomPadding = 16.dp
            )
        )
    }

    @Test
    fun `bottom padding reserves shell chrome plus page spacing`() {
        assertEquals(
            124.dp,
            dayDialScrollableBottomPadding(
                contentBottomPadding = 108.dp,
                pageBottomPadding = 16.dp
            )
        )
    }

    @Test
    fun `sidebar pages share the same horizontal and bottom layout padding`() {
        assertEquals(16.dp, SidebarPageLayoutHorizontalPadding)
        assertEquals(16.dp, sidebarPageLayoutBottomPadding(contentBottomPadding = 0.dp))
        assertEquals(124.dp, sidebarPageLayoutBottomPadding(contentBottomPadding = 108.dp))
    }

    @Test
    fun `sidebar pages reserve the floating top bar inset as scroll padding`() {
        assertEquals(16.dp, sidebarPageLayoutTopPadding(contentTopPadding = 0.dp))
        assertEquals(110.dp, sidebarPageLayoutTopPadding(contentTopPadding = 94.dp))
    }

    @Test
    fun `viewport bottom padding is capped so compact content stays visible`() {
        assertEquals(0.dp, dayDialScrollableViewportBottomPadding(0.dp))
        assertEquals(32.dp, dayDialScrollableViewportBottomPadding(32.dp))
        assertEquals(48.dp, dayDialScrollableViewportBottomPadding(132.dp))
    }
}
