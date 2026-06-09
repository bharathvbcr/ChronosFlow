package com.chronosflow.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.chronosflow.core.domain.model.SleepSchedule
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepScheduleRepositoryImplTest {
    private val context: Context = mockk()
    private val preferences: SharedPreferences = mockk()

    private fun repository(): SleepScheduleRepositoryImpl {
        every { context.getSharedPreferences(SleepSchedule.PREFERENCES_NAME, Context.MODE_PRIVATE) } returns preferences
        return SleepScheduleRepositoryImpl(context)
    }

    @Test
    fun `get sleep schedule defaults when no values are stored`() {
        every { preferences.getBoolean(SleepSchedule.KEY_ENABLED, false) } returns false
        every { preferences.getInt(SleepSchedule.KEY_START_MINUTE, SleepSchedule.DEFAULT_START_MINUTE) } returns SleepSchedule.DEFAULT_START_MINUTE
        every { preferences.getInt(SleepSchedule.KEY_END_MINUTE, SleepSchedule.DEFAULT_END_MINUTE) } returns SleepSchedule.DEFAULT_END_MINUTE

        val repository = repository()

        assertEquals(
            SleepSchedule(
                enabled = false,
                startMinute = SleepSchedule.DEFAULT_START_MINUTE,
                endMinute = SleepSchedule.DEFAULT_END_MINUTE
            ),
            repository.getSleepSchedule()
        )
    }

    @Test
    fun `get sleep schedule uses persisted values`() {
        every { preferences.getBoolean(SleepSchedule.KEY_ENABLED, false) } returns true
        every { preferences.getInt(SleepSchedule.KEY_START_MINUTE, SleepSchedule.DEFAULT_START_MINUTE) } returns 1380
        every { preferences.getInt(SleepSchedule.KEY_END_MINUTE, SleepSchedule.DEFAULT_END_MINUTE) } returns 420

        val repository = repository()

        assertEquals(
            SleepSchedule(enabled = true, startMinute = 1380, endMinute = 420),
            repository.getSleepSchedule()
        )
    }
}

