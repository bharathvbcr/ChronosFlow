package com.chronosflow.core.domain.model

enum class WidgetFocusState { IDLE, RUNNING, PAUSED }

data class ChronosWidgetSummary(
    val habitId: String? = null,
    val habitTitle: String? = null,
    val medicationId: String? = null,
    val medicationName: String? = null,
    val focusState: WidgetFocusState = WidgetFocusState.IDLE,
    val focusTimeLeftSeconds: Int = 0,
    val currentBlockTitle: String? = null,
    val nextBlockTitle: String? = null,
    val nextBlockStartMinuteOfDay: Int? = null
)
