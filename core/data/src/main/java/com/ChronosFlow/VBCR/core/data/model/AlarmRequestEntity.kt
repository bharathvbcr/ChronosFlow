package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "alarm_requests",
    indices = [Index("type"), Index("scheduledFor"), Index("deliveryState"), Index("medicationPlanId"), Index("blockId")]
)
data class AlarmRequestEntity(
    @PrimaryKey val id: String,
    val type: String,
    val scheduledFor: Instant,
    val title: String,
    val message: String,
    val medicationPlanId: String?,
    val blockId: String?,
    val reliability: String,
    val deliveryState: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deliveredAt: Instant?,
    val failureReason: String?
)
