package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurrence_rules")
data class RecurrenceRuleEntity(
    @PrimaryKey val id: String,
    val blockId: String,
    val pattern: String,
    val intervalWeeks: Int,
    val startsOn: String?,
    val endsOn: String?,
    val maxOccurrences: Int?,
    val weekdays: String?,
    val createdAt: Long?,
    val updatedAt: Long?
)
