package com.ChronosFlow.VBCR.core.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_usage_overrides")
data class AppUsageOverrideEntity(
    @PrimaryKey val packageName: String,
    val category: String
)
