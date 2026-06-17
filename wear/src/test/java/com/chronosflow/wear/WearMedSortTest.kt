package com.chronosflow.wear

import com.chronosflow.wear.model.WearMed
import com.chronosflow.wear.model.sortMedsForGlance
import org.junit.Assert.assertEquals
import org.junit.Test

class WearMedSortTest {

    private fun med(id: String, reminder: Int, taken: Boolean) =
        WearMed(id = id, name = id, doseLabel = "1 tab", reminderMinute = reminder, taken = taken)

    @Test
    fun `untaken doses sort before taken, earliest reminder first so overdue floats up`() {
        val sorted = sortMedsForGlance(
            listOf(
                med("taken-early", reminder = 8 * 60, taken = true),
                med("upcoming", reminder = 20 * 60, taken = false),
                med("overdue", reminder = 9 * 60, taken = false)
            )
        )

        // Untaken first (overdue 09:00 before upcoming 20:00), then the taken dose last even though
        // its reminder (08:00) is the earliest of all.
        assertEquals(listOf("overdue", "upcoming", "taken-early"), sorted.map { it.id })
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<WearMed>(), sortMedsForGlance(emptyList()))
    }
}
