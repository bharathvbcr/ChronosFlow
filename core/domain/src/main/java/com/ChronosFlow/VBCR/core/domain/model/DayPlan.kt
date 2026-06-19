package com.ChronosFlow.VBCR.core.domain.model

import java.time.LocalDate
import java.time.ZoneId

data class DayPlan(
    val date: LocalDate,
    val timezone: ZoneId,
    val status: DayPlanStatus,
    val blocks: List<TimeBlock>,
    val conflicts: List<ScheduleConflict>,
    val review: DailyReviewSummary?
)
