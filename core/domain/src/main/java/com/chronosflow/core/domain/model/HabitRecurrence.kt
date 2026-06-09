package com.chronosflow.core.domain.model

enum class HabitRecurrencePeriodUnit {
    DAY,
    WEEK,
    MONTH
}

sealed interface HabitRecurrenceRule {
    data class Scheduled(
        val recurrence: PlannerRecurrence = PlannerRecurrence()
    ) : HabitRecurrenceRule

    data class Quota(
        val targetCompletions: Int,
        val periodUnit: HabitRecurrencePeriodUnit,
        val interval: Int = 1
    ) : HabitRecurrenceRule {
        init {
            require(targetCompletions > 0) {
                "Quota recurrence targetCompletions must be greater than 0."
            }
            require(interval > 0) {
                "Quota recurrence interval must be greater than 0."
            }
        }
    }
}
