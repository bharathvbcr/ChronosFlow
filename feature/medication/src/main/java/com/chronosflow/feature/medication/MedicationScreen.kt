package com.chronosflow.feature.medication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.chronosflow.core.ui.shell.ChronosModalBottomSheet
import com.chronosflow.core.ui.shell.ChronosSnackbarHost
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronosflow.core.ai.MedicationAssistRequest
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.ui.components.ChronosScreenScaffold
import com.chronosflow.core.ui.components.ChronosEmptyState
import com.chronosflow.core.ui.components.ChronosListCard
import com.chronosflow.core.ui.components.ChronosConfirmBottomSheet
import com.chronosflow.core.ui.components.ChronosMetricTile
import com.chronosflow.core.ui.components.ChronosCommandPaletteAction
import com.chronosflow.core.ui.components.ChronosPageHeader
import com.chronosflow.core.ui.components.ChronosQuickAddChips
import com.chronosflow.core.ui.components.formatDisplayMinute
import com.chronosflow.core.ui.shell.LocalChronosShellBottomInset
import com.chronosflow.core.ui.theme.ChronosSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(
    viewModel: MedicationViewModel = hiltViewModel(),
    onBack: (() -> Unit)? = null,
    onOpenCommandPalette: (() -> Unit)? = null,
    openAddSheet: Boolean = false,
    initialAddCapture: String? = null
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val recentHistoryTemplateIds by viewModel.recentHistoryTemplateIds.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val showExactAlarmPermissionAction by viewModel.showExactAlarmPermissionAction.collectAsStateWithLifecycle()
    val assistState by viewModel.assistState.collectAsStateWithLifecycle()
    val rewriteState by viewModel.rewriteState.collectAsStateWithLifecycle()
    val activePlans = plans.filter { it.isActive }
    val snackbarHostState = remember { SnackbarHostState() }
    var sheetTarget by remember { mutableStateOf<MedicationSheetTarget?>(null) }
    val normalizedInitialAddCapture = initialAddCapture?.trim()?.takeIf(String::isNotBlank)
    var initialAddConsumed by rememberSaveable(openAddSheet, normalizedInitialAddCapture) {
        mutableStateOf(false)
    }
    var planToArchive by remember { mutableStateOf<MedicationPlan?>(null) }
    var planContextTarget by remember { mutableStateOf<MedicationPlan?>(null) }
    val shellBottomInset = LocalChronosShellBottomInset.current

    LaunchedEffect(openAddSheet, normalizedInitialAddCapture, initialAddConsumed) {
        if (!openAddSheet || initialAddConsumed) return@LaunchedEffect
        sheetTarget = MedicationSheetTarget.Add(prefillName = normalizedInitialAddCapture)
        normalizedInitialAddCapture?.let { capture ->
            viewModel.requestMedicationAssist(
                MedicationAssistRequest(
                    name = capture,
                    dosage = "",
                    unit = "dose",
                    frequency = "Once daily",
                    primaryReminderMinute = 8 * 60,
                    secondaryReminderMinute = null,
                    mealTiming = "Anytime",
                    hasRefillTracking = false,
                    notes = "",
                    form = "tablet",
                    route = "oral"
                )
            )
        }
        initialAddConsumed = true
    }

    LaunchedEffect(status) {
        val message = status ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearStatus()
    }

    ChronosScreenScaffold(
        title = "Medication",
        onBack = onBack,
        actions = { ChronosCommandPaletteAction(onOpenCommandPalette) },
        snackbarHost = { ChronosSnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + shellBottomInset + ChronosSpacing.Medium
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ChronosPageHeader(
                    title = "Medication tracking",
                    subtitle = "Track active plans and create exact reminder requests for critical doses.",
                    icon = Icons.Default.Medication
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ChronosMetricTile("Active", activePlans.size.toString(), Modifier.weight(1f))
                    ChronosMetricTile("Missed", plans.sumOf { it.missedCount }.toString(), Modifier.weight(1f))
                }
            }
            item {
                FilledTonalButton(
                    onClick = { sheetTarget = MedicationSheetTarget.Add() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add medication", fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                MedicationAdherenceChart(plans = plans)
            }
            if (showExactAlarmPermissionAction) {
                item {
                    MedicationAttentionCard(
                        title = "Exact alarms are off",
                        message = "Reminders use a 10-minute fallback until exact alarms are enabled.",
                        actionLabel = "Settings",
                        onAction = viewModel::openExactAlarmSettings,
                        onDismiss = viewModel::dismissExactAlarmPermissionAction
                    )
                }
            }
            if (activePlans.isEmpty()) {
                item {
                    ChronosEmptyState(
                        title = "No medication plans",
                        message = "Add a plan to schedule a reminder and track adherence.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    ChronosQuickAddChips(
                        label = "Start quickly",
                        options = listOf("Vitamin D", "Blood pressure", "Evening dose", "Inhaler"),
                        onSelect = { name ->
                            sheetTarget = MedicationSheetTarget.Add(prefillName = name)
                        }
                    )
                }
            } else {
                items(activePlans, key = { it.id }) { plan ->
                    MedicationRow(
                        modifier = Modifier.animateItem(),
                        plan = plan,
                        onEdit = { sheetTarget = MedicationSheetTarget.Edit(plan) },
                        onTaken = { viewModel.markDoseTaken(plan) },
                        onMissed = { viewModel.markDoseMissed(plan) },
                        onSnooze = { minutes -> viewModel.snoozeReminder(plan, minutes) },
                        onSkip = { viewModel.skipDoseToday(plan) },
                        onPause = { viewModel.pausePlan(plan, days = 1) },
                        onResume = { viewModel.resumePlan(plan) },
                        onArchive = { planToArchive = plan },
                        onOpenContext = { planContextTarget = plan }
                    )
                }
            }
        }
    }

    MedicationFormSheet(
        target = sheetTarget,
        onDismiss = { sheetTarget = null },
        onConfirm = { name, dosage, unit, reminderMinute, takeWithFood, refillNeededAfterDoses, notes, schedule, safetyProfile ->
            when (val target = sheetTarget) {
                is MedicationSheetTarget.Edit -> viewModel.updateMedication(
                    target.plan,
                    name,
                    dosage,
                    unit,
                    reminderMinute,
                    takeWithFood,
                    refillNeededAfterDoses,
                    notes,
                    schedule,
                    safetyProfile
                )
                is MedicationSheetTarget.Add -> viewModel.addMedication(
                    name,
                    dosage,
                    unit,
                    reminderMinute,
                    takeWithFood,
                    refillNeededAfterDoses,
                    notes,
                    schedule,
                    safetyProfile
                )
                null -> Unit
            }
            sheetTarget = null
        },
        historyTemplates = buildMedicationHistoryTemplates(
            plans = plans,
            currentPlanId = (sheetTarget as? MedicationSheetTarget.Edit)?.plan?.id
        ),
        recentHistoryIds = recentHistoryTemplateIds,
        onHistoryTemplateSelected = viewModel::rememberHistoryTemplateSelection,
        onTaken = { plan ->
            viewModel.markDoseTaken(plan)
            sheetTarget = null
        },
        onMissed = { plan ->
            viewModel.markDoseMissed(plan)
            sheetTarget = null
        },
        onSnooze = { plan, minutes ->
            viewModel.snoozeReminder(plan, minutes)
            sheetTarget = null
        },
        onArchive = { plan -> planToArchive = plan },
        onDuplicate = { plan ->
            viewModel.duplicateMedication(plan)
            sheetTarget = null
        },
        assistState = assistState,
        onRequestAssist = viewModel::requestMedicationAssist,
        onClearAssist = viewModel::clearMedicationAssist,
        rewriteState = rewriteState,
        onRequestRewrite = viewModel::rewriteMedicationNotes,
        onClearRewrite = viewModel::clearMedicationRewrite
    )

    planContextTarget?.let { plan ->
        MedicationContextActionSheet(
            plan = plan,
            onDismiss = { planContextTarget = null },
            onTaken = {
                viewModel.markDoseTaken(plan)
                planContextTarget = null
            },
            onMissed = {
                viewModel.markDoseMissed(plan)
                planContextTarget = null
            },
            onSnooze = { minutes ->
                viewModel.snoozeReminder(plan, minutes)
                planContextTarget = null
            },
            onSkip = {
                viewModel.skipDoseToday(plan)
                planContextTarget = null
            },
            onPause = {
                viewModel.pausePlan(plan, days = 1)
                planContextTarget = null
            },
            onResume = {
                viewModel.resumePlan(plan)
                planContextTarget = null
            },
            onEdit = {
                sheetTarget = MedicationSheetTarget.Edit(plan)
                planContextTarget = null
            },
            onArchive = {
                planToArchive = plan
                planContextTarget = null
            }
        )
    }

    planToArchive?.let { plan ->
        ChronosConfirmBottomSheet(
            visible = true,
            title = medicationArchiveConfirmTitle(plan),
            message = "\"${plan.name}\" will be removed from active plans. Reminders will stop scheduling on save.",
            confirmLabel = "Archive",
            onConfirm = {
                viewModel.archive(plan)
                planToArchive = null
                sheetTarget = null
            },
            onDismiss = { planToArchive = null }
        )
    }
}

private fun medicationArchiveConfirmTitle(plan: MedicationPlan): String {
    val planName = plan.name.ifBlank { "this medication" }
    return "Archive \"$planName\"?"
}

@Composable
private fun MedicationAttentionCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    ChronosListCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
                onDismiss?.let { dismiss ->
                    TextButton(
                        onClick = dismiss,
                        modifier = Modifier.semantics {
                            contentDescription = medicationAttentionDismissActionLabel(title)
                        }
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedicationRow(
    modifier: Modifier = Modifier,
    plan: MedicationPlan,
    onEdit: () -> Unit,
    onTaken: () -> Unit,
    onMissed: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onArchive: () -> Unit,
    onOpenContext: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val analytics = plan.analytics
    val missed = analytics.missedCountLast14Days.coerceAtLeast(plan.missedCount)
    val score = analytics.adherenceRate.coerceIn(0f, 1f)
    val supplyRemaining = plan.safetyProfile?.supplyRemaining ?: plan.refillNeededAfterDoses
    val refillThreshold = plan.safetyProfile?.refillThreshold ?: plan.refillNeededAfterDoses
    val refillIsUrgent = plan.safetyProfile?.refillSoon == true ||
        (supplyRemaining != null && refillThreshold != null && supplyRemaining <= refillThreshold) ||
        (supplyRemaining != null && supplyRemaining <= 3)
    val isPaused = plan.schedule?.pausedUntil?.let { !it.isBefore(java.time.LocalDate.now()) } == true
    val recentDoseSummary = plan.recentDoseEvents.firstOrNull()?.let { event ->
        "${event.type.name.lowercase().replaceFirstChar(Char::uppercase)} · ${event.eventDate}"
    }
    val form = plan.safetyProfile?.form ?: plan.unit
    val formIcon = dosageFormIcon(form)

    ChronosListCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = medicationContextActionLabel(plan),
                role = Role.Button,
                onClick = onOpenContext
            )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Compact)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(formIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            plan.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (refillIsUrgent && supplyRemaining != null) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Report,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Low Supply",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "${plan.dosage} ${plan.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MedicationInfoPill(
                            label = "Reminder",
                            value = formatMinute(plan.reminderMinuteOfDay),
                            emphasized = true
                        )
                        MedicationInfoPill(
                            label = "Adherence",
                            value = "${(score * 100).toInt()}%"
                        )
                        MedicationInfoPill(
                            label = "Form",
                            value = form.replaceFirstChar(Char::uppercase)
                        )
                        if (isPaused) {
                            MedicationInfoPill(
                                label = "Status",
                                value = "Paused",
                                alert = true
                            )
                        }
                        supplyRemaining?.let {
                            MedicationInfoPill(
                                label = "Refill",
                                value = "$it doses",
                                alert = refillIsUrgent
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = medicationEditActionLabel(plan))
                    }
                    IconButton(onClick = onArchive) {
                        Icon(Icons.Default.Archive, contentDescription = medicationArchiveActionLabel(plan))
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = medicationDetailsActionLabel(plan, expanded)
                        )
                    }
                }
            }

            if (refillIsUrgent && supplyRemaining != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Report,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Remaining supply is low ($supplyRemaining doses left). Please refill soon.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onTaken,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = medicationTakenActionLabel(plan) }
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(medicationTakenActionLabel(plan))
                }
                OutlinedButton(
                    onClick = onMissed,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = medicationMissedActionLabel(plan) }
                ) {
                    Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(medicationMissedActionLabel(plan))
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPaused) {
                            MedicationInfoPill(
                                label = "Status",
                                value = "Paused",
                                alert = true
                            )
                        }
                        MedicationInfoPill(
                            label = "Misses",
                            value = missed.toString(),
                            alert = missed > 0
                        )
                        MedicationInfoPill(
                            label = "Meal",
                            value = plan.safetyProfile?.mealTiming ?: if (plan.takeWithFood) "With food" else "Anytime"
                        )
                        supplyRemaining?.let {
                            MedicationInfoPill(
                                label = "Refill",
                                value = "$it doses left",
                                alert = refillIsUrgent
                            )
                        }
                    }

                    plan.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Notes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    recentDoseSummary?.let { summary ->
                        Text(
                            text = "Recent dose: $summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onSkip,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = medicationSkipActionLabel(plan) },
                            enabled = !isPaused
                        ) {
                            Text("Skip today")
                        }
                        if (isPaused) {
                            FilledTonalButton(
                                onClick = onResume,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = medicationResumeActionLabel(plan) }
                            ) {
                                Text("Resume")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onPause,
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { contentDescription = medicationPauseActionLabel(plan) }
                            ) {
                                Text("Pause")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationContextActionSheet(
    plan: MedicationPlan,
    onDismiss: () -> Unit,
    onTaken: () -> Unit,
    onMissed: () -> Unit,
    onSnooze: (Int) -> Unit,
    onSkip: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val isPaused = plan.schedule?.pausedUntil?.let { !it.isBefore(java.time.LocalDate.now()) } == true
    val actionLabels = medicationContextSheetActionLabels(plan)
    ChronosModalBottomSheet(
        onDismissRequest = onDismiss,
        chromeTag = "medication-context-sheet"
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                plan.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${plan.dosage} ${plan.unit} · ${formatMinute(plan.reminderMinuteOfDay)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = onTaken,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = actionLabels.taken }
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(medicationTakenActionLabel(plan))
                }
                OutlinedButton(
                    onClick = onMissed,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = actionLabels.missed }
                ) {
                    Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(medicationMissedActionLabel(plan))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(15, 30, 60).forEach { minutes ->
                    FilledTonalButton(
                        onClick = { onSnooze(minutes) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription = medicationSnoozeActionLabel(plan, minutes)
                            },
                        enabled = !isPaused
                    ) {
                        Text("${minutes}m")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = medicationSkipActionLabel(plan) },
                    enabled = !isPaused
                ) {
                    Text(medicationSkipActionLabel(plan))
                }
                if (isPaused) {
                    FilledTonalButton(
                        onClick = onResume,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = medicationResumeActionLabel(plan) }
                    ) {
                        Text(medicationResumeActionLabel(plan))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = medicationPauseActionLabel(plan) }
                    ) {
                        Text(medicationPauseActionLabel(plan))
                    }
                }
            }
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = actionLabels.edit }
                ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(medicationEditActionLabel(plan))
            }
            TextButton(
                onClick = onArchive,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = actionLabels.archive }
                ) {
                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(medicationArchiveActionLabel(plan))
            }
        }
    }
}

