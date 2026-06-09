package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "calendar_events",
    indices = [
        Index("startAt"),
        Index("endAt"),
        Index("externalId")
    ]
)
data class CalendarEventEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String?,
    val startAt: Instant,
    val endAt: Instant,
    val timezone: String,
    val location: String?,
    val externalId: String?,
    val isAllDay: Boolean
)
