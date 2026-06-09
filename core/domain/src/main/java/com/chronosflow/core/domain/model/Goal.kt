package com.chronosflow.core.domain.model

import java.time.LocalDate

data class Goal(
    val id: String,
    val title: String,
    val description: String?,
    val category: String,
    val targetValue: Int,
    val startDate: LocalDate,
    val targetDate: LocalDate?,
    val progressValue: Int,
    val isCompleted: Boolean
)
