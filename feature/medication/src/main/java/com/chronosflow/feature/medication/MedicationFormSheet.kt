package com.chronosflow.feature.medication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.domain.model.MedicationSafetyProfile
import com.chronosflow.core.domain.model.MedicationSchedule
import com.chronosflow.core.domain.model.PlannerRecurrence
import com.chronosflow.core.domain.model.PlannerRecurrenceType
import com.chronosflow.core.ui.components.GenAiAssistBanner
import com.chronosflow.core.ui.components.ChronosSpeechInputButton
import com.chronosflow.core.ui.components.ChronosFormBottomSheet
import com.chronosflow.core.ui.components.ChronosModalActionLabels
import com.chronosflow.core.ui.components.ChronosFormPreviewCard
import com.chronosflow.core.ui.components.commandPaletteSpeechQuery
import com.chronosflow.core.ui.components.ChronosCollapsibleSection
import com.chronosflow.core.ui.components.ChronosFormSection
import com.chronosflow.core.ui.components.ChronosFormSwitchRow
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosOptionChips
import com.chronosflow.core.ui.components.ChronosTimePickerField
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.components.nudgeMinuteText
import com.chronosflow.core.ui.components.parseFlexibleMinute
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import com.chronosflow.core.ai.MedicationAssistRequest
import com.chronosflow.core.ai.MedicationAssistSuggestion
import com.chronosflow.core.ai.genai.GenAiAssistCopy
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.delay

internal sealed class MedicationSheetTarget {
    data class Add(val prefillName: String? = null) : MedicationSheetTarget()
    data class Edit(val plan: MedicationPlan) : MedicationSheetTarget()
}

private val medicationUnitOptions = listOf("dose", "mg", "mcg", "g", "ml", "tsp", "tbsp", "iu", "unit", "tablet", "capsule", "drop")
private val medicationFormOptions = listOf("tablet", "capsule", "liquid", "drop", "inhaler", "injection", "patch", "cream", "powder")
private val medicationRouteOptions = listOf("oral", "inhaled", "topical", "transdermal", "injection")
private val medicationDosagePresets = listOf("½", "1", "2", "5", "10")
private val medicationNameSuggestions = listOf("Vitamin D", "Ibuprofen", "Metformin", "Inhaler", "Supplement")
private val medicationReminderPresets = mapOf(
    "Morning" to 8 * 60,
    "Noon" to 12 * 60,
    "Afternoon" to 15 * 60,
    "Evening" to 18 * 60,
    "Night" to 21 * 60,
    "Custom" to null
)
private val refillCountPresets = listOf(7, 14, 30)
private val medicationFrequencyOptions = listOf(
    "As needed",
    "Once daily",
    "Twice daily",
    "3 times daily",
    "4 times daily",
    "Weekdays",
    "Mon/Wed/Fri",
    "Tue/Thu",
    "Weekly",
    "Every other day"
)
private val medicationSecondarySpacingOptions = listOf(6, 8, 10, 12)
private val medicationReminderWindowOptions = listOf(5, 10, 15, 30, 45, 60, 90, 120)
private val medicationWeekdayShortcuts = mapOf(
    "Weekdays" to setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    "Mon/Wed/Fri" to setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    "Tue/Thu" to setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
)
private val medicationTemplates = listOf(
    MedicationTemplate("Morning", "Vitamin D", "1", "tablet", 8 * 60, "Once daily", false, "Anytime"),
    MedicationTemplate("With breakfast", "Supplement", "1", "capsule", 8 * 60, "Once daily", true, "With food"),
    MedicationTemplate("Bedtime", "Evening dose", "1", "tablet", 21 * 60, "Once daily", false, "Before bed"),
    MedicationTemplate("Twice daily", "Metformin", "1", "tablet", 8 * 60, "Twice daily", true, "With food"),
    MedicationTemplate("Rescue kit", "Inhaler", "2", "dose", 9 * 60, "As needed", false, "Anytime")
)
private val medicationMealTimingOptions = listOf("Anytime", "With food", "Before bed")

