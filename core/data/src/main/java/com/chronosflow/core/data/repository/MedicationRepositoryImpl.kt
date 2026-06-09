package com.chronosflow.core.data.repository

import com.chronosflow.core.data.dao.MedicationDoseEventDao
import com.chronosflow.core.data.dao.MedicationDao
import com.chronosflow.core.data.dao.MedicationSafetyProfileDao
import com.chronosflow.core.data.dao.MedicationScheduleDao
import com.chronosflow.core.data.mapper.toDomain
import com.chronosflow.core.data.mapper.toEntity
import com.chronosflow.core.domain.model.MedicationAnalytics
import com.chronosflow.core.domain.model.MedicationDoseEvent
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationSafetyProfile
import com.chronosflow.core.domain.model.MedicationSchedule
import com.chronosflow.core.domain.model.buildLegacyMedicationSchedule
import com.chronosflow.core.domain.model.deriveMedicationAnalytics
import com.chronosflow.core.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

class MedicationRepositoryImpl @Inject constructor(
    private val medicationDao: MedicationDao,
    private val medicationScheduleDao: MedicationScheduleDao,
    private val medicationSafetyProfileDao: MedicationSafetyProfileDao,
    private val medicationDoseEventDao: MedicationDoseEventDao
) : MedicationRepository {
    override fun observeMedicationPlans(): Flow<List<MedicationPlan>> =
        combine(
            medicationDao.observeMedicationPlans(),
            medicationScheduleDao.observeAllSchedules(),
            medicationSafetyProfileDao.observeAllProfiles(),
            medicationDoseEventDao.observeAllEvents()
        ) { plans, schedules, profiles, events ->
            val schedulesByPlan = schedules.associateBy { it.medicationPlanId }
            val profilesByPlan = profiles.associateBy { it.medicationPlanId }
            val eventsByPlan = events.groupBy { it.medicationPlanId }
            plans.map { planEntity ->
                val base = planEntity.toDomain()
                val secondaryReminder = legacySecondaryReminderMinute(planEntity.notes)
                val displayNotes = stripLegacyMedicationMetadata(planEntity.notes)
                val schedule = schedulesByPlan[planEntity.id]?.toDomain() ?: buildLegacyMedicationSchedule(
                    medicationPlanId = planEntity.id,
                    primaryReminderMinute = planEntity.reminderMinuteOfDay,
                    secondaryReminderMinute = secondaryReminder,
                    plannerVisible = planEntity.isActive
                )
                val profile = profilesByPlan[planEntity.id]?.toDomain() ?: legacySafetyProfile(
                    planId = planEntity.id,
                    unit = planEntity.unit,
                    notes = displayNotes,
                    takeWithFood = planEntity.takeWithFood,
                    refillNeededAfterDoses = planEntity.refillNeededAfterDoses,
                    legacyTiming = legacyMealTiming(planEntity.notes)
                )
                val doseEvents = eventsByPlan[planEntity.id].orEmpty().map { it.toDomain() }
                val analytics = if (doseEvents.isEmpty()) {
                    MedicationAnalytics(
                        adherenceRate = estimateLegacyMedicationAdherence(planEntity.missedCount),
                        missedCountLast14Days = planEntity.missedCount,
                        refillSoon = profile.refillSoon
                    )
                } else {
                    deriveMedicationAnalytics(
                        events = doseEvents,
                        supplyRemaining = profile.supplyRemaining,
                        refillThreshold = profile.refillThreshold
                    )
                }
                base.copy(
                    notes = displayNotes,
                    schedule = schedule,
                    safetyProfile = profile,
                    recentDoseEvents = doseEvents.sortedByDescending(MedicationDoseEvent::recordedAt).take(12),
                    analytics = analytics
                )
            }
        }

    override suspend fun getMedicationPlanById(id: String): MedicationPlan? =
        medicationDao.getMedicationPlanById(id)?.toDomain()?.let { plan ->
            val secondaryReminder = legacySecondaryReminderMinute(plan.notes)
            val displayNotes = stripLegacyMedicationMetadata(plan.notes)
            val schedule = medicationScheduleDao.getScheduleForPlan(id)?.toDomain() ?: buildLegacyMedicationSchedule(
                medicationPlanId = id,
                primaryReminderMinute = plan.reminderMinuteOfDay,
                secondaryReminderMinute = secondaryReminder,
                plannerVisible = plan.isActive
            )
            val profile = medicationSafetyProfileDao.getProfileForPlan(id)?.toDomain() ?: legacySafetyProfile(
                planId = id,
                unit = plan.unit,
                notes = displayNotes,
                takeWithFood = plan.takeWithFood,
                refillNeededAfterDoses = plan.refillNeededAfterDoses,
                legacyTiming = legacyMealTiming(plan.notes)
            )
            val doseEvents = medicationDoseEventDao.getEventsForPlan(id).map { it.toDomain() }
            plan.copy(
                notes = displayNotes,
                schedule = schedule,
                safetyProfile = profile,
                recentDoseEvents = doseEvents.sortedByDescending(MedicationDoseEvent::recordedAt).take(12),
                analytics = if (doseEvents.isEmpty()) {
                    MedicationAnalytics(
                        adherenceRate = estimateLegacyMedicationAdherence(plan.missedCount),
                        missedCountLast14Days = plan.missedCount,
                        refillSoon = profile.refillSoon
                    )
                } else {
                    deriveMedicationAnalytics(
                        events = doseEvents,
                        supplyRemaining = profile.supplyRemaining,
                        refillThreshold = profile.refillThreshold
                    )
                }
            )
        }

    override suspend fun saveMedicationPlan(plan: MedicationPlan) {
        medicationDao.insertMedicationPlan(plan.copy(notes = stripLegacyMedicationMetadata(plan.notes)).toEntity())
        saveMedicationSchedule(
            plan.schedule ?: buildLegacyMedicationSchedule(
                medicationPlanId = plan.id,
                primaryReminderMinute = plan.reminderMinuteOfDay,
                secondaryReminderMinute = null,
                plannerVisible = plan.isActive
            )
        )
        saveMedicationSafetyProfile(
            plan.safetyProfile ?: legacySafetyProfile(
                planId = plan.id,
                unit = plan.unit,
                notes = stripLegacyMedicationMetadata(plan.notes),
                takeWithFood = plan.takeWithFood,
                refillNeededAfterDoses = plan.refillNeededAfterDoses,
                legacyTiming = null
            )
        )
    }

    override suspend fun saveMedicationSchedule(schedule: MedicationSchedule) {
        val now = Instant.now()
        medicationScheduleDao.upsertSchedule(schedule.toEntity(createdAt = now, updatedAt = now))
    }

    override suspend fun saveMedicationSafetyProfile(profile: MedicationSafetyProfile) {
        medicationSafetyProfileDao.upsertProfile(profile.toEntity())
    }

    override suspend fun addMedicationDoseEvent(event: MedicationDoseEvent) {
        medicationDoseEventDao.insertEvent(event.toEntity())
    }

    override suspend fun deleteMedicationPlan(plan: MedicationPlan) =
        medicationDao.deleteMedicationPlan(plan.toEntity())

    private fun legacySecondaryReminderMinute(notes: String?): Int? {
        val marker = Regex("""\[\[reminder2:(\d{1,4})]]""").find(notes.orEmpty())
        return marker?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 1439)
    }

    private fun legacyMealTiming(notes: String?): String? = when {
        notes.isNullOrBlank() -> null
        notes.contains("[[timing:with_food]]") -> "With food"
        notes.contains("[[timing:before_bed]]") -> "Before bed"
        notes.contains("[[timing:anytime]]") -> "Anytime"
        else -> null
    }

    private fun stripLegacyMedicationMetadata(notes: String?): String? = notes
        ?.replace(Regex("""\[\[reminder2:\d{1,4}]]\s*"""), "")
        ?.replace(Regex("""\[\[timing:[a-z_]+]]\s*"""), "")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun legacySafetyProfile(
        planId: String,
        unit: String,
        notes: String?,
        takeWithFood: Boolean,
        refillNeededAfterDoses: Int?,
        legacyTiming: String?
    ): MedicationSafetyProfile {
        val normalizedForm = when (unit.trim().lowercase()) {
            "tablet", "capsule", "liquid", "drop", "inhaler", "injection", "patch", "powder" -> unit.trim().lowercase()
            "ml" -> "liquid"
            else -> "tablet"
        }
        return MedicationSafetyProfile(
            medicationPlanId = planId,
            form = normalizedForm,
            route = if (normalizedForm == "inhaler") "inhaled" else "oral",
            instructions = notes,
            mealTiming = when {
                takeWithFood -> "With food"
                legacyTiming != null -> legacyTiming
                else -> "Anytime"
            },
            supplyRemaining = refillNeededAfterDoses,
            refillThreshold = refillNeededAfterDoses
        )
    }

    private fun estimateLegacyMedicationAdherence(missedCount: Int): Float {
        val expected = (missedCount + 7).coerceAtLeast(7)
        return ((expected - missedCount).toFloat() / expected.toFloat()).coerceIn(0f, 1f)
    }
}
