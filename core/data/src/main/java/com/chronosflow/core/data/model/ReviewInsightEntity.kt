package com.chronosflow.core.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "review_insights",
    indices = [Index("date"), Index("type"), Index("severity"), Index("relatedBlockId")]
)
data class ReviewInsightEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val type: String,
    val title: String,
    val detail: String,
    val relatedBlockId: String?,
    val severity: String,
    val assistSource: String? = null
)