@Composable
internal fun MedicationFormSheet(
    target: MedicationSheetTarget?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int, Boolean, Int?, String?, MedicationSchedule, MedicationSafetyProfile) -> Unit,
    historyTemplates: List<MedicationHistoryTemplate> = emptyList(),
    recentHistoryIds: List<String> = emptyList(),
    onHistoryTemplateSelected: (String) -> Unit = {},
    onTaken: ((MedicationPlan) -> Unit)? = null,
    onMissed: ((MedicationPlan) -> Unit)? = null,
    onSnooze: ((MedicationPlan, Int) -> Unit)? = null,
    onArchive: ((MedicationPlan) -> Unit)? = null,
    onDuplicate: ((MedicationPlan) -> Unit)? = null,
    assistState: MedicationAssistUiState = MedicationAssistUiState(),
    onRequestAssist: ((MedicationAssistRequest) -> Unit)? = null,
    onClearAssist: (() -> Unit)? = null
) {
    if (target == null) return
    val initialPlan = (target as? MedicationSheetTarget.Edit)?.plan
    val prefillName = (target as? MedicationSheetTarget.Add)?.prefillName
    val prefillDraft = remember(prefillName) {
        medicationTranscriptDraft(prefillName.orEmpty())
    }
    val planKey = initialPlan?.id ?: "new-${prefillName.orEmpty()}"
    val initialSafetyProfile = initialPlan?.safetyProfile
    val initialRecurrence = initialPlan?.schedule?.recurrence
    val initialReminderMinutes = initialRecurrence?.normalizedTimesOfDayMinutes.orEmpty()
    val initialIsPrn = initialPlan?.schedule?.isPrn == true ||
        initialRecurrence?.type == PlannerRecurrenceType.PRN
    val primaryInitialReminder = initialReminderMinutes.firstOrNull() ?: initialPlan?.reminderMinuteOfDay ?: 8 * 60
    val secondaryInitialReminder = initialReminderMinutes.drop(1).firstOrNull()
    val thirdInitialReminder = initialReminderMinutes.drop(2).firstOrNull()
    val fourthInitialReminder = initialReminderMinutes.drop(3).firstOrNull()
    val initialWindowMinutes = (initialPlan?.schedule?.windowMinutes ?: 15).coerceIn(5, 240)

    var name by rememberSaveable(planKey) {
        mutableStateOf(initialPlan?.name ?: prefillDraft.name ?: prefillName.orEmpty())
    }
    var dosage by rememberSaveable(planKey) { mutableStateOf(initialPlan?.dosage.orEmpty()) }
    var unit by rememberSaveable(planKey) { mutableStateOf(initialPlan?.unit ?: "dose") }
    var medicationForm by rememberSaveable(planKey) {
        mutableStateOf(initialPlan?.safetyProfile?.form ?: inferMedicationForm(initialPlan?.unit ?: "dose", initialPlan?.name.orEmpty()))
    }
    var route by rememberSaveable(planKey) {
        mutableStateOf(initialPlan?.safetyProfile?.route ?: inferMedicationRoute(medicationForm))
    }
    var reminderPreset by rememberSaveable(planKey) {
        mutableStateOf(
            resolveMedicationReminderPreset(primaryInitialReminder)
        )
    }
    var reminder by rememberSaveable(planKey) {
        mutableStateOf(formatDisplayMinute(primaryInitialReminder))
    }
    var mealTiming by rememberSaveable(planKey) {
        mutableStateOf(
            initialSafetyProfile?.mealTiming ?: inferMealTiming(initialPlan)
        )
    }
    var takeWithFood by rememberSaveable(planKey) { mutableStateOf(initialPlan?.takeWithFood ?: false) }
    var hasRefillTracking by rememberSaveable(planKey) {
        mutableStateOf(
            initialPlan?.safetyProfile?.supplyRemaining != null || initialPlan?.refillNeededAfterDoses != null
        )
    }
    var refillDoses by rememberSaveable(planKey) {
        mutableStateOf(
            (initialPlan?.safetyProfile?.supplyRemaining ?: initialPlan?.refillNeededAfterDoses)?.toString().orEmpty()
        )
    }
    var notes by rememberSaveable(planKey) {
        mutableStateOf(initialSafetyProfile?.instructions.orEmpty())
    }
    var pharmacyName by rememberSaveable(planKey) {
        mutableStateOf(initialSafetyProfile?.pharmacyName.orEmpty())
    }
    var prescriberName by rememberSaveable(planKey) {
        mutableStateOf(initialSafetyProfile?.prescriberName.orEmpty())
    }
    var cautions by rememberSaveable(planKey) {
        mutableStateOf(initialSafetyProfile?.cautions?.joinToString(", ").orEmpty())
    }
    var historyQuery by rememberSaveable(planKey) { mutableStateOf("") }
    var medicationCaptureContext by rememberSaveable(planKey) { mutableStateOf(prefillName.orEmpty()) }
    var frequency by rememberSaveable(planKey) {
        mutableStateOf(
            if (initialIsPrn) {
                "As needed"
            } else {
                medicationFrequencyLabel(
                    recurrence = initialRecurrence,
                    isPrn = initialIsPrn,
                    fallbackCount = initialReminderMinutes.size.coerceAtLeast(1)
                )
            }
        )
    }
    var secondaryReminder by rememberSaveable(planKey) {
        mutableStateOf(
            formatDisplayMinute(
                secondaryInitialReminder ?: ((primaryInitialReminder + 12 * 60) % (24 * 60))
            )
        )
    }
    var thirdReminder by rememberSaveable(planKey) {
        mutableStateOf(formatDisplayMinute(thirdInitialReminder ?: ((primaryInitialReminder + 16 * 60) % (24 * 60))))
    }
    var fourthReminder by rememberSaveable(planKey) {
        mutableStateOf(formatDisplayMinute(fourthInitialReminder ?: ((primaryInitialReminder + 18 * 60) % (24 * 60))))
    }
    var reminderWindowMinutes by rememberSaveable(planKey) { mutableStateOf(initialWindowMinutes) }
    var templatesExpanded by rememberSaveable(planKey) { mutableStateOf(false) }
    var historyExpanded by rememberSaveable(planKey) { mutableStateOf(false) }
    var doseExpanded by rememberSaveable(planKey) { mutableStateOf(initialPlan != null || dosage.isBlank()) }
    var reminderExpanded by rememberSaveable(planKey) {
        mutableStateOf(initialPlan != null || medicationReminderCount(frequency) > 1 || reminderPreset == "Custom")
    }
    var safetyExpanded by rememberSaveable(planKey) {
        mutableStateOf(
            initialPlan?.safetyProfile?.let { profile ->
                profile.form.isNotBlank() || profile.route.isNotBlank() ||
                    profile.pharmacyName?.isNotBlank() == true ||
                    profile.prescriberName?.isNotBlank() == true ||
                    profile.cautions.isNotEmpty()
            } == true
        )
    }
    var refillExpanded by rememberSaveable(planKey) {
        mutableStateOf(hasRefillTracking || notes.isNotBlank())
    }
    var lastAutoAssistCapture by rememberSaveable(planKey) { mutableStateOf("") }

    val parsedReminder = parseFlexibleMinute(reminder)
    val parsedSecondary = parseFlexibleMinute(secondaryReminder)
    val parsedThird = parseFlexibleMinute(thirdReminder)
    val parsedFourth = parseFlexibleMinute(fourthReminder)
    val parsedRefillCount = refillDoses.toIntOrNull()
    val normalizedReminderWindowMinutes = reminderWindowMinutes.coerceIn(5, 240)
    val isRefillError = hasRefillTracking && (parsedRefillCount == null || parsedRefillCount <= 0)
    val reminderCount = medicationReminderCount(frequency)
    val isAsNeeded = reminderCount == 0
    val needsSecondary = reminderCount >= 2
    val needsThird = reminderCount >= 3
    val needsFourth = reminderCount >= 4
    val appliedSuggestionIds = remember(planKey, assistState.suggestions) {
        mutableStateListOf<String>()
    }
    LaunchedEffect(planKey, prefillName) {
        if (initialPlan == null && !prefillName.isNullOrBlank()) {
            medicationCaptureContext = prefillName
            prefillDraft.dosage?.let { dosage = it }
            prefillDraft.unit?.let { unit = it }
            prefillDraft.frequency?.let { frequency = it }
            prefillDraft.mealTiming?.let { mealTiming = it }
            prefillDraft.form?.let { medicationForm = it }
            prefillDraft.route?.let { route = it }
            if (prefillDraft.hasRefillTracking == true) {
                hasRefillTracking = true
                refillExpanded = true
            }
        }
    }

    val visibleAssistSuggestions = assistState.suggestions.filterNot { it.id in appliedSuggestionIds }
    val contextualAssistSuggestions = assistState.suggestions
    val medicationContextQuery = listOf(name, medicationCaptureContext)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" ")
    val adaptiveMedicationHints = medicationModalAdaptiveHints(
        name = medicationContextQuery,
        dosage = dosage,
        unit = unit,
        frequency = frequency,
        mealTiming = mealTiming,
        notes = notes,
        form = medicationForm,
        suggestions = contextualAssistSuggestions
    )
    val showMedicationDoseDetails = doseExpanded || adaptiveMedicationHints.showDose
    val showMedicationReminderDetails = reminderExpanded || adaptiveMedicationHints.showReminder
    val showMedicationSafetyDetails = safetyExpanded || adaptiveMedicationHints.showSafety
    val showMedicationRefillDetails = refillExpanded || adaptiveMedicationHints.showRefill
    val autoAssistCapture = medicationAutoAssistCapture(
        name = medicationContextQuery,
        dosage = dosage,
        unit = unit,
        frequency = frequency,
        mealTiming = mealTiming,
        notes = notes,
        form = medicationForm,
        route = route
    )
    LaunchedEffect(autoAssistCapture, assistState.isLoading, onRequestAssist) {
        val requestAssist = onRequestAssist ?: return@LaunchedEffect
        if (assistState.isLoading) return@LaunchedEffect
        if (!shouldAutoRequestMedicationAssistForCapture(autoAssistCapture) || autoAssistCapture == lastAutoAssistCapture) {
            return@LaunchedEffect
        }
        delay(AUTO_ASSIST_DEBOUNCE_MS)
        if (
            medicationAutoAssistCapture(
                name = medicationContextQuery,
                dosage = dosage,
                unit = unit,
                frequency = frequency,
                mealTiming = mealTiming,
                notes = notes,
                form = medicationForm,
                route = route
            ) != autoAssistCapture
        ) {
            return@LaunchedEffect
        }
        lastAutoAssistCapture = autoAssistCapture
        requestAssist(
            MedicationAssistRequest(
                name = name.ifBlank { medicationCaptureContext },
                dosage = dosage,
                unit = unit,
                frequency = frequency,
                primaryReminderMinute = parsedReminder ?: primaryInitialReminder,
                secondaryReminderMinute = parsedSecondary,
                mealTiming = mealTiming,
                hasRefillTracking = hasRefillTracking,
                notes = listOf(notes, medicationCaptureContext)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" "),
                form = medicationForm,
                route = route
            )
        )
    }

    val isValid = name.isNotBlank() && dosage.isNotBlank() && (isAsNeeded || parsedReminder != null) &&
        (!needsSecondary || parsedSecondary != null) &&
        (!needsThird || parsedThird != null) &&
        (!needsFourth || parsedFourth != null) &&
        !isRefillError
    val validationHint = when {
        name.isBlank() -> "Enter a medication name."
        dosage.isBlank() -> "Enter an amount (or pick a preset)."
        !isAsNeeded && parsedReminder == null -> "Use a valid reminder time."
        needsSecondary && parsedSecondary == null -> "Add a valid second reminder time."
        needsThird && parsedThird == null -> "Add a valid third reminder time."
        needsFourth && parsedFourth == null -> "Add a valid fourth reminder time."
        isRefillError -> "Refill count must be a positive number."
        else -> null
    }
    val reminderTimeLabels = listOfNotNull(
        parsedReminder,
        if (needsSecondary) parsedSecondary else null,
        if (needsThird) parsedThird else null,
        if (needsFourth) parsedFourth else null
    ).distinct().sorted().map(::formatDisplayMinute)
    val previewDetail = buildString {
        append("$dosage $unit")
        append(
            " · ${
                if (isAsNeeded) {
                    "As needed"
                } else {
                    medicationPreviewScheduleText(
                        frequency = frequency,
                        reminderLabels = reminderTimeLabels,
                        fallbackMinute = parsedReminder ?: 8 * 60
                    )
                }
            }"
        )
        if (mealTiming != "Anytime") append(" · ${mealTiming.lowercase()}")
    }

    val contextualMedicationNameOptions = contextualMedicationNameSuggestions(
        name,
        visibleAssistSuggestions
    )
    val dynamicMedicationName = name.ifBlank { initialPlan?.name.orEmpty() }
    val editMedicationSubtitle = medicationEditModalDynamicSubtitle(
        medicationName = dynamicMedicationName,
        dosage = dosage,
        unit = unit,
        frequency = frequency,
        isAsNeeded = isAsNeeded,
        reminderMinute = parsedReminder ?: 8 * 60,
        mealTiming = mealTiming,
        takeWithFood = takeWithFood,
        refillCount = parsedRefillCount.takeIf { hasRefillTracking },
        reminderWindowMinutes = normalizedReminderWindowMinutes,
        notes = notes
    )
    val editMedicationTitle = medicationEditModalDynamicTitle(dynamicMedicationName)
    val title = medicationModalDynamicTitle(
        isAddMode = target is MedicationSheetTarget.Add,
        name = name,
        dosage = dosage,
        unit = unit,
        hints = adaptiveMedicationHints,
        suggestions = contextualAssistSuggestions,
        editTitle = editMedicationTitle
    )
    val subtitle = medicationModalDynamicSubtitle(
        isAddMode = target is MedicationSheetTarget.Add,
        name = name,
        dosage = dosage,
        unit = unit,
        frequency = frequency,
        hints = adaptiveMedicationHints,
        suggestions = contextualAssistSuggestions,
        editSubtitle = editMedicationSubtitle
    )
    val actionLabels = medicationModalActionLabels(target is MedicationSheetTarget.Add)

    fun applyMedicationAssistSuggestion(suggestion: MedicationAssistSuggestion) {
        when (suggestion) {
            is MedicationAssistSuggestion.Details -> {
                suggestion.name?.takeIf(String::isNotBlank)?.let { name = it }
                suggestion.dosage?.takeIf(String::isNotBlank)?.let { dosage = it }
                suggestion.unit?.takeIf(String::isNotBlank)?.let { suggestedUnit ->
                    unit = suggestedUnit
                    medicationForm = inferMedicationForm(suggestedUnit, name)
                    route = inferMedicationRoute(medicationForm)
                }
                suggestion.frequency
                    ?.takeIf { it in medicationFrequencyOptions }
                    ?.let { suggestedFrequency ->
                        frequency = suggestedFrequency
                        val count = medicationReminderCount(suggestedFrequency)
                        if (count > 1) {
                            applyEvenMedicationSpacing(
                                primaryMinute = parsedReminder ?: primaryInitialReminder,
                                count = count,
                                setSecondary = { secondaryReminder = it },
                                setThird = { thirdReminder = it },
                                setFourth = { fourthReminder = it }
                            )
                        }
                        reminderExpanded = true
                    }
                doseExpanded = true
            }
            is MedicationAssistSuggestion.Reminder -> {
                reminder = formatDisplayMinute(suggestion.primaryMinute)
                reminderPreset = resolveMedicationReminderPreset(suggestion.primaryMinute)
                suggestion.secondaryMinute?.let { secondaryReminder = formatDisplayMinute(it) }
                frequency = suggestion.frequency ?: if (suggestion.secondaryMinute != null) "Twice daily" else "Once daily"
                if (medicationReminderCount(frequency) > 1) {
                    applyEvenMedicationSpacing(
                        primaryMinute = suggestion.primaryMinute,
                        count = medicationReminderCount(frequency),
                        setSecondary = { secondaryReminder = it },
                        setThird = { thirdReminder = it },
                        setFourth = { fourthReminder = it }
                    )
                }
                reminderExpanded = true
            }
            is MedicationAssistSuggestion.MealTiming -> {
                mealTiming = suggestion.mealTiming
                takeWithFood = suggestion.mealTiming == "With food"
                reminderExpanded = true
            }
            is MedicationAssistSuggestion.RefillTracking -> {
                hasRefillTracking = true
                refillDoses = suggestion.dosesLeft.toString()
                refillExpanded = true
            }
            is MedicationAssistSuggestion.Notes -> {
                notes = suggestion.notes
                refillExpanded = true
            }
            is MedicationAssistSuggestion.FormRoute -> {
                medicationForm = suggestion.form
                route = suggestion.route
                safetyExpanded = true
            }
        }
        if (suggestion.id !in appliedSuggestionIds) {
            appliedSuggestionIds.add(suggestion.id)
        }
        if (assistState.suggestions.all { it.id in appliedSuggestionIds }) {
            onClearAssist?.invoke()
        }
    }

    ChronosFormBottomSheet(
        visible = true,
        title = title,
        subtitle = subtitle,
        confirmLabel = actionLabels.confirm,
        validationHint = validationHint,
        onDismiss = onDismiss,
        onConfirm = {
            val reminderTimes = if (isAsNeeded) {
                emptyList()
            } else {
                listOfNotNull(
                    parsedReminder,
                    if (needsSecondary) parsedSecondary else null,
                    if (needsThird) parsedThird else null,
                    if (needsFourth) parsedFourth else null
                ).distinct().sorted()
            }
            val schedule = MedicationSchedule(
                id = initialPlan?.schedule?.id.orEmpty(),
                medicationPlanId = initialPlan?.id.orEmpty(),
                recurrence = buildMedicationRecurrenceForFrequency(
                    frequency = frequency,
                    reminderTimes = reminderTimes,
                    anchorWeekday = initialPlan?.startAt?.dayOfWeek ?: LocalDate.now().dayOfWeek
                ),
                plannerVisible = if (isAsNeeded) false else initialPlan?.schedule?.plannerVisible ?: true,
                pausedUntil = initialPlan?.schedule?.pausedUntil,
                windowMinutes = normalizedReminderWindowMinutes,
                isPrn = isAsNeeded
            )
            val safetyProfile = MedicationSafetyProfile(
                medicationPlanId = initialPlan?.id.orEmpty(),
                form = medicationForm,
                route = route,
                strength = dosage.trim().takeIf { it.isNotBlank() },
                instructions = buildMedicationNotes(mealTiming, notes),
                mealTiming = mealTiming,
                supplyRemaining = if (hasRefillTracking) parsedRefillCount else null,
                refillThreshold = if (hasRefillTracking) {
                    parsedRefillCount?.let { count -> minOf(count, 7) }
                } else {
                    null
                },
                pharmacyName = pharmacyName.trim().takeIf { it.isNotBlank() },
                prescriberName = prescriberName.trim().takeIf { it.isNotBlank() },
                cautions = (cautions.split(',').map(String::trim).filter(String::isNotBlank) +
                    deriveMedicationCautions(medicationForm, mealTiming, takeWithFood)).distinct()
            )
            onConfirm(
                name,
                dosage,
                unit,
                parsedReminder ?: 9 * 60,
                takeWithFood,
                if (hasRefillTracking) parsedRefillCount else null,
                buildMedicationNotes(mealTiming, notes),
                schedule,
                safetyProfile
            )
        },
        enabled = isValid,
        onArchive = if (initialPlan != null && onArchive != null) {
            { onArchive(initialPlan); onDismiss() }
        } else {
            null
        },
        onDuplicate = if (initialPlan != null && onDuplicate != null) {
            { onDuplicate(initialPlan); onDismiss() }
        } else {
            null
        },
        duplicateLabel = actionLabels.duplicate,
        archiveLabel = actionLabels.archive
    ) {
        val selectedTemplateLabel = medicationTemplates.firstOrNull { template ->
            template.name.equals(name, ignoreCase = true) &&
                template.frequency == frequency &&
                template.reminderMinute == (parsedReminder ?: 8 * 60) &&
                template.mealTiming == mealTiming
        }?.label.orEmpty()
        ChronosFormPreviewCard(
            title = name,
            subtitle = previewDetail,
            detail = if (hasRefillTracking && parsedRefillCount != null) {
                "Refill alert at $parsedRefillCount doses left"
            } else {
                null
            }
        )
        ChronosFormSection(
            title = "Essentials",
            subtitle = "Keep the plan identity visible while details stay grouped."
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Lisinopril") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            ChronosOptionChips(
                label = "Context names",
                options = contextualMedicationNameOptions,
                selected = name,
                onSelected = { name = it }
            )
            ChronosSpeechInputButton(
                prompt = "Describe the medication exactly as you want tracked, including dose, timing, and frequency.",
                label = "Dictate medication",
                onTranscript = { transcript ->
                    val capture = commandPaletteSpeechQuery(transcript)
                    if (capture.isBlank()) return@ChronosSpeechInputButton
                    medicationCaptureContext = capture
                    val transcriptDraft = medicationTranscriptDraft(capture)
                    val requestName = name.ifBlank { transcriptDraft.name ?: capture }
                    val requestDosage = transcriptDraft.dosage ?: dosage
                    val requestUnit = transcriptDraft.unit ?: unit
                    val requestFrequency = transcriptDraft.frequency ?: frequency
                    val requestMealTiming = transcriptDraft.mealTiming ?: mealTiming
                    val requestForm = transcriptDraft.form ?: medicationForm
                    val requestRoute = transcriptDraft.route ?: route
                    val requestHasRefillTracking = transcriptDraft.hasRefillTracking ?: hasRefillTracking
                    val requestNotes = if (name.isBlank()) {
                        notes
                    } else {
                        appendMedicationTranscript(notes, capture)
                    }
                    if (name.isBlank()) {
                        name = requestName
                    } else {
                        notes = requestNotes
                    }
                    transcriptDraft.dosage?.let { dosage = it }
                    transcriptDraft.unit?.let { unit = it }
                    transcriptDraft.frequency?.let { frequency = it }
                    transcriptDraft.mealTiming?.let { mealTiming = it }
                    transcriptDraft.form?.let { medicationForm = it }
                    transcriptDraft.route?.let { route = it }
                    transcriptDraft.hasRefillTracking?.let { hasRefillTracking = it }
                    onClearAssist?.invoke()
                    lastAutoAssistCapture = medicationAutoAssistCapture(
                        name = listOf(requestName, capture)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" "),
                        dosage = requestDosage,
                        unit = requestUnit,
                        frequency = requestFrequency,
                        mealTiming = requestMealTiming,
                        notes = listOf(requestNotes, capture)
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString(" "),
                        form = requestForm,
                        route = requestRoute
                    )
                    onRequestAssist?.invoke(
                        MedicationAssistRequest(
                            name = requestName,
                            dosage = requestDosage,
                            unit = requestUnit,
                            frequency = requestFrequency,
                            primaryReminderMinute = parsedReminder ?: primaryInitialReminder,
                            secondaryReminderMinute = parsedSecondary,
                            mealTiming = requestMealTiming,
                            hasRefillTracking = requestHasRefillTracking,
                            notes = listOf(requestNotes, capture)
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinct()
                                .joinToString(" "),
                            form = requestForm,
                            route = requestRoute
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (onRequestAssist != null) {
                FilledTonalButton(
                    onClick = {
                        onRequestAssist(
                            MedicationAssistRequest(
                                name = name.ifBlank { medicationCaptureContext },
                                dosage = dosage,
                                unit = unit,
                                frequency = frequency,
                                primaryReminderMinute = parsedReminder ?: 8 * 60,
                                secondaryReminderMinute = if (needsSecondary) parsedSecondary else null,
                                mealTiming = mealTiming,
                                hasRefillTracking = hasRefillTracking,
                                notes = listOf(notes, medicationCaptureContext)
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .joinToString(" "),
                                form = medicationForm,
                                route = route
                            )
                        )
                    },
                    enabled = !assistState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            assistState.isLoading -> "Parsing medication..."
                            visibleAssistSuggestions.isNotEmpty() -> "Refresh AI suggestions"
                            else -> "Parse medication fields"
                        }
                    )
                }
            }
            assistState.assistSnapshot?.let { snapshot ->
                GenAiAssistBanner(
                    title = snapshot.bannerTitle,
                    message = snapshot.bannerMessage +
                        " Typed or Android speech input becomes editable suggestions for name, dose, cadence, timing, form, route, and refill cues. Gemini Nano is enough for extraction, but it is not medical advice; review before saving."
                )
            }
            assistState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (visibleAssistSuggestions.isNotEmpty()) {
                MedicationAssistSuggestionChips(
                    suggestions = visibleAssistSuggestions,
                    onSuggestion = ::applyMedicationAssistSuggestion
                )
            }
        }
        val contextualMedicationTemplateLabels = contextualMedicationTemplateOptions(
            name = name,
            dosage = dosage,
            unit = unit,
            frequency = frequency,
            mealTiming = mealTiming,
            notes = notes,
            suggestions = contextualAssistSuggestions,
            templates = medicationTemplates
        )
        val suggestedMedicationTemplateLabel = contextualMedicationTemplateLabels
            .firstOrNull()
            ?.takeIf {
                hasMedicationTemplateContext(
                    name = name,
                    dosage = dosage,
                    unit = unit,
                    frequency = frequency,
                    mealTiming = mealTiming,
                    notes = notes,
                    suggestions = contextualAssistSuggestions
                )
            }
        ChronosCollapsibleSection(
            title = "Quick setup",
            summary = selectedTemplateLabel.ifBlank {
                suggestedMedicationTemplateLabel?.let { "Suggested: $it" } ?: "No template applied"
            },
            expanded = templatesExpanded || selectedTemplateLabel.isNotBlank(),
            onExpandedChange = { templatesExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Templates",
                options = contextualMedicationTemplateLabels,
                selected = selectedTemplateLabel,
                onSelected = { label ->
                    medicationTemplates.firstOrNull { it.label == label }?.let { template ->
                        name = template.name
                        dosage = template.dose
                        unit = template.unit
                        medicationForm = inferMedicationForm(template.unit, template.name)
                        route = inferMedicationRoute(medicationForm)
                        reminder = formatDisplayMinute(template.reminderMinute)
                        reminderPreset = resolveMedicationReminderPreset(template.reminderMinute)
                        frequency = template.frequency
                        reminderWindowMinutes = template.windowMinutes
                        if (template.frequency == "Twice daily") {
                            secondaryReminder = formatDisplayMinute((template.reminderMinute + 12 * 60) % (24 * 60))
                        } else if (template.frequency == "As needed") {
                            secondaryReminder = formatDisplayMinute((template.reminderMinute + 12 * 60) % (24 * 60))
                            thirdReminder = formatDisplayMinute((template.reminderMinute + 16 * 60) % (24 * 60))
                            fourthReminder = formatDisplayMinute((template.reminderMinute + 18 * 60) % (24 * 60))
                        }
                        mealTiming = template.mealTiming
                        takeWithFood = template.takeWithFood
                    }
                }
            )
        }
        if (historyTemplates.isNotEmpty()) {
            val filteredHistoryTemplates = prioritizeContextualMedicationHistoryTemplates(
                templates = filterMedicationHistoryTemplates(historyTemplates, historyQuery),
                recentIds = recentHistoryIds,
                name = name,
                dosage = dosage,
                unit = unit,
                frequency = frequency,
                mealTiming = mealTiming,
                notes = notes,
                suggestions = contextualAssistSuggestions
            )
            val suggestedHistoryTemplateLabel = filteredHistoryTemplates
                .firstOrNull()
                ?.displayLabel
                ?.takeIf {
                    historyQuery.isBlank() &&
                        hasContextualMedicationHistoryMatch(
                            templates = filteredHistoryTemplates,
                            name = name,
                            dosage = dosage,
                            unit = unit,
                            frequency = frequency,
                            mealTiming = mealTiming,
                            notes = notes,
                            suggestions = contextualAssistSuggestions
                        )
                }
            val archivedTemplates = filteredHistoryTemplates.filter(MedicationHistoryTemplate::isArchived)
            val savedTemplates = filteredHistoryTemplates.filterNot(MedicationHistoryTemplate::isArchived)
            ChronosCollapsibleSection(
                title = "From history",
                summary = suggestedHistoryTemplateLabel?.let { "Suggested: $it" }
                    ?: "${historyTemplates.size} saved plans available",
                expanded = historyExpanded,
                onExpandedChange = { historyExpanded = it }
            ) {
                OutlinedTextField(
                    value = historyQuery,
                    onValueChange = { historyQuery = it },
                    label = { Text("Search history") },
                    placeholder = { Text("Filter by name, dose, timing, or archived") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (filteredHistoryTemplates.isEmpty()) {
                    Text(
                        text = "No saved or archived medication plans match that search yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (archivedTemplates.isNotEmpty()) {
                    ChronosOptionChips(
                        label = "Archived templates",
                        options = archivedTemplates.map(MedicationHistoryTemplate::id),
                        selected = "",
                        onSelected = { selectedId ->
                            archivedTemplates.firstOrNull { it.id == selectedId }?.let { template ->
                                onHistoryTemplateSelected(selectedId)
                                applyMedicationHistoryTemplate(template) { history ->
                                    name = history.name
                                    dosage = history.dose
                                    unit = history.unit
                                    medicationForm = inferMedicationForm(history.unit, history.name)
                                    route = inferMedicationRoute(medicationForm)
                                    reminder = formatDisplayMinute(history.reminderMinute)
                                    reminderPreset = resolveMedicationReminderPreset(history.reminderMinute)
                                    frequency = history.frequencyLabel
                                    secondaryReminder = formatDisplayMinute(
                                        history.secondaryReminderMinute ?: ((history.reminderMinute + 12 * 60) % (24 * 60))
                                    )
                                    thirdReminder = formatDisplayMinute(
                                        history.thirdReminderMinute ?: ((history.reminderMinute + 16 * 60) % (24 * 60))
                                    )
                                    fourthReminder = formatDisplayMinute(
                                        history.fourthReminderMinute ?: ((history.reminderMinute + 18 * 60) % (24 * 60))
                                    )
                                    reminderWindowMinutes = history.windowMinutes.coerceIn(5, 240)
                                    mealTiming = history.mealTiming
                                    takeWithFood = history.takeWithFood
                                    hasRefillTracking = history.refillNeededAfterDoses != null
                                    refillDoses = history.refillNeededAfterDoses?.toString().orEmpty()
                                    notes = history.displayNotes.orEmpty()
                                    pharmacyName = history.pharmacyName.orEmpty()
                                    prescriberName = history.prescriberName.orEmpty()
                                    cautions = history.cautions.joinToString(", ")
                                }
                            }
                        },
                        optionLabel = { selectedId ->
                            archivedTemplates.first { it.id == selectedId }.displayLabel
                        }
                    )
                }
                if (savedTemplates.isNotEmpty()) {
                    ChronosOptionChips(
                        label = "Saved plans",
                        options = savedTemplates.map(MedicationHistoryTemplate::id),
                        selected = "",
                        onSelected = { selectedId ->
                            savedTemplates.firstOrNull { it.id == selectedId }?.let { template ->
                                onHistoryTemplateSelected(selectedId)
                                applyMedicationHistoryTemplate(template) { history ->
                                    name = history.name
                                    dosage = history.dose
                                    unit = history.unit
                                    medicationForm = inferMedicationForm(history.unit, history.name)
                                    route = inferMedicationRoute(medicationForm)
                                    reminder = formatDisplayMinute(history.reminderMinute)
                                    reminderPreset = resolveMedicationReminderPreset(history.reminderMinute)
                                    frequency = history.frequencyLabel
                                    secondaryReminder = formatDisplayMinute(
                                        history.secondaryReminderMinute ?: ((history.reminderMinute + 12 * 60) % (24 * 60))
                                    )
                                    thirdReminder = formatDisplayMinute(
                                        history.thirdReminderMinute ?: ((history.reminderMinute + 16 * 60) % (24 * 60))
                                    )
                                    fourthReminder = formatDisplayMinute(
                                        history.fourthReminderMinute ?: ((history.reminderMinute + 18 * 60) % (24 * 60))
                                    )
                                    reminderWindowMinutes = history.windowMinutes.coerceIn(5, 240)
                                    mealTiming = history.mealTiming
                                    takeWithFood = history.takeWithFood
                                    hasRefillTracking = history.refillNeededAfterDoses != null
                                    refillDoses = history.refillNeededAfterDoses?.toString().orEmpty()
                                    notes = history.displayNotes.orEmpty()
                                    pharmacyName = history.pharmacyName.orEmpty()
                                    prescriberName = history.prescriberName.orEmpty()
                                    cautions = history.cautions.joinToString(", ")
                                }
                            }
                        },
                        optionLabel = { selectedId ->
                            savedTemplates.first { it.id == selectedId }.displayLabel
                        }
                    )
                }
            }
        }
        ChronosFormSection(
            title = "Safety snapshot",
            subtitle = medicationSafetySnapshotSubtitle(
                name = name,
                dosage = dosage,
                unit = unit,
                frequency = frequency,
                mealTiming = mealTiming,
                notes = notes,
                medicationForm = medicationForm,
                route = route,
                hasRefillTracking = hasRefillTracking,
                suggestions = contextualAssistSuggestions
            )
        ) {
            ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = buildMedicationScheduleSummary(
                            parsedReminder = parsedReminder,
                            needsSecondary = needsSecondary,
                            parsedSecondary = parsedSecondary,
                            needsThird = needsThird,
                            parsedThird = parsedThird,
                            needsFourth = needsFourth,
                            parsedFourth = parsedFourth,
                            isAsNeeded = isAsNeeded,
                            frequency = frequency,
                            windowMinutes = normalizedReminderWindowMinutes
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildMedicationSafetySummary(
                            missedCount = initialPlan?.missedCount ?: 0,
                            refillCount = if (hasRefillTracking) parsedRefillCount else null,
                            mealTiming = mealTiming
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (initialPlan != null && (onTaken != null || onMissed != null || onSnooze != null)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (onTaken != null) {
                        FilledTonalButton(
                            onClick = {
                                onTaken(initialPlan)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    contentDescription = medicationFormTakenActionLabel(initialPlan)
                                }
                        ) { Text("Taken") }
                    }
                    if (onMissed != null) {
                        OutlinedButton(
                            onClick = {
                                onMissed(initialPlan)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    contentDescription = medicationFormMissedActionLabel(initialPlan)
                                }
                        ) { Text("Missed") }
                    }
                }
                if (onSnooze != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(15, 30, 60).forEach { minutes ->
                            FilledTonalButton(
                                onClick = {
                                    onSnooze(initialPlan, minutes)
                                    onDismiss()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics {
                                        contentDescription = medicationFormSnoozeActionLabel(
                                            initialPlan,
                                            minutes
                                        )
                                    }
                            ) {
                                Text("Snooze ${minutes}m")
                            }
                        }
                    }
                }
            }
        }
        ChronosCollapsibleSection(
            title = "Dose",
            summary = if (dosage.isBlank()) "Add prescribed amount" else "$dosage $unit",
            expanded = showMedicationDoseDetails,
            onExpandedChange = { doseExpanded = it }
        ) {
            Text(
                text = "Amount",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChronosOptionChips(
                label = "Presets",
                options = contextualMedicationDosagePresets(
                    name = medicationContextQuery,
                    dosage = dosage,
                    suggestions = contextualAssistSuggestions
                ),
                selected = dosage,
                onSelected = { dosage = it }
            )
            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Custom amount") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            ChronosOptionChips(
                label = "Unit",
                options = contextualMedicationUnitOptions(
                    name = medicationContextQuery,
                    selectedUnit = unit,
                    suggestions = contextualAssistSuggestions
                ),
                selected = unit,
                onSelected = {
                    unit = it
                    medicationForm = inferMedicationForm(it, name)
                    route = inferMedicationRoute(medicationForm)
                }
            )
        }

        ChronosCollapsibleSection(
            title = "Reminder",
            summary = buildMedicationScheduleSummary(
                parsedReminder = parsedReminder,
                needsSecondary = needsSecondary,
                parsedSecondary = parsedSecondary,
                needsThird = needsThird,
                parsedThird = parsedThird,
                needsFourth = needsFourth,
                parsedFourth = parsedFourth,
                isAsNeeded = isAsNeeded,
                frequency = frequency,
                windowMinutes = normalizedReminderWindowMinutes
            ),
            expanded = showMedicationReminderDetails,
            onExpandedChange = { reminderExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Frequency",
                options = contextualMedicationFrequencyOptions(
                    name = medicationContextQuery,
                    notes = notes,
                    frequency = frequency,
                    suggestions = contextualAssistSuggestions
                ),
                selected = frequency,
                onSelected = { choice ->
                    frequency = choice
                    parsedReminder?.let { primary ->
                        applyEvenMedicationSpacing(
                            primaryMinute = primary,
                            count = medicationReminderCount(choice),
                            setSecondary = { secondaryReminder = it },
                            setThird = { thirdReminder = it },
                            setFourth = { fourthReminder = it }
                        )
                    }
                }
            )
            if (isAsNeeded) {
                ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "As needed · no scheduled reminders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                ChronosOptionChips(
                    label = "Meal timing",
                    options = contextualMedicationMealTimingOptions(
                        name = name,
                        notes = notes,
                        selectedMealTiming = mealTiming,
                        suggestions = contextualAssistSuggestions
                    ),
                    selected = mealTiming,
                    onSelected = { choice ->
                        mealTiming = choice
                        takeWithFood = choice == "With food"
                    }
                )
                return@ChronosCollapsibleSection
            }
            ChronosOptionChips(
                label = "Reminder window",
                options = medicationReminderWindowOptions.map { "$it min" },
                selected = "$normalizedReminderWindowMinutes min",
                onSelected = { label ->
                    reminderWindowMinutes = label.substringBefore(' ').toIntOrNull()
                        ?.coerceIn(5, 240)
                        ?: normalizedReminderWindowMinutes
                }
            )
            ChronosOptionChips(
                label = "Time of day",
                options = contextualMedicationReminderOptions(
                    name = name,
                    notes = notes,
                    suggestions = contextualAssistSuggestions
                ),
                selected = reminderPreset,
                onSelected = { preset ->
                    reminderPreset = preset
                    medicationReminderPresets[preset]?.let { minute ->
                        reminder = formatDisplayMinute(minute)
                    }
                },
                optionLabel = { preset ->
                    medicationReminderPresets[preset]?.let { formatDisplayMinute(it) }?.let { time ->
                        "$preset · $time"
                    } ?: preset
                }
            )
            if (parsedReminder != null) {
                ChronosListCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Reminder at ${formatDisplayMinute(parsedReminder)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        reminder = nudgeMinuteText(reminder, -30, parsedReminder ?: 8 * 60)
                        reminderPreset = "Custom"
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("−30m") }
                FilledTonalButton(
                    onClick = {
                        reminder = nudgeMinuteText(reminder, 30, parsedReminder ?: 8 * 60)
                        reminderPreset = "Custom"
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("+30m") }
            }
            if (reminderCount > 1 && parsedReminder != null) {
                FilledTonalButton(
                    onClick = {
                        applyEvenMedicationSpacing(
                            primaryMinute = parsedReminder,
                            count = reminderCount,
                            setSecondary = { secondaryReminder = it },
                            setThird = { thirdReminder = it },
                            setFourth = { fourthReminder = it }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Evenly space reminders")
                }
            }
            if (reminderPreset == "Custom") {
                ChronosTimePickerField(
                    label = "Custom time",
                    value = parsedReminder?.let(::formatDisplayMinute) ?: reminder.ifBlank { "Pick a time" },
                    selectedMinute = parsedReminder,
                    onTimeSelected = { minute ->
                        reminder = formatDisplayMinute(minute)
                        reminderPreset = "Custom"
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (needsSecondary) {
                if (parsedReminder != null) {
                    ChronosOptionChips(
                        label = "Spacing",
                        options = medicationSecondarySpacingOptions.map { "$it hours" },
                        selected = medicationSecondarySpacingOptions
                            .firstOrNull { hours ->
                                parsedSecondary == ((parsedReminder + hours * 60) % (24 * 60))
                            }?.let { "$it hours" }.orEmpty(),
                        onSelected = { label ->
                            val hours = label.substringBefore(' ').toIntOrNull() ?: 12
                            secondaryReminder = formatDisplayMinute((parsedReminder + hours * 60) % (24 * 60))
                        }
                    )
                }
                ChronosTimePickerField(
                    label = "Second reminder",
                    value = parsedSecondary?.let(::formatDisplayMinute) ?: secondaryReminder.ifBlank { "Pick a time" },
                    selectedMinute = parsedSecondary ?: parsedReminder,
                    onTimeSelected = { minute ->
                        secondaryReminder = formatDisplayMinute(minute)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (needsThird) {
                ChronosTimePickerField(
                    label = "Third reminder",
                    value = parsedThird?.let(::formatDisplayMinute) ?: thirdReminder.ifBlank { "Pick a time" },
                    selectedMinute = parsedThird ?: parsedReminder,
                    onTimeSelected = { minute ->
                        thirdReminder = formatDisplayMinute(minute)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (needsFourth) {
                ChronosTimePickerField(
                    label = "Fourth reminder",
                    value = parsedFourth?.let(::formatDisplayMinute) ?: fourthReminder.ifBlank { "Pick a time" },
                    selectedMinute = parsedFourth ?: parsedReminder,
                    onTimeSelected = { minute ->
                        fourthReminder = formatDisplayMinute(minute)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            ChronosOptionChips(
                label = "Meal timing",
                options = contextualMedicationMealTimingOptions(
                    name = name,
                    notes = notes,
                    selectedMealTiming = mealTiming,
                    suggestions = contextualAssistSuggestions
                ),
                selected = mealTiming,
                onSelected = { choice ->
                    mealTiming = choice
                    takeWithFood = choice == "With food"
                }
            )
        }

        ChronosCollapsibleSection(
            title = "Safety",
            summary = "${medicationForm.replaceFirstChar(Char::uppercase)} · ${route.replaceFirstChar(Char::uppercase)}",
            expanded = showMedicationSafetyDetails,
            onExpandedChange = { safetyExpanded = it }
        ) {
            ChronosOptionChips(
                label = "Dosage form",
                options = contextualMedicationFormOptions(
                    name = listOf(name, dosage, unit, frequency, mealTiming, notes)
                        .joinToString(" "),
                    selectedForm = medicationForm,
                    suggestions = contextualAssistSuggestions
                ),
                selected = medicationForm,
                onSelected = {
                    medicationForm = it
                    route = inferMedicationRoute(it)
                }
            )
            ChronosOptionChips(
                label = "Route",
                options = contextualMedicationRouteOptions(
                    name = listOf(name, dosage, unit, frequency, mealTiming, notes, medicationForm)
                        .joinToString(" "),
                    selectedRoute = route,
                    suggestions = contextualAssistSuggestions
                ),
                selected = route,
                onSelected = { route = it }
            )
            OutlinedTextField(
                value = pharmacyName,
                onValueChange = { pharmacyName = it },
                label = { Text("Pharmacy (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = prescriberName,
                onValueChange = { prescriberName = it },
                label = { Text("Prescriber (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = cautions,
                onValueChange = { cautions = it },
                label = { Text("Cautions") },
                placeholder = { Text("e.g. Drowsy, no alcohol") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 3
            )
        }

        ChronosCollapsibleSection(
            title = "Refill & notes",
            summary = buildString {
                append(if (hasRefillTracking) "Refill tracking on" else "No refill tracking")
                if (notes.isNotBlank()) append(" · notes added")
            },
            expanded = showMedicationRefillDetails,
            onExpandedChange = { refillExpanded = it }
        ) {
            ChronosFormSwitchRow(
                title = "Refill tracking",
                subtitle = "Alert when doses remaining are low.",
                checked = hasRefillTracking,
                onCheckedChange = { hasRefillTracking = it }
            )
            if (hasRefillTracking) {
                ChronosOptionChips(
                    label = "Doses left",
                    options = contextualMedicationRefillCountOptions(
                        selectedRefillDoses = refillDoses,
                        suggestions = contextualAssistSuggestions
                    ),
                    selected = refillDoses,
                    onSelected = { refillDoses = it }
                )
                OutlinedTextField(
                    value = refillDoses,
                    onValueChange = { refillDoses = it.filter { char -> char.isDigit() } },
                    label = { Text("Custom count") },
                    isError = isRefillError,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("e.g. Take with water") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
        }
    }
}

private data class MedicationModalAdaptiveHints(
    val showDose: Boolean,
    val showReminder: Boolean,
    val showSafety: Boolean,
    val showRefill: Boolean,
    val contextKind: MedicationContextKind?
)

internal enum class MedicationContextKind {
    SUPPLEMENT,
    AS_NEEDED,
    INHALER,
    INJECTION,
    REFILL,
    TOPICAL,
    LIQUID,
    DROPS
}

private fun medicationDoseSectionSubtitle(
    name: String,
    dosage: String,
    unit: String,
    suggestions: List<MedicationAssistSuggestion>
): String {
    val context = medicationTemplateContextText(
        name = name,
        dosage = dosage,
        unit = unit,
        frequency = "",
        mealTiming = "",
        notes = "",
        suggestions = suggestions
    )
    val doseText = listOf(dosage.trim(), unit.trim())
        .filter(String::isNotBlank)
        .joinToString(" ")
    return when {
        context.containsAnyTemplateWord("vitamin", "supplement", "omega", "b12", "d3", "iron", "magnesium") ->
            "Supplement dose detected. Confirm the amount and unit exactly as entered."
        doseText.isNotBlank() ->
            "Detected $doseText. Adjust only if the typed or spoken amount was wrong."
        context.containsAnyTemplateWord("mg", "mcg", "iu", "tablet", "capsule", "dose", "drop", "puff", "spray") ->
            "Dose-like text detected. Pick the matching amount and unit before saving."
        else -> "Set the prescribed amount and unit."
    }
}

private fun medicationReminderSectionSubtitle(
    name: String,
    frequency: String,
    primaryMinute: Int?,
    secondaryMinute: Int?,
    thirdMinute: Int?,
    fourthMinute: Int?,
    mealTiming: String,
    windowMinutes: Int,
    suggestions: List<MedicationAssistSuggestion>
): String {
    val context = medicationTemplateContextText(
        name = name,
        dosage = "",
        unit = "",
        frequency = frequency,
        mealTiming = mealTiming,
        notes = "",
        suggestions = suggestions
    )
    val aiReminder = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Reminder)?.let { reminder ->
            listOfNotNull(
                reminder.frequency?.trim()?.takeIf(String::isNotBlank),
                reminder.primaryMinute.takeIf { it in 0 until (24 * 60) }?.let(::formatDisplayMinute)
            ).joinToString(" at ")
        }?.takeIf(String::isNotBlank)
    }
    val reminderCount = listOfNotNull(primaryMinute, secondaryMinute, thirdMinute, fourthMinute).size
    return when {
        aiReminder != null ->
            "AI suggests $aiReminder. Confirm the rhythm before saving."
        frequency.equals("As needed", ignoreCase = true) ||
            context.containsAnyTemplateWord("as needed", "prn", "rescue", "inhaler") ->
            "As-needed routine detected. Keep reminders flexible and use notes for when to take it."
        context.containsAnyTemplateWord("insulin", "injection", "inject", "shot", "syringe", "pen needle", "epipen", "epi pen", "epinephrine") ->
            "Injection context detected. Confirm dose units, route, and exact timing before saving."
        context.containsAnyTemplateWord("refill", "prescription", "rx", "pharmacy", "pick up prescription", "pickup prescription") ->
            "Refill context detected. Confirm current supply and whether reminders should continue."
        reminderCount >= 3 ->
            "Multiple daily reminders detected. Confirm spacing so doses are not clustered."
        context.containsAnyTemplateWord("morning", "breakfast", "am") ->
            "Morning timing detected. Confirm food timing and reminder window."
        context.containsAnyTemplateWord("evening", "dinner", "bedtime", "night", "pm") ->
            "Evening timing detected. Confirm whether this belongs with dinner or bedtime."
        mealTiming.isNotBlank() && mealTiming != "Anytime" ->
            "$mealTiming timing selected. Match reminders to how this medication should be taken."
        windowMinutes != 15 ->
            "$windowMinutes minute reminder window selected. Adjust if the dose needs tighter timing."
        else -> "Set the reminder rhythm and timing window."
    }
}

private fun medicationSafetySnapshotSubtitle(
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String,
    notes: String,
    medicationForm: String,
    route: String,
    hasRefillTracking: Boolean,
    suggestions: List<MedicationAssistSuggestion>
): String {
    val context = medicationTemplateContextText(name, dosage, unit, frequency, mealTiming, notes, suggestions)
    val doseText = listOf(dosage.trim(), unit.trim())
        .filter(String::isNotBlank)
        .joinToString(" ")
    return when {
        context.containsAnyTemplateWord("vitamin", "supplement", "omega", "b12", "d3", "iron", "magnesium") ->
            "Supplement-style capture detected. Review amount, timing, and food notes; AI only organizes what you entered."
        frequency.equals("As needed", ignoreCase = true) ||
            context.containsAnyTemplateWord("as needed", "prn", "rescue", "inhaler") ->
            "As-needed medication detected. Confirm route, notes, and when to use it before saving."
        context.containsAnyTemplateWord("insulin", "injection", "inject", "shot", "syringe", "pen needle", "epipen", "epi pen", "epinephrine") ->
            "Injection-style capture detected. Review dose units, route, timing, and emergency notes before saving."
        hasRefillTracking ->
            "Refill tracking is on. Confirm dose count, safety notes, and reminder rhythm before saving."
        context.containsAnyTemplateWord("refill", "prescription", "rx", "pharmacy", "pick up prescription", "pickup prescription") ->
            "Prescription refill context detected. Turn on refill tracking and add pharmacy notes if needed."
        doseText.isNotBlank() ->
            "Detected $doseText. Review dose, route, and reminder before saving."
        medicationForm.isNotBlank() && route.isNotBlank() && medicationForm != "tablet" ->
            "Review ${medicationForm.replaceFirstChar(Char::uppercase)} via $route before saving."
        else -> "Keep the plan readable before you save it."
    }
}

private fun medicationAddModalDynamicTitle(
    name: String,
    dosage: String,
    unit: String,
    hints: MedicationModalAdaptiveHints,
    suggestions: List<MedicationAssistSuggestion>
): String {
    val aiNameSuggestion = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Details)?.name?.trim()?.takeIf(String::isNotBlank)
    }
    val aiDosageSuggestion = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Details)?.let {
            it.dosage?.trim()?.takeIf(String::isNotBlank)
        }
    }
    val aiUnitSuggestion = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Details)?.let {
            it.unit?.trim()?.takeIf(String::isNotBlank)
        }
    }
    val normalized = "$name $dosage $unit".lowercase()
    return when {
        name.isBlank() && aiNameSuggestion != null ->
            "New ${aiNameSuggestion.trim().replaceFirstChar { it.uppercaseChar() }}"
        dosage.isBlank() && aiDosageSuggestion != null -> {
            val doseText = listOfNotNull(aiDosageSuggestion, aiUnitSuggestion).joinToString(" ")
            "New medication $doseText"
        }
        hints.contextKind == MedicationContextKind.SUPPLEMENT -> "New supplement routine"
        hints.contextKind == MedicationContextKind.AS_NEEDED -> "New as-needed medication"
        hints.contextKind == MedicationContextKind.INHALER -> "New inhaler routine"
        hints.contextKind == MedicationContextKind.INJECTION -> "New injection routine"
        hints.contextKind == MedicationContextKind.REFILL -> "New refill-ready medication"
        hints.contextKind == MedicationContextKind.TOPICAL -> "New topical medication"
        hints.contextKind == MedicationContextKind.LIQUID -> "New liquid medication"
        hints.contextKind == MedicationContextKind.DROPS -> "New drops routine"
        hints.showRefill -> "New medication with refill tracking"
        hints.showSafety -> "New safe medication routine"
        hints.showReminder && hints.showDose -> "New scheduled medication dose"
        hints.showReminder -> "New scheduled medication"
        hints.showDose -> "New medication dose"
        "vitamin" in normalized || "supplement" in normalized || dosage.isNotBlank() ->
            "New ${name.trim().ifBlank { "medication" }} ${if (dosage.isBlank()) "plan" else "$dosage ${unit.trim()}".trim()}"
        else -> "New medication"
    }
}

private fun medicationModalDynamicTitle(
    isAddMode: Boolean,
    name: String,
    dosage: String,
    unit: String,
    hints: MedicationModalAdaptiveHints,
    suggestions: List<MedicationAssistSuggestion>,
    editTitle: String
): String {
    return if (isAddMode) {
        medicationAddModalDynamicTitle(
            name = name,
            dosage = dosage,
            unit = unit,
            hints = hints,
            suggestions = suggestions
        )
    } else {
        editTitle
    }
}

private fun medicationAddModalDynamicSubtitle(
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    hints: MedicationModalAdaptiveHints,
    suggestions: List<MedicationAssistSuggestion>
): String {
    val normalized = "$name $frequency".lowercase()
    val aiReminderSuggestion = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Reminder)?.let {
            listOfNotNull(
                it.frequency?.trim()?.takeIf(String::isNotBlank),
                it.primaryMinute
                    .takeIf { minute -> minute in 0 until (24 * 60) }
                    ?.let(::formatDisplayMinute)
            ).joinToString(" at ")
        }
    }
    val aiFormRouteSuggestion = suggestions.firstNotNullOfOrNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.FormRoute)?.let {
            buildString {
                if (it.form.isNotBlank()) append("form ${it.form}")
                if (it.route.isNotBlank()) append(" via ${it.route}")
            }.trim().takeIf(String::isNotBlank)
        }
    }
    val doseSummary = when {
        dosage.isNotBlank() -> "$dosage ${unit.trim()}".trim()
        unit.isNotBlank() -> unit.trim()
        else -> "the detected dose"
    }
    return when {
        aiReminderSuggestion != null && dosage.isBlank() && name.isBlank() ->
            "AI suggests this rhythm: ${aiReminderSuggestion}. Fill dose and notes to finish."
        aiFormRouteSuggestion != null && hints.showSafety ->
            "AI detected $aiFormRouteSuggestion; review safety details before saving."
        hints.contextKind == MedicationContextKind.SUPPLEMENT ->
            "Supplement context detected. Confirm dose, unit, timing, and food notes from what you entered."
        hints.contextKind == MedicationContextKind.AS_NEEDED ->
            "As-needed context detected. Use notes for when to take it instead of forcing a daily reminder."
        hints.contextKind == MedicationContextKind.INHALER ->
            "Inhaler context detected. Confirm route, dose wording, and as-needed timing before saving."
        hints.contextKind == MedicationContextKind.INJECTION ->
            "Injection context detected. Confirm dose units, route, timing, and safety notes before saving."
        hints.contextKind == MedicationContextKind.REFILL ->
            "Refill context detected. Add current supply, pharmacy notes, and reminder continuity before saving."
        hints.contextKind == MedicationContextKind.TOPICAL ->
            "Topical context detected. Confirm form, route, and usage notes before saving."
        hints.contextKind == MedicationContextKind.LIQUID ->
            "Liquid medication context detected. Confirm amount, unit, route, and food notes before saving."
        hints.contextKind == MedicationContextKind.DROPS ->
            "Drop context detected. Confirm dose wording, route, and reminder spacing before saving."
        hints.showSafety ->
            "Capture form, route, and safety checks before confirming this medication."
        hints.showRefill ->
            "Track refill and note details for long-term medication continuity."
        hints.showReminder ->
            "A reminder cadence is inferred from your medication details."
        hints.showDose ->
            "Set dose and timing details using $doseSummary."
        "vitamin" in normalized || "supplement" in normalized ->
            "Schedule this routine in your day and tune reminders as needed."
        else -> "Schedule a daily reminder and track doses."
    }
}

private fun medicationModalDynamicSubtitle(
    isAddMode: Boolean,
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    hints: MedicationModalAdaptiveHints,
    suggestions: List<MedicationAssistSuggestion>,
    editSubtitle: String
): String {
    return if (isAddMode) {
        medicationAddModalDynamicSubtitle(
            name = name,
            dosage = dosage,
            unit = unit,
            frequency = frequency,
            hints = hints,
            suggestions = suggestions
        )
    } else {
        editSubtitle
    }
}

private fun medicationEditModalDynamicTitle(medicationName: String): String {
    return if (medicationName.isNotBlank()) {
        "Edit medication: ${medicationName.trim()}"
    } else {
        "Edit medication"
    }
}

private fun medicationEditModalDynamicSubtitle(
    medicationName: String,
    dosage: String,
    unit: String,
    frequency: String,
    isAsNeeded: Boolean,
    reminderMinute: Int,
    mealTiming: String,
    takeWithFood: Boolean,
    refillCount: Int?,
    reminderWindowMinutes: Int,
    notes: String
): String {
    if (medicationName.isBlank()) return "Editing medication details"
    val details = buildList {
        if (dosage.isNotBlank()) add(dosage.trim())
        if (unit.isNotBlank()) add(unit)
        add(frequency)
        if (!isAsNeeded) add(formatDisplayMinute(reminderMinute))
        if (mealTiming != "Anytime") add(mealTiming.lowercase())
        if (takeWithFood) add("with food")
        refillCount?.let { add("refill target $it") }
        if (reminderWindowMinutes != 15) add("window ${reminderWindowMinutes}m")
        if (notes.isNotBlank()) add("notes")
    }.take(4)
    return if (details.isEmpty()) {
        "Editing ${medicationName.trim()}"
    } else {
        "Editing ${medicationName.trim()} · ${details.joinToString(" · ")}"
    }
}

private data class MedicationTranscriptDraft(
    val name: String? = null,
    val dosage: String? = null,
    val unit: String? = null,
    val frequency: String? = null,
    val mealTiming: String? = null,
    val form: String? = null,
    val route: String? = null,
    val hasRefillTracking: Boolean? = null
)

private fun medicationTranscriptDraft(capture: String): MedicationTranscriptDraft {
    val normalized = capture.lowercase()
    val doseMatch = MEDICATION_TRANSCRIPT_DOSE_PATTERN.find(capture)
    val form = medicationTranscriptForm(normalized)
    return MedicationTranscriptDraft(
        name = medicationTranscriptName(normalized),
        dosage = doseMatch?.groups?.get(1)?.value?.let(::normalizeTranscriptDose),
        unit = doseMatch?.groups?.get(2)?.value?.let(::normalizeTranscriptUnit),
        frequency = medicationTranscriptFrequency(normalized),
        mealTiming = medicationTranscriptMealTiming(normalized),
        form = form,
        route = medicationTranscriptRoute(normalized, form),
        hasRefillTracking = true.takeIf { medicationTranscriptHasRefillContext(normalized) }
    )
}

private val MEDICATION_TRANSCRIPT_DOSE_PATTERN = Regex(
    """\b(\d+(?:\.\d+)?|1/2|half|one|two|three|four|five|ten)\s*(mg|mcg|g|ml|iu|i\.u\.|unit|units|tablet|tablets|tab|tabs|capsule|capsules|drop|drops|puff|puffs|tsp|tbsp)\b""",
    RegexOption.IGNORE_CASE
)

private val MEDICATION_TRANSCRIPT_EVERY_HOURS_PATTERN =
    Regex("""\bevery\s+(\d+)\s*(?:hours?|hrs?)\b""")

private fun normalizeTranscriptDose(value: String): String = when (value.trim().lowercase()) {
    "half", "1/2" -> "0.5"
    "one" -> "1"
    "two" -> "2"
    "three" -> "3"
    "four" -> "4"
    "five" -> "5"
    "ten" -> "10"
    else -> value.trim()
}

private fun normalizeTranscriptUnit(value: String): String = when (value.trim().lowercase()) {
    "iu", "i.u." -> "IU"
    "unit", "units" -> "unit"
    "tablet", "tablets", "tab", "tabs" -> "tablet"
    "capsule", "capsules" -> "capsule"
    "drop", "drops" -> "drop"
    "puff", "puffs" -> "puff"
    else -> value.trim().lowercase()
}

private fun medicationTranscriptName(normalized: String): String? = when {
    Regex("""\b(vitamin\s*d3|vitamin\s*d|vit\s*d|d3)\b""").containsMatchIn(normalized) -> "Vitamin D"
    Regex("""\b(vitamin\s*c|vit\s*c)\b""").containsMatchIn(normalized) -> "Vitamin C"
    "magnesium" in normalized -> "Magnesium"
    "metformin" in normalized -> "Metformin"
    "ibuprofen" in normalized -> "Ibuprofen"
    "tylenol" in normalized || "acetaminophen" in normalized -> "Acetaminophen"
    "zyrtec" in normalized || "allergy" in normalized || "allergies" in normalized -> "Allergy medication"
    "insulin" in normalized -> "Insulin"
    "epi pen" in normalized || "epipen" in normalized -> "EpiPen"
    "eye drop" in normalized || "eye drops" in normalized -> "Eye drops"
    "inhaler" in normalized -> "Inhaler"
    else -> null
}

private fun medicationTranscriptFrequency(normalized: String): String? {
    val everyHours = MEDICATION_TRANSCRIPT_EVERY_HOURS_PATTERN.find(normalized)
        ?.groupValues
        ?.getOrNull(1)
    return when {
        everyHours != null -> "Every $everyHours hours"
        "as needed" in normalized || Regex("""\bprn\b""").containsMatchIn(normalized) -> "As needed"
        "twice daily" in normalized ||
            "twice a day" in normalized ||
            Regex("""\b(2x|bid)\b""").containsMatchIn(normalized) -> "Twice daily"
        "three times" in normalized ||
            Regex("""\b(3x|tid)\b""").containsMatchIn(normalized) -> "Three times daily"
        "four times" in normalized ||
            Regex("""\b(4x|qid)\b""").containsMatchIn(normalized) -> "Four times daily"
        "weekly" in normalized || "once a week" in normalized -> "Weekly"
        "daily" in normalized ||
            "every day" in normalized ||
            "every morning" in normalized ||
            "every night" in normalized ||
            "at night" in normalized -> "Daily"
        else -> null
    }
}

private fun medicationTranscriptMealTiming(normalized: String): String? = when {
    "empty stomach" in normalized || "before food" in normalized || "before meal" in normalized -> "Empty stomach"
    "with food" in normalized ||
        "with meal" in normalized ||
        "after food" in normalized ||
        "after meal" in normalized ||
        "breakfast" in normalized ||
        "lunch" in normalized ||
        "dinner" in normalized -> "With food"
    else -> null
}

private fun medicationTranscriptForm(normalized: String): String? = when {
    "insulin" in normalized || "injection" in normalized || "inject" in normalized || "shot" in normalized -> "injection"
    "inhaler" in normalized || "puff" in normalized -> "inhaler"
    "eye drop" in normalized || "eye drops" in normalized || "drop" in normalized -> "drop"
    "patch" in normalized -> "patch"
    "cream" in normalized || "ointment" in normalized || "gel" in normalized -> "cream"
    "liquid" in normalized || "syrup" in normalized || "ml" in normalized -> "liquid"
    "capsule" in normalized -> "capsule"
    "tablet" in normalized || "pill" in normalized -> "tablet"
    else -> null
}

private fun medicationTranscriptRoute(normalized: String, form: String?): String? = when {
    form == "injection" -> "injection"
    form == "inhaler" -> "inhaled"
    form == "patch" -> "transdermal"
    form == "cream" || form == "drop" || "topical" in normalized -> "topical"
    form == "tablet" || form == "capsule" || form == "liquid" || "oral" in normalized -> "oral"
    else -> null
}

private fun medicationTranscriptHasRefillContext(normalized: String): Boolean {
    return "refill" in normalized ||
        "pharmacy" in normalized ||
        "prescription" in normalized ||
        Regex("""\brx\b""").containsMatchIn(normalized)
}

private fun medicationModalActionLabels(isAddMode: Boolean): ChronosModalActionLabels {
    return ChronosModalActionLabels(
        confirm = if (isAddMode) "Add medication" else "Save medication",
        duplicate = "Duplicate medication",
        archive = if (isAddMode) "Archive medication" else "Delete medication"
    )
}

private fun medicationModalAdaptiveHints(
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String,
    notes: String,
    form: String,
    suggestions: List<MedicationAssistSuggestion>
): MedicationModalAdaptiveHints {
    val normalized = "$name $dosage $unit $frequency $mealTiming $notes $form".lowercase()
    val context = medicationTemplateContextText(
        name = name,
        dosage = dosage,
        unit = unit,
        frequency = frequency,
        mealTiming = mealTiming,
        notes = notes,
        suggestions = suggestions
    )
    val contextKind = contextualMedicationContextKind("$context $form".lowercase())
    val hasDetailsSuggestion = suggestions.any { it is MedicationAssistSuggestion.Details }
    val hasSafetySuggestion = suggestions.any { suggestion ->
        suggestion is MedicationAssistSuggestion.FormRoute ||
            suggestion is MedicationAssistSuggestion.MealTiming ||
            suggestion is MedicationAssistSuggestion.Notes ||
            suggestion is MedicationAssistSuggestion.RefillTracking
    }
    val hasRefillSuggestion = suggestions.any { suggestion ->
        suggestion is MedicationAssistSuggestion.RefillTracking ||
            suggestion is MedicationAssistSuggestion.Notes
    }

    return MedicationModalAdaptiveHints(
        showDose = dosage.isBlank() ||
            unit.isBlank() ||
            hasDetailsSuggestion ||
            MEDICATION_DOSE_WORDS.any { normalized.contains(it) },
        showReminder = suggestions.any {
            it is MedicationAssistSuggestion.Reminder || it is MedicationAssistSuggestion.MealTiming
        } || MEDICATION_REMINDER_WORDS.any { normalized.contains(it) },
        showSafety = hasSafetySuggestion ||
            contextKind == MedicationContextKind.INJECTION ||
            MEDICATION_SAFETY_WORDS.any { normalized.contains(it) },
        showRefill = hasRefillSuggestion ||
            contextKind == MedicationContextKind.REFILL ||
            MEDICATION_REFILL_WORDS.any { normalized.contains(it) },
        contextKind = contextKind
    )
}

internal fun contextualMedicationContextKind(normalizedContext: String): MedicationContextKind? {
    return when {
        normalizedContext.containsAnyTemplateWord(
            "vitamin",
            "supplement",
            "omega",
            "b12",
            "d3",
            "vitamin d",
            "vit d",
            "iron",
            "magnesium",
            "calcium",
            "zinc",
            "probiotic",
            "melatonin",
            "vitamin c",
            "vit c"
        ) -> MedicationContextKind.SUPPLEMENT
        normalizedContext.containsAnyTemplateWord("as needed", "prn", "rescue") -> MedicationContextKind.AS_NEEDED
        normalizedContext.containsAnyTemplateWord("inhaler", "puff", "spray") -> MedicationContextKind.INHALER
        normalizedContext.containsAnyTemplateWord("insulin", "injection", "inject", "shot", "syringe", "pen needle", "epipen", "epi pen", "epinephrine") ->
            MedicationContextKind.INJECTION
        normalizedContext.containsAnyTemplateWord("refill", "prescription", "rx", "pharmacy", "pick up prescription", "pickup prescription") ->
            MedicationContextKind.REFILL
        normalizedContext.containsAnyTemplateWord("drop", "drops", "eye drops", "ear drops") -> MedicationContextKind.DROPS
        normalizedContext.containsAnyTemplateWord("cream", "ointment", "gel", "topical", "patch") -> MedicationContextKind.TOPICAL
        normalizedContext.containsAnyTemplateWord("liquid", "syrup", "solution", "ml", "milliliter", "tsp", "teaspoon", "tbsp", "tablespoon") ->
            MedicationContextKind.LIQUID
        else -> null
    }
}

internal val MEDICATION_DOSE_WORDS = listOf(
    "mg",
    "mcg",
    "ml",
    "tablet",
    "capsule",
    "dose",
    "iu",
    "puff",
    "spray",
    "drop",
    "unit"
)

internal val MEDICATION_REMINDER_WORDS = listOf(
    "morning",
    "breakfast",
    "noon",
    "lunch",
    "evening",
    "dinner",
    "night",
    "bed",
    "daily",
    "twice",
    "three times",
    "3 times",
    "as needed",
    "before food",
    "after food"
)

internal val MEDICATION_SAFETY_WORDS = listOf(
    "inhaler",
    "patch",
    "drop",
    "injection",
    "topical",
    "oral",
    "inhaled",
    "insulin",
    "shot",
    "syringe",
    "pen needle",
    "epipen",
    "epi pen",
    "epinephrine",
    "nasal",
    "with food",
    "empty stomach"
)

internal val MEDICATION_REFILL_WORDS = listOf(
    "refill",
    "supply",
    "doses left",
    "pharmacy",
    "prescription",
    "rx",
    "pick up prescription",
    "pickup prescription",
    "note",
    "instructions",
    "expires",
    "remaining"
)

private const val AUTO_ASSIST_DEBOUNCE_MS = 650L
internal fun medicationAutoAssistCapture(
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String,
    notes: String,
    form: String,
    route: String
): String = listOf(
    name,
    dosage,
    unit.takeIf { dosage.isNotBlank() }.orEmpty(),
    frequency.takeUnless { it == "Once daily" }.orEmpty(),
    mealTiming.takeUnless { it == "Anytime" }.orEmpty(),
    notes,
    form.takeUnless { it == "tablet" }.orEmpty(),
    route.takeUnless { it == "oral" }.orEmpty()
)
    .joinToString(" ")
    .trim()
    .replace(Regex("\\s+"), " ")

internal fun shouldAutoRequestMedicationAssistForCapture(capture: String): Boolean {
    val normalized = capture.trim().lowercase()
    val contextKind = contextualMedicationContextKind(normalized)
    if (normalized.length < 6 && contextKind == null) return false
    return MEDICATION_DOSE_WORDS.any { normalized.contains(it) } ||
        (contextKind != null && contextKind != MedicationContextKind.SUPPLEMENT) ||
        MEDICATION_REMINDER_WORDS.any { normalized.contains(it) } ||
        MEDICATION_SAFETY_WORDS.any { normalized.contains(it) } ||
        MEDICATION_REFILL_WORDS.any { normalized.contains(it) }
}

private fun contextualMedicationNameSuggestions(
    name: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val normalized = name.lowercase()
    val suggestedNames = suggestions.mapNotNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Details)?.name?.trim()?.takeIf(String::isNotBlank)
    }
    val contextOptions = buildList {
        if ("vitamin c" in normalized || "vit c" in normalized) add("Vitamin C")
        if ("vitamin" in normalized && "vitamin c" !in normalized || "vit d" in normalized) add("Vitamin D")
        if ("magnesium" in normalized) add("Magnesium")
        if ("iron" in normalized) add("Iron")
        if ("calcium" in normalized) add("Calcium")
        if ("zinc" in normalized) add("Zinc")
        if ("probiotic" in normalized) add("Probiotic")
        if ("melatonin" in normalized) add("Melatonin")
        if ("omega" in normalized) add("Omega-3")
        if ("inhaler" in normalized || "puff" in normalized) add("Inhaler")
        if ("rescue" in normalized) add("Rescue inhaler")
        if ("syrup" in normalized || "cough" in normalized) add("Cough syrup")
        if ("hydrocortisone" in normalized || "rash" in normalized) add("Hydrocortisone cream")
        if ("drop" in normalized || "drops" in normalized) add("Eye drops")
        if ("supplement" in normalized) add("Supplement")
        if ("insulin" in normalized) add("Insulin")
        if ("epipen" in normalized || "epi pen" in normalized || "epinephrine" in normalized) add("EpiPen")
        if ("prescription" in normalized || "rx" in normalized || "pharmacy" in normalized) add("Prescription")
        if ("ibuprofen" in normalized || "pain" in normalized) add("Ibuprofen")
        if ("allergy" in normalized || "allergies" in normalized || "zyrtec" in normalized) add("Allergy medication")
        if ("metformin" in normalized) add("Metformin")
    }
    return (suggestedNames + contextOptions + medicationNameSuggestions)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase() }
        .take(6)
}

internal fun contextualMedicationDosagePresets(
    name: String,
    dosage: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val capturedDosages = MEDICATION_CAPTURE_DOSE_PATTERN.findAll(name)
        .mapNotNull { match ->
            match.groupValues.getOrNull(1)
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let(::normalizeMedicationDosagePreset)
        }
        .toList()
    val suggestedDosages = suggestions.mapNotNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Details)?.dosage?.trim()?.takeIf(String::isNotBlank)
    }
    val contextualDosages = suggestedDosages + capturedDosages
    val optionLimit = if (contextualDosages.isNotEmpty()) 4 else 6
    return (contextualDosages + dosage.trim().takeIf(String::isNotBlank) + medicationDosagePresets)
        .filterNotNull()
        .distinctBy { it.lowercase() }
        .take(optionLimit)
}

private fun normalizeMedicationDosagePreset(value: String): String {
    return if (value == "1/2") "½" else value
}

private val MEDICATION_CAPTURE_DOSE_PATTERN = Regex(
    """\b(½|1/2|\d+(?:\.\d+)?)\s*(?:mg|mcg|g|ml|teaspoons?|tsps?|tsp|tablespoons?|tbsps?|tbsp|iu|units?|tablets?|tabs?|capsules?|caps?|drops?|puffs?|sprays?|doses?|dose)\b""",
    RegexOption.IGNORE_CASE
)

internal fun contextualMedicationUnitOptions(
    name: String,
    selectedUnit: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val normalized = name.lowercase()
    val suggestedUnits = suggestions.mapNotNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Details)
            ?.unit
            ?.trim()
            ?.lowercase()
            ?.takeIf { it in medicationUnitOptions }
    }
    val contextUnits = buildList {
        if ("vitamin" in normalized || "supplement" in normalized || Regex("""\b\d+\s*iu\b""").containsMatchIn(normalized)) {
            add("iu")
        }
        if ("syrup" in normalized || "liquid" in normalized || Regex("""\b\d+\s*(?:teaspoons?|tsps?|tsp)\b""").containsMatchIn(normalized)) {
            add("tsp")
        }
        if (Regex("""\b\d+\s*(?:tablespoons?|tbsps?|tbsp)\b""").containsMatchIn(normalized)) {
            add("tbsp")
        }
        if (Regex("""\b\d+\s*mcg\b""").containsMatchIn(normalized)) add("mcg")
        if (Regex("""\b\d+\s*mg\b""").containsMatchIn(normalized)) add("mg")
        if (Regex("""\b\d+\s*g\b""").containsMatchIn(normalized)) add("g")
        if ("liquid" in normalized || Regex("""\b\d+\s*ml\b""").containsMatchIn(normalized)) add("ml")
        if ("insulin" in normalized || "injection" in normalized || "inject" in normalized || "shot" in normalized) add("unit")
        if ("capsule" in normalized || " cap" in normalized) add("capsule")
        if ("tablet" in normalized || "pill" in normalized) add("tablet")
        if ("drop" in normalized || "drops" in normalized) add("drop")
        if ("inhaler" in normalized || "puff" in normalized || "spray" in normalized) add("dose")
    }

    val contextualUnits = suggestedUnits + contextUnits
    val optionLimit = if (contextualUnits.isNotEmpty()) 5 else 6
    return (contextualUnits + listOfNotNull(selectedUnit.takeIf { it in medicationUnitOptions }) + medicationUnitOptions)
        .distinct()
        .take(optionLimit)
}

internal fun contextualMedicationFrequencyOptions(
    name: String,
    notes: String,
    frequency: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val normalized = "$name $notes".lowercase()
    val contextFrequencies = buildList {
        if (Regex("""\b(as needed|prn|rescue)\b""").containsMatchIn(normalized)) add("As needed")
        if (Regex("""\b(epipen|epi pen|epinephrine|headache|pain|migraine|allergy attack)\b""").containsMatchIn(normalized)) add("As needed")
        if (Regex("""\b(every other day|alternate days?)\b""").containsMatchIn(normalized)) add("Every other day")
        if (Regex("""\b(weekday|weekdays|workday|workdays)\b""").containsMatchIn(normalized)) add("Weekdays")
        if (Regex("""\b(mon/wed/fri|monday wednesday friday)\b""").containsMatchIn(normalized)) add("Mon/Wed/Fri")
        if (Regex("""\b(tue/thu|tuesday thursday)\b""").containsMatchIn(normalized)) add("Tue/Thu")
        if (Regex("""\b(weekly|once a week)\b""").containsMatchIn(normalized)) add("Weekly")
        if (Regex("""\bevery\s+(?:6|six)\s+hours?\b""").containsMatchIn(normalized)) add("4 times daily")
        if (Regex("""\bevery\s+(?:8|eight)\s+hours?\b""").containsMatchIn(normalized)) add("3 times daily")
        if (Regex("""\bevery\s+(?:12|twelve)\s+hours?\b""").containsMatchIn(normalized)) add("Twice daily")
        if (Regex("""\b(4 times|four times|4x)\b""").containsMatchIn(normalized)) add("4 times daily")
        if (Regex("""\b(3 times|three times|3x)\b""").containsMatchIn(normalized)) add("3 times daily")
        if (Regex("""\b(twice|two times|2x)\b""").containsMatchIn(normalized)) add("Twice daily")
        if (Regex("""\b(daily|every day|once daily|morning|night|bedtime)\b""").containsMatchIn(normalized)) add("Once daily")
    }
    val suggestedFrequencies = suggestions.mapNotNull { suggestion ->
        when (suggestion) {
            is MedicationAssistSuggestion.Details -> suggestion.frequency
            is MedicationAssistSuggestion.Reminder -> suggestion.frequency
            else -> null
        }?.takeIf { it in medicationFrequencyOptions }
    }
    val contextualFrequencies = suggestedFrequencies + contextFrequencies
    val optionLimit = if (contextualFrequencies.isNotEmpty()) 5 else 6
    return (contextualFrequencies + frequency.takeIf { it in medicationFrequencyOptions } + medicationFrequencyOptions)
        .filterNotNull()
        .distinct()
        .take(optionLimit)
}

private fun appendMedicationTranscript(existing: String, transcript: String): String {
    val cleanTranscript = transcript.trim()
    if (cleanTranscript.isBlank()) return existing
    return listOf(existing.trim(), cleanTranscript)
        .filter(String::isNotBlank)
        .joinToString("\n")
}

internal fun contextualMedicationReminderOptions(
    name: String,
    notes: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val normalized = "$name $notes".lowercase()
    val suggestedReminders = suggestions.mapNotNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.Reminder)
            ?.primaryMinute
            ?.let(::resolveMedicationReminderPreset)
            ?.takeIf { it in medicationReminderPresets }
    }
    val contextReminders = buildList {
        if ("morning" in normalized || "breakfast" in normalized) add("Morning")
        if ("noon" in normalized || "lunch" in normalized) add("Noon")
        if ("afternoon" in normalized) add("Afternoon")
        if ("evening" in normalized || "dinner" in normalized) add("Evening")
        if ("night" in normalized || "bed" in normalized || "bedtime" in normalized) add("Night")
    }

    val contextualReminders = suggestedReminders + contextReminders
    val optionLimit = if (contextualReminders.isNotEmpty()) 5 else 6
    return (contextualReminders + medicationReminderPresets.keys)
        .distinct()
        .take(optionLimit)
}

internal fun contextualMedicationMealTimingOptions(
    name: String,
    notes: String,
    selectedMealTiming: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val normalized = "$name $notes".lowercase()
    val suggestedTimings = suggestions.mapNotNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.MealTiming)
            ?.mealTiming
            ?.takeIf { it in medicationMealTimingOptions }
    }
    val contextTimings = buildList {
        if (
            "with food" in normalized ||
            "food" in normalized ||
            "meal" in normalized ||
            "breakfast" in normalized ||
            "lunch" in normalized ||
            "dinner" in normalized
        ) {
            add("With food")
        }
        if ("bed" in normalized || "bedtime" in normalized || "night" in normalized) {
            add("Before bed")
        }
        if ("as needed" in normalized || "prn" in normalized || "rescue" in normalized) {
            add("Anytime")
        }
    }

    val contextualTimings = suggestedTimings + contextTimings
    val optionLimit = if (contextualTimings.isNotEmpty()) 2 else medicationMealTimingOptions.size
    return (
        contextualTimings +
            listOfNotNull(selectedMealTiming.takeIf { it in medicationMealTimingOptions }) +
            medicationMealTimingOptions
        )
        .distinct()
        .take(optionLimit)
}

internal fun contextualMedicationFormOptions(
    name: String,
    selectedForm: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val normalized = name.lowercase()
    val suggestedForms = suggestions.mapNotNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.FormRoute)
            ?.form
            ?.takeIf { it in medicationFormOptions }
    }
    val contextForms = buildList {
        if ("inhaler" in normalized) add("inhaler")
        if ("patch" in normalized) add("patch")
        if ("drop" in normalized || "drops" in normalized) add("drop")
        if ("injection" in normalized || "inject" in normalized || "shot" in normalized || "insulin" in normalized || "syringe" in normalized || "pen needle" in normalized || "epipen" in normalized || "epi pen" in normalized || "epinephrine" in normalized) add("injection")
        if ("liquid" in normalized || "ml" in normalized || "milliliter" in normalized || "tsp" in normalized || "teaspoon" in normalized || "tbsp" in normalized || "tablespoon" in normalized) add("liquid")
        if ("powder" in normalized) add("powder")
        if ("capsule" in normalized) add("capsule")
        if (
            "tablet" in normalized ||
            "pill" in normalized ||
            "vitamin" in normalized ||
            "supplement" in normalized ||
            "magnesium" in normalized ||
            "calcium" in normalized ||
            "zinc" in normalized ||
            "probiotic" in normalized ||
            "iu" in normalized
        ) {
            add("tablet")
        }
        if ("cream" in normalized || "ointment" in normalized || "gel" in normalized || "topical" in normalized) {
            add("cream")
        }
    }

    val contextualForms = suggestedForms + contextForms
    val optionLimit = if (contextualForms.isNotEmpty()) 5 else 6
    return (contextualForms + listOfNotNull(selectedForm.takeIf(String::isNotBlank)) + medicationFormOptions)
        .distinct()
        .take(optionLimit)
}