internal fun medicationContextActionLabel(plan: MedicationPlan): String = "Open actions for ${plan.name}"

internal fun medicationEditActionLabel(plan: MedicationPlan): String = "Edit ${plan.name}"

internal fun medicationArchiveActionLabel(plan: MedicationPlan): String = "Archive ${plan.name}"

internal fun medicationAttentionDismissActionLabel(title: String): String = "Dismiss ${title.trim()} alert"

internal fun medicationTakenActionLabel(plan: MedicationPlan): String = "Mark ${plan.name} taken"

internal fun medicationMissedActionLabel(plan: MedicationPlan): String = "Mark ${plan.name} missed"

internal fun medicationSkipActionLabel(plan: MedicationPlan): String = "Skip ${plan.name} today"

internal fun medicationPauseActionLabel(plan: MedicationPlan): String = "Pause ${plan.name}"

internal fun medicationResumeActionLabel(plan: MedicationPlan): String = "Resume ${plan.name}"

internal fun medicationSnoozeActionLabel(plan: MedicationPlan, minutes: Int): String {
    return "Snooze ${plan.name} for $minutes minutes"
}

internal fun medicationDetailsActionLabel(plan: MedicationPlan, expanded: Boolean): String {
    val action = if (expanded) "Hide" else "Show"
    return "$action details for ${plan.name}"
}

internal data class MedicationContextSheetActionLabels(
    val taken: String,
    val missed: String,
    val edit: String,
    val archive: String
)

internal fun medicationContextSheetActionLabels(plan: MedicationPlan): MedicationContextSheetActionLabels {
    return MedicationContextSheetActionLabels(
        taken = medicationTakenActionLabel(plan),
        missed = medicationMissedActionLabel(plan),
        edit = medicationEditActionLabel(plan),
        archive = medicationArchiveActionLabel(plan)
    )
}

@Composable
private fun MedicationInfoPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    alert: Boolean = false
) {
    val containerColor = when {
        alert -> MaterialTheme.colorScheme.errorContainer
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        alert -> MaterialTheme.colorScheme.onErrorContainer
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun dosageFormIcon(form: String) = when (form.lowercase()) {
    "inhaler" -> Icons.Default.Air
    "liquid", "drop" -> Icons.Default.WaterDrop
    "injection" -> Icons.Default.Vaccines
    else -> Icons.Default.Medication
}
