package com.chronosflow.core.domain.model

import java.time.LocalDate

data class Routine(
    val id: String,
    val title: String,
    val blockIds: List<String>,
    val isActive: Boolean,
    val lastCompletedDate: LocalDate?
)