internal fun contextualMedicationRouteOptions(
    name: String,
    selectedRoute: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val normalized = name.lowercase()
    val suggestedRoutes = suggestions.mapNotNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.FormRoute)
            ?.route
            ?.takeIf { it in medicationRouteOptions }
    }
    val contextRoutes = buildList {
        if ("inhaler" in normalized || "inhaled" in normalized) add("inhaled")
        if ("patch" in normalized) add("transdermal")
        if (
            "cream" in normalized ||
            "ointment" in normalized ||
            "gel" in normalized ||
            "topical" in normalized ||
            "drop" in normalized
        ) {
            add("topical")
        }
        if ("injection" in normalized || "inject" in normalized || "shot" in normalized || "insulin" in normalized || "syringe" in normalized || "pen needle" in normalized || "epipen" in normalized || "epi pen" in normalized || "epinephrine" in normalized) add("injection")
        if (
            "tablet" in normalized ||
            "capsule" in normalized ||
            "pill" in normalized ||
            "vitamin" in normalized ||
            "supplement" in normalized ||
            "magnesium" in normalized ||
            "calcium" in normalized ||
            "zinc" in normalized ||
            "probiotic" in normalized ||
            "iu" in normalized
        ) {
            add("oral")
        }
    }

    val contextualRoutes = suggestedRoutes + contextRoutes
    val optionLimit = if (contextualRoutes.isNotEmpty()) 5 else 6
    return (contextualRoutes + listOfNotNull(selectedRoute.takeIf(String::isNotBlank)) + medicationRouteOptions)
        .distinct()
        .take(optionLimit)
}

