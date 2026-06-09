package com.chronosflow.core.domain.model

data class TaskChecklistItem(
    val id: String,
    val label: String,
    val isCompleted: Boolean
)
