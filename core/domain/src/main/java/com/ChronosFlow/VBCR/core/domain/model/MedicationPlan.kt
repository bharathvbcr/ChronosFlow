package com.ChronosFlow.VBCR.core.domain.model

import java.time.LocalDateTime

data class MedicationPlan(
    val id: String,
    val name: String,
    val dosage: String,
    val unit: String,
    val notes: String?,
    val startAt: LocalDateTime?,
    val endAt: LocalDateTime?,
    val reminderMinuteOfDay: Int,
    val takeWithFood: Boolean,
    val missedCount: Int,
    val refillNeededAfterDoses: Int?,
    val isActive: Boolean,
    val schedule: MedicationSchedule? = null,
    val safetyProfile: MedicationSafetyProfile? = null,
    val recentDoseEvents: List<MedicationDoseEvent> = emptyList(),
    val analytics: MedicationAnalytics = MedicationAnalytics()
)
