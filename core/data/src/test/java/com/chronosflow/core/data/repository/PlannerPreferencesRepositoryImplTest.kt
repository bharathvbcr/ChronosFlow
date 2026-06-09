package com.chronosflow.core.data.repository

import com.chronosflow.core.data.datastore.ChronosPreferencesDataSource
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class PlannerPreferencesRepositoryImplTest {
    private val dataSource: ChronosPreferencesDataSource = mockk()
    private val repository = PlannerPreferencesRepositoryImpl(dataSource)

    @Test
    fun `habit template ids dedupe and trim`() {
        every {
            dataSource.getString("habit_recent_template_ids")
        } returns " a1 |a2|a1||  b1 | "

        assertEquals(listOf("a1", "a2", "b1"), repository.getRecentHabitTemplateIds())
    }

    @Test
    fun `medication template ids default to empty`() {
        every { dataSource.getString("medication_recent_template_ids") } returns ""
        assertEquals(emptyList<String>(), repository.getRecentMedicationTemplateIds())
    }

    @Test
    fun `habit template ids persist deduped list`() {
        every { dataSource.putString(any(), any()) } returns Unit
        repository.saveRecentHabitTemplateIds(listOf("  a1  ", "a1", "b2", "b2", ""))

        verify {
            dataSource.putString("habit_recent_template_ids", "a1|b2")
        }
    }

    @Test
    fun `medication template ids persist deduped list`() {
        every { dataSource.putString(any(), any()) } returns Unit
        repository.saveRecentMedicationTemplateIds(listOf("m1", "m1", "m2", "m3", "m3", "m4", "m5", "m6"))

        verify {
            dataSource.putString("medication_recent_template_ids", "m1|m2|m3|m4|m5")
        }
    }
}

