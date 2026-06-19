package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import java.time.Instant

@Entity(
    tableName = "calendar_events",
    // Composite key: recurring instances share the device EVENT_ID, so keying
    // by id alone keeps only the last instance of the sync window.
    primaryKeys = ["id", "startAt"],
    indices = [
        Index("startAt"),
        Index("endAt"),
        Index("externalId")
    ]
)
data class CalendarEventEntity(
    val id: Long,
    val title: String,
    val description: String?,
    val startAt: Instant,
    val endAt: Instant,
    val timezone: String,
    val location: String?,
    val externalId: String?,
    val isAllDay: Boolean
)
