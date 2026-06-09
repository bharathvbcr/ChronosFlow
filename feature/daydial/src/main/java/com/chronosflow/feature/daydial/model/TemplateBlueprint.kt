package com.chronosflow.feature.daydial.model

internal data class TemplateBlockBlueprint(
    val title: String,
    val startMinute: Int,
    val durationMinutes: Int,
    val category: String
)

internal data class TemplateBlueprint(
    val id: String,
    val name: String,
    val blocks: List<TemplateBlockBlueprint>
)
