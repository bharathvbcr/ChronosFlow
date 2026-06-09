package com.chronosflow.core.domain.model

enum class AlarmReliability {
    EXACT,
    DEGRADED_WINDOW,
    INEXACT,
    BLOCKED
}

data class ExactAlarmPolicy(
    val requestId: String,
    val reliability: AlarmReliability,
    val userFacingMessage: String,
    val requiresSettingsAction: Boolean
)
