package com.chronosflow.core.domain.model

import java.time.LocalDate

data class Habit(
    val id: String,
    val title: String,
    val cadence: String,
    val windowStartMinute: Int,
    val windowEndMinute: Int,
    val difficulty: Int,
    val isBundled: Boolean,
    val streakCount: Int,
    val lastCompletedDate: LocalDate?,
    val isActive: Boolean,
    val schedule: HabitSchedule? = null,
    val recentEvents: List<HabitEvent> = emptyList(),
    val analytics: HabitAnalytics = HabitAnalytics(),
    val launchTarget: AppLaunchTarget? = null
)
