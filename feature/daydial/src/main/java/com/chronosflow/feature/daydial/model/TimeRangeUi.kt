package com.chronosflow.feature.daydial.model

data class TimeRangeUi(
    val startMinute: Int,
    val endMinute: Int,
    val isFree: Boolean = true
)
