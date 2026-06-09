package com.chronosflow.core.domain.model

import java.time.LocalDate

data class RecurrenceRule(
    val id: String,
    val blockId: String,
    val pattern: String,
    val intervalWeeks: Int,
    val startsOn: LocalDate?,
    val endsOn: LocalDate?,
    val maxOccurrences: Int?,
    val weekdays: String?,
    val createdAt: Long?,
    val updatedAt: Long?
)