internal fun contextualMedicationRefillCountOptions(
    selectedRefillDoses: String,
    suggestions: List<MedicationAssistSuggestion>
): List<String> {
    val suggestedCounts = suggestions.mapNotNull { suggestion ->
        (suggestion as? MedicationAssistSuggestion.RefillTracking)
            ?.dosesLeft
            ?.takeIf { it > 0 }
            ?.toString()
    }
    val contextualCounts = suggestedCounts + listOf(selectedRefillDoses.trim().takeIf(String::isNotBlank))
    val optionLimit = if (contextualCounts.filterNotNull().isNotEmpty()) 3 else 6
    return (
        contextualCounts +
            refillCountPresets.map(Int::toString)
        )
        .filterNotNull()
        .distinct()
        .take(optionLimit)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationAssistSuggestionChips(
    suggestions: List<MedicationAssistSuggestion>,
    onSuggestion: (MedicationAssistSuggestion) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            val sourceLabel = GenAiAssistCopy.routineAssistSourceLabel(suggestion.source)
            FilterChip(
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Apply suggestion: ${suggestion.label}. $sourceLabel. ${suggestion.reason}"
                },
                selected = false,
                onClick = { onSuggestion(suggestion) },
                label = {
                    Column {
                        Text(suggestion.label)
                        Text(
                            text = "Tap to apply",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = suggestion.reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}

private fun contextualMedicationTemplateOptions(
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String,
    notes: String,
    suggestions: List<MedicationAssistSuggestion>,
    templates: List<MedicationTemplate>
): List<String> {
    val context = medicationTemplateContextText(name, dosage, unit, frequency, mealTiming, notes, suggestions)
    return templates
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<MedicationTemplate>> {
                contextualMedicationTemplateScore(it.value, context, dosage, unit, frequency, mealTiming)
            }.thenBy { it.index }
        )
        .map { it.value.label }
}

private fun hasMedicationTemplateContext(
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String,
    notes: String,
    suggestions: List<MedicationAssistSuggestion>
): Boolean = medicationTemplateContextText(name, dosage, unit, frequency, mealTiming, notes, suggestions).isNotBlank()

private val MEDICATION_SUPPLEMENT_TEMPLATE_WORDS = arrayOf(
    "vitamin",
    "supplement",
    "omega",
    "b12",
    "d3",
    "vitamin d",
    "vit d",
    "vitamin c",
    "vit c",
    "magnesium",
    "iron",
    "calcium",
    "zinc",
    "probiotic",
    "melatonin"
)

private val MEDICATION_AS_NEEDED_TEMPLATE_WORDS = arrayOf(
    "as needed",
    "prn",
    "rescue",
    "inhaler",
    "pain",
    "headache",
    "migraine",
    "allergy",
    "allergies",
    "rash",
    "itching",
    "cough",
    "cold",
    "fever",
    "nausea",
    "heartburn"
)

private val MEDICATION_TOPICAL_TEMPLATE_WORDS = arrayOf(
    "cream",
    "ointment",
    "gel",
    "topical",
    "patch",
    "rash",
    "itching",
    "hydrocortisone"
)

private val MEDICATION_LIQUID_TEMPLATE_WORDS = arrayOf(
    "liquid",
    "syrup",
    "solution",
    "ml",
    "milliliter",
    "milliliters",
    "tsp",
    "teaspoon",
    "teaspoons",
    "tbsp",
    "tablespoon",
    "tablespoons",
    "cough"
)

private val MEDICATION_DROP_TEMPLATE_WORDS = arrayOf(
    "drop",
    "drops",
    "eye drops",
    "ear drops"
)

private fun contextualMedicationTemplateScore(
    template: MedicationTemplate,
    context: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String
): Int {
    if (context.isBlank()) return 0
    val templateText = "${template.label} ${template.name} ${template.dose} ${template.unit} ${template.frequency} ${template.mealTiming}".lowercase()
    val templateWords = templateText.contextTemplateWords()
    var score = templateWords.count { word -> word in context } * 4
    if (dosage.isNotBlank() && dosage.trim() == template.dose.trim()) score += 8
    if (unit.isNotBlank() && unit.equals(template.unit, ignoreCase = true)) score += 6
    if (frequency.isNotBlank() && frequency.equals(template.frequency, ignoreCase = true)) score += 6
    if (mealTiming.isNotBlank() && mealTiming.equals(template.mealTiming, ignoreCase = true)) score += 5
    score += medicationTemplateTimingScore(template.reminderMinute, context)
    if (context.containsAnyTemplateWord(*MEDICATION_SUPPLEMENT_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_SUPPLEMENT_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord(*MEDICATION_AS_NEEDED_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_AS_NEEDED_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord(*MEDICATION_TOPICAL_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_TOPICAL_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord(*MEDICATION_LIQUID_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_LIQUID_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord(*MEDICATION_DROP_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_DROP_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    return score
}

private fun medicationTemplateTimingScore(reminderMinute: Int, context: String): Int = when {
    context.containsAnyTemplateWord("morning", "breakfast", "am") && reminderMinute in 4 * 60 until 12 * 60 -> 5
    context.containsAnyTemplateWord("afternoon", "lunch") && reminderMinute in 12 * 60 until 17 * 60 -> 5
    context.containsAnyTemplateWord("evening", "dinner", "night", "pm") && reminderMinute in 17 * 60 until 24 * 60 -> 5
    else -> 0
}

private fun medicationTemplateContextText(
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String,
    notes: String,
    suggestions: List<MedicationAssistSuggestion>
): String = buildString {
    append(name)
    append(' ')
    append(dosage)
    append(' ')
    append(unit)
    append(' ')
    append(frequency)
    append(' ')
    append(mealTiming)
    append(' ')
    append(notes)
    suggestions.forEach { suggestion ->
        append(' ')
        append(suggestion.label)
        append(' ')
        append(suggestion.reason)
    }
}.lowercase()

private fun String.contextTemplateWords(): Set<String> {
    return lowercase()
        .split(Regex("""[^a-z0-9]+"""))
        .mapNotNull { word -> word.takeIf { it.length >= 3 } }
        .toSet()
}

private fun String.containsAnyTemplateWord(vararg words: String): Boolean {
    return words.any { word ->
        val normalized = word.lowercase()
        if (normalized.any { !it.isLetterOrDigit() }) {
            normalized in this
        } else {
            Regex("""\b${Regex.escape(normalized)}\b""").containsMatchIn(this)
        }
    }
}

internal fun editableSummaryCardContentDescription(
    title: String,
    value: String,
    actionLabel: String,
    expanded: Boolean
): String {
    val currentValue = value.trim().ifBlank { "Not set" }
    val state = if (expanded) "Expanded" else "Collapsed"
    return "${actionLabel.trim()} for ${title.trim()}. Current value: $currentValue. $state"
}

internal fun medicationFormTakenActionLabel(plan: MedicationPlan): String =
    "Mark ${plan.name} taken from medication form"

internal fun medicationFormMissedActionLabel(plan: MedicationPlan): String =
    "Mark ${plan.name} missed from medication form"

internal fun medicationFormSnoozeActionLabel(plan: MedicationPlan, minutes: Int): String =
    "Snooze ${plan.name} for $minutes minutes from medication form"

private fun buildMedicationNotes(mealTiming: String, userNotes: String): String? {
    return userNotes.trim().takeIf { it.isNotBlank() }
}

private fun resolveMedicationReminderPreset(minuteOfDay: Int): String =
    medicationReminderPresets.entries.firstOrNull { it.value == minuteOfDay }?.key ?: "Custom"

private fun inferMealTiming(initialPlan: MedicationPlan?): String = when {
        initialPlan?.takeWithFood == true -> "With food"
        initialPlan?.notes?.contains("before bed", ignoreCase = true) == true -> "Before bed"
        else -> "Anytime"
    }

private fun buildMedicationScheduleSummary(
    parsedReminder: Int?,
    needsSecondary: Boolean,
    parsedSecondary: Int?,
    needsThird: Boolean = false,
    parsedThird: Int? = null,
    needsFourth: Boolean = false,
    parsedFourth: Int? = null,
    isAsNeeded: Boolean = false,
    frequency: String = "Once daily",
    windowMinutes: Int = 15
): String = buildString {
    if (isAsNeeded) {
        append("As needed")
        return@buildString
    }
    val times = listOfNotNull(
        parsedReminder,
        if (needsSecondary) parsedSecondary else null,
        if (needsThird) parsedThird else null,
        if (needsFourth) parsedFourth else null
    ).distinct().sorted()
    val cadence = frequency.takeUnless { it == "Once daily" }
    if (cadence != null) {
        append(cadence)
        append(" · ")
    }
    append(if (times.size <= 1) "Reminder " else "Reminders ")
    append(times.ifEmpty { listOf(8 * 60) }.joinToString(" & ") { formatDisplayMinute(it) })
    append(" · ${windowMinutes.coerceIn(5, 240)} min window")
}

private fun medicationPreviewScheduleText(
    frequency: String,
    reminderLabels: List<String>,
    fallbackMinute: Int
): String {
    val times = reminderLabels.joinToString(" & ").ifBlank { formatDisplayMinute(fallbackMinute) }
    return if (frequency == "Once daily") {
        times
    } else {
        "$frequency · $times"
    }
}

internal fun buildMedicationRecurrenceForFrequency(
    frequency: String,
    reminderTimes: List<Int>,
    anchorWeekday: DayOfWeek
): PlannerRecurrence = when (frequency) {
    "As needed" -> PlannerRecurrence(
        type = PlannerRecurrenceType.PRN,
        timesOfDayMinutes = emptyList()
    )
    "Weekdays" -> PlannerRecurrence(
        type = PlannerRecurrenceType.WEEKDAYS,
        weekdays = medicationWeekdayShortcuts.getValue("Weekdays"),
        timesOfDayMinutes = reminderTimes
    )
    "Mon/Wed/Fri",
    "Tue/Thu" -> PlannerRecurrence(
        type = PlannerRecurrenceType.SELECTED_WEEKDAYS,
        weekdays = medicationWeekdayShortcuts.getValue(frequency),
        timesOfDayMinutes = reminderTimes
    )
    "Weekly" -> PlannerRecurrence(
        type = PlannerRecurrenceType.WEEKLY_INTERVAL,
        interval = 1,
        weekdays = setOf(anchorWeekday),
        timesOfDayMinutes = reminderTimes
    )
    "Every other day" -> PlannerRecurrence(
        type = PlannerRecurrenceType.EVERY_N_DAYS,
        interval = 2,
        timesOfDayMinutes = reminderTimes
    )
    else -> PlannerRecurrence(
        type = if (reminderTimes.size > 1) {
            PlannerRecurrenceType.MULTIPLE_TIMES_DAILY
        } else {
            PlannerRecurrenceType.DAILY
        },
        timesOfDayMinutes = reminderTimes
    )
}

private fun medicationFrequencyLabel(
    recurrence: PlannerRecurrence?,
    isPrn: Boolean,
    fallbackCount: Int
): String = when {
    isPrn || recurrence?.type == PlannerRecurrenceType.PRN -> "As needed"
    recurrence?.type == PlannerRecurrenceType.WEEKDAYS -> "Weekdays"
    recurrence?.type == PlannerRecurrenceType.SELECTED_WEEKDAYS ->
        medicationWeekdayShortcuts.entries.firstOrNull { it.value == recurrence.weekdays }?.key ?: "Weekly"
    recurrence?.type == PlannerRecurrenceType.WEEKLY_INTERVAL -> "Weekly"
    recurrence?.type == PlannerRecurrenceType.EVERY_N_DAYS && recurrence.interval == 2 -> "Every other day"
    else -> medicationFrequencyLabel(fallbackCount)
}

private fun medicationFrequencyLabel(count: Int): String = when (count.coerceIn(1, 4)) {
    1 -> "Once daily"
    2 -> "Twice daily"
    3 -> "3 times daily"
    else -> "4 times daily"
}

private fun medicationReminderCount(frequency: String): Int = when (frequency) {
    "As needed" -> 0
    "Twice daily" -> 2
    "3 times daily" -> 3
    "4 times daily" -> 4
    else -> 1
}

private inline fun applyEvenMedicationSpacing(
    primaryMinute: Int,
    count: Int,
    setSecondary: (String) -> Unit,
    setThird: (String) -> Unit,
    setFourth: (String) -> Unit
) {
    val safeCount = count.coerceIn(1, 4)
    val step = (24 * 60) / safeCount
    if (safeCount >= 2) setSecondary(formatDisplayMinute((primaryMinute + step) % (24 * 60)))
    if (safeCount >= 3) setThird(formatDisplayMinute((primaryMinute + step * 2) % (24 * 60)))
    if (safeCount >= 4) setFourth(formatDisplayMinute((primaryMinute + step * 3) % (24 * 60)))
}

private fun buildMedicationSafetySummary(
    missedCount: Int,
    refillCount: Int?,
    mealTiming: String
): String = buildString {
    append(if (mealTiming == "Anytime") "Flexible timing" else mealTiming)
    append(" · ")
    append(
        when {
            missedCount <= 0 -> "No missed doses tracked"
            missedCount == 1 -> "1 missed dose logged"
            else -> "$missedCount missed doses logged"
        }
    )
    refillCount?.let {
        append(if (it <= 3) " · refill soon" else " · refill at $it doses")
    }
}

private data class MedicationTemplate(
    val label: String,
    val name: String,
    val dose: String,
    val unit: String,
    val reminderMinute: Int,
    val frequency: String,
    val takeWithFood: Boolean,
    val mealTiming: String,
    val windowMinutes: Int = 15
)

internal data class MedicationHistoryTemplate(
    val id: String,
    val name: String,
    val dose: String,
    val unit: String,
    val reminderMinute: Int,
    val secondaryReminderMinute: Int?,
    val takeWithFood: Boolean,
    val mealTiming: String,
    val refillNeededAfterDoses: Int?,
    val displayNotes: String?,
    val form: String,
    val route: String,
    val pharmacyName: String?,
    val prescriberName: String?,
    val cautions: List<String>,
    val isArchived: Boolean,
    val thirdReminderMinute: Int? = null,
    val fourthReminderMinute: Int? = null,
    val isPrn: Boolean = false,
    val recurrenceType: PlannerRecurrenceType = PlannerRecurrenceType.DAILY,
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdays: Set<DayOfWeek> = emptySet(),
    val windowMinutes: Int = 15
) {
    val sourceLabel: String
        get() = if (isArchived) "Archived" else "Saved"

    val frequencyLabel: String
        get() = medicationFrequencyLabel(
            recurrence = PlannerRecurrence(
                type = recurrenceType,
                interval = recurrenceInterval,
                weekdays = recurrenceWeekdays,
                timesOfDayMinutes = listOfNotNull(
                    reminderMinute,
                    secondaryReminderMinute,
                    thirdReminderMinute,
                    fourthReminderMinute
                )
            ),
            isPrn = isPrn,
            fallbackCount = listOfNotNull(
                reminderMinute,
                secondaryReminderMinute,
                thirdReminderMinute,
                fourthReminderMinute
            ).size.coerceAtLeast(1)
        )

    val displayLabel: String
        get() = "$name · $frequencyLabel · $sourceLabel"
}

internal fun buildMedicationHistoryTemplates(
    plans: List<MedicationPlan>,
    currentPlanId: String? = null
): List<MedicationHistoryTemplate> = plans
    .asSequence()
    .filter { it.id != currentPlanId }
    .map { plan ->
        val recurrence = plan.schedule?.recurrence
        val isPrn = plan.schedule?.isPrn == true || recurrence?.type == PlannerRecurrenceType.PRN
        val reminderTimes = if (isPrn) {
            emptyList()
        } else {
            recurrence?.normalizedTimesOfDayMinutes.orEmpty()
        }
        val safetyProfile = plan.safetyProfile
        MedicationHistoryTemplate(
            id = plan.id,
            name = plan.name,
            dose = plan.dosage,
            unit = plan.unit,
            reminderMinute = reminderTimes.firstOrNull() ?: plan.reminderMinuteOfDay,
            secondaryReminderMinute = reminderTimes.drop(1).firstOrNull(),
            takeWithFood = plan.takeWithFood,
            mealTiming = safetyProfile?.mealTiming ?: inferMealTiming(plan),
            refillNeededAfterDoses = safetyProfile?.supplyRemaining ?: plan.refillNeededAfterDoses,
            displayNotes = safetyProfile?.instructions,
            form = safetyProfile?.form ?: inferMedicationForm(plan.unit, plan.name),
            route = safetyProfile?.route ?: inferMedicationRoute(safetyProfile?.form ?: inferMedicationForm(plan.unit, plan.name)),
            pharmacyName = safetyProfile?.pharmacyName,
            prescriberName = safetyProfile?.prescriberName,
            cautions = safetyProfile?.cautions.orEmpty(),
            isArchived = !plan.isActive,
            thirdReminderMinute = reminderTimes.drop(2).firstOrNull(),
            fourthReminderMinute = reminderTimes.drop(3).firstOrNull(),
            isPrn = isPrn,
            recurrenceType = recurrence?.type ?: PlannerRecurrenceType.DAILY,
            recurrenceInterval = recurrence?.interval ?: 1,
            recurrenceWeekdays = recurrence?.weekdays.orEmpty(),
            windowMinutes = (plan.schedule?.windowMinutes ?: 15).coerceIn(5, 240)
        )
    }
    .sortedWith(
        compareBy<MedicationHistoryTemplate>({ !it.isArchived }, { it.reminderMinute }, { it.name.lowercase() })
    )
    .distinctBy { template ->
        listOf(
            template.name.lowercase(),
            template.dose.lowercase(),
            template.unit.lowercase(),
            template.reminderMinute.toString(),
            template.secondaryReminderMinute?.toString().orEmpty(),
            template.thirdReminderMinute?.toString().orEmpty(),
            template.fourthReminderMinute?.toString().orEmpty(),
            template.isPrn.toString(),
            template.recurrenceType.name,
            template.recurrenceInterval.toString(),
            template.recurrenceWeekdays.joinToString(",") { it.name },
            template.windowMinutes.toString(),
            template.mealTiming.lowercase(),
            template.refillNeededAfterDoses?.toString().orEmpty(),
            template.displayNotes.orEmpty().lowercase(),
            template.form.lowercase(),
            template.route.lowercase(),
            template.pharmacyName.orEmpty().lowercase(),
            template.prescriberName.orEmpty().lowercase(),
            template.cautions.joinToString("|").lowercase()
        ).joinToString("|")
    }
    .take(8)
    .toList()

internal fun filterMedicationHistoryTemplates(
    templates: List<MedicationHistoryTemplate>,
    query: String
): List<MedicationHistoryTemplate> {
    val terms = query.trim().split(medicationHistoryQueryTokenSplit).filter { it.isNotBlank() }
    if (terms.isEmpty()) return templates
    return templates.filter { template ->
        terms.all(template::matchesMedicationHistoryTerm)
    }
}

private val medicationHistoryQueryTokenSplit = Regex("\\s+")

private fun MedicationHistoryTemplate.matchesMedicationHistoryTerm(term: String): Boolean =
    name.contains(term, ignoreCase = true) ||
        dose.contains(term, ignoreCase = true) ||
        unit.contains(term, ignoreCase = true) ||
        frequencyLabel.contains(term, ignoreCase = true) ||
        windowMinutes.toString().contains(term, ignoreCase = true) ||
        "min window".contains(term, ignoreCase = true) ||
        mealTiming.contains(term, ignoreCase = true) ||
        sourceLabel.contains(term, ignoreCase = true) ||
        displayNotes.orEmpty().contains(term, ignoreCase = true) ||
        form.contains(term, ignoreCase = true) ||
        route.contains(term, ignoreCase = true) ||
        pharmacyName.orEmpty().contains(term, ignoreCase = true) ||
        prescriberName.orEmpty().contains(term, ignoreCase = true) ||
        cautions.any { it.contains(term, ignoreCase = true) }

internal fun prioritizeRecentMedicationHistoryTemplates(
    templates: List<MedicationHistoryTemplate>,
    recentIds: List<String>
): List<MedicationHistoryTemplate> {
    if (templates.isEmpty() || recentIds.isEmpty()) return templates
    val positions = recentIds.withIndex().associate { it.value to it.index }
    return templates.sortedWith(
        compareBy<MedicationHistoryTemplate>({ positions[it.id] == null }, { positions[it.id] ?: Int.MAX_VALUE })
    )
}

internal fun prioritizeContextualMedicationHistoryTemplates(
    templates: List<MedicationHistoryTemplate>,
    recentIds: List<String>,
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String,
    notes: String,
    suggestions: List<MedicationAssistSuggestion>
): List<MedicationHistoryTemplate> {
    val recentRanked = prioritizeRecentMedicationHistoryTemplates(templates, recentIds)
    val context = medicationTemplateContextText(name, dosage, unit, frequency, mealTiming, notes, suggestions)
    if (context.isBlank()) return recentRanked
    return recentRanked
        .withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<MedicationHistoryTemplate>> {
                contextualMedicationHistoryScore(it.value, context, dosage, unit, frequency, mealTiming)
            }.thenBy { it.index }
        )
        .map { it.value }
}

private fun hasContextualMedicationHistoryMatch(
    templates: List<MedicationHistoryTemplate>,
    name: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String,
    notes: String,
    suggestions: List<MedicationAssistSuggestion>
): Boolean {
    val context = medicationTemplateContextText(name, dosage, unit, frequency, mealTiming, notes, suggestions)
    return context.isNotBlank() && templates.any { template ->
        contextualMedicationHistoryScore(template, context, dosage, unit, frequency, mealTiming) > 0
    }
}

private fun contextualMedicationHistoryScore(
    template: MedicationHistoryTemplate,
    context: String,
    dosage: String,
    unit: String,
    frequency: String,
    mealTiming: String
): Int {
    val templateText = buildString {
        append(template.name)
        append(' ')
        append(template.dose)
        append(' ')
        append(template.unit)
        append(' ')
        append(template.frequencyLabel)
        append(' ')
        append(template.mealTiming)
        append(' ')
        append(template.displayNotes.orEmpty())
        append(' ')
        append(template.form)
        append(' ')
        append(template.route)
    }.lowercase()
    var score = templateText.contextTemplateWords().count { word -> word in context } * 4
    if (dosage.isNotBlank() && dosage.trim() == template.dose.trim()) score += 8
    if (unit.isNotBlank() && unit.equals(template.unit, ignoreCase = true)) score += 6
    if (frequency.isNotBlank() && frequency.equals(template.frequencyLabel, ignoreCase = true)) score += 6
    if (mealTiming.isNotBlank() && mealTiming.equals(template.mealTiming, ignoreCase = true)) score += 5
    score += medicationTemplateTimingScore(template.reminderMinute, context)
    if (context.containsAnyTemplateWord(*MEDICATION_SUPPLEMENT_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_SUPPLEMENT_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord(*MEDICATION_AS_NEEDED_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_AS_NEEDED_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord(*MEDICATION_TOPICAL_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_TOPICAL_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord(*MEDICATION_LIQUID_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_LIQUID_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    if (context.containsAnyTemplateWord(*MEDICATION_DROP_TEMPLATE_WORDS) &&
        templateText.containsAnyTemplateWord(*MEDICATION_DROP_TEMPLATE_WORDS)
    ) {
        score += 8
    }
    return score
}

internal fun parseRecentMedicationTemplateIds(raw: String): List<String> = raw
    .split('|')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .take(5)

internal fun encodeRecentMedicationTemplateIds(ids: List<String>): String = ids
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .take(5)
    .joinToString("|")

private inline fun applyMedicationHistoryTemplate(
    template: MedicationHistoryTemplate,
    apply: (MedicationHistoryTemplate) -> Unit
) {
    apply(template)
}

private fun inferMedicationForm(unit: String, name: String): String {
    val normalizedUnit = unit.trim().lowercase()
    val normalizedName = name.trim().lowercase()
    return when {
        normalizedUnit in setOf("tablet", "capsule", "drop", "inhaler", "injection", "patch", "cream", "powder", "liquid") -> normalizedUnit
        normalizedUnit == "ml" || normalizedUnit == "tsp" || normalizedUnit == "tbsp" -> "liquid"
        "inhaler" in normalizedName -> "inhaler"
        "patch" in normalizedName -> "patch"
        "cream" in normalizedName || "ointment" in normalizedName || "gel" in normalizedName || "topical" in normalizedName -> "cream"
        else -> "tablet"
    }
}

private fun inferMedicationRoute(form: String): String = when (form) {
    "inhaler" -> "inhaled"
    "drop", "cream" -> "topical"
    "patch" -> "transdermal"
    "injection" -> "injection"
    else -> "oral"
}

private fun deriveMedicationCautions(
    form: String,
    mealTiming: String,
    takeWithFood: Boolean
): List<String> = buildList {
    if (takeWithFood || mealTiming == "With food") add("Take with food")
    if (mealTiming == "Before bed") add("Bedtime routine")
    if (form == "inhaler") add("Track rescue usage")
}
