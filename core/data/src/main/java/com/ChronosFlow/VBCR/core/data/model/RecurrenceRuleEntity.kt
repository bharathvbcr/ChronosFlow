package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "recurrence_rules", indices = [Index("blockId")])
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
