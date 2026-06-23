package com.ChronosFlow.VBCR.feature.daydial

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ChronosFlow.VBCR.core.data.usage.ScreenTimeSyncManager
import com.ChronosFlow.VBCR.core.data.usage.ScreenTimeSyncOutcome
import com.ChronosFlow.VBCR.core.data.usage.ScreenTimeSyncStatus
import com.ChronosFlow.VBCR.core.domain.model.AppUsageDay
import com.ChronosFlow.VBCR.core.domain.model.DistractionNudge
import com.ChronosFlow.VBCR.core.domain.model.UsageCategory
import com.ChronosFlow.VBCR.core.domain.model.distractionNudge
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageOverrideRepository
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageRepository
import com.ChronosFlow.VBCR.core.ui.components.ChronosDurationSlider
import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosSegmentedButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosSwitch
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.components.formatDurationLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** One app's foreground time today, with the category currently in effect (override or system). */
data class AppUsageBreakdownItem(
    val packageName: String,
    val label: String,
    val category: UsageCategory,
    val minutes: Int,
    val isOverridden: Boolean = false
)

/** The category options offered in the per-app re-tag control, in display order. */
private val ReTagOptions = listOf(
    UsageCategory.PRODUCTIVE to "Focus",
    UsageCategory.NEUTRAL to "Neutral",
    UsageCategory.DISTRACTING to "Distract"
)

private const val BreakdownAppCap = 8
private const val NUDGE_WINDOW_DAYS = 14

@HiltViewModel
class ScreenTimeViewModel @Inject constructor(
    private val manager: ScreenTimeSyncManager,
    private val overrideRepository: AppUsageOverrideRepository,
    appUsageRepository: AppUsageRepository
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentDateFlow = MutableStateFlow(LocalDate.now())

    private val _status = MutableStateFlow(manager.status())
    val status = _status.asStateFlow()
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _syncOutcomes = MutableSharedFlow<ScreenTimeSyncOutcome>(extraBufferCapacity = 1)
    val syncOutcomes = _syncOutcomes.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val today: StateFlow<AppUsageDay?> =
        currentDateFlow.flatMapLatest { date ->
            appUsageRepository.observeForDate(date)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A nudge when today's distraction runs above the trailing-window usual (null otherwise). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val distractionNudge: StateFlow<DistractionNudge?> =
        currentDateFlow.flatMapLatest { date ->
            appUsageRepository.observeForDateRange(date.minusDays(NUDGE_WINDOW_DAYS - 1L), date)
                .map { distractionNudge(it, date) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Raw per-app system samples for today (snapshot). The effective category is layered on by
    // combining with the live overrides, so a re-tag recolours the list instantly without a re-query.
    private val _rawSamples = MutableStateFlow<List<AppUsageBreakdownItem>>(emptyList())
    val breakdown: StateFlow<List<AppUsageBreakdownItem>> =
        combine(_rawSamples, overrideRepository.observeOverrides()) { samples, overrides ->
            samples.map {
                it.copy(
                    category = overrides[it.packageName] ?: it.category,
                    isOverridden = overrides.containsKey(it.packageName)
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _focusGoal = MutableStateFlow(manager.focusGoalMinutes())
    val focusGoal = _focusGoal.asStateFlow()

    private val _nudgeNotifications = MutableStateFlow(manager.nudgeNotificationsEnabled())
    val nudgeNotifications = _nudgeNotifications.asStateFlow()

    init {
        reloadSamples()
    }

    fun setFocusGoal(minutes: Int) {
        manager.setFocusGoal(minutes)
        _focusGoal.value = manager.focusGoalMinutes()
    }

    fun setNudgeNotifications(enabled: Boolean) {
        manager.setNudgeNotificationsEnabled(enabled)
        _nudgeNotifications.value = enabled
    }

    /** Drop a user re-tag so the app falls back to its system-derived category. */
    fun resetCategory(packageName: String) {
        viewModelScope.launch {
            overrideRepository.clearOverride(packageName)
            runNow()
        }
    }

    fun usageAccessIntent(): Intent = manager.usageAccessSettingsIntent()

    fun refresh() {
        currentDateFlow.value = LocalDate.now()
        _status.value = manager.status()
        reloadSamples()
    }

    fun setEnabled(enabled: Boolean) {
        manager.setEnabled(enabled)
        _status.value = manager.status()
    }

    /** Re-tag an app and re-aggregate stored totals so the headline + Insights reflect the change. */
    fun setCategory(packageName: String, category: UsageCategory) {
        viewModelScope.launch {
            overrideRepository.setOverride(packageName, category)
            runNow()
        }
    }

    fun runNow() {
        if (_isRunning.value) return
        _isRunning.value = true
        viewModelScope.launch {
            try {
                val outcome = manager.runSync()
                _status.value = manager.status()
                loadSamplesNow()
                _syncOutcomes.emit(outcome)
            } finally {
                _isRunning.value = false
            }
        }
    }

    private fun reloadSamples() {
        viewModelScope.launch { loadSamplesNow() }
    }

    private suspend fun loadSamplesNow() {
        _rawSamples.value = if (manager.status().hasAccess) {
            manager.breakdownForDay(LocalDate.now()).map {
                AppUsageBreakdownItem(it.packageName, it.label, it.category, it.minutes)
            }
        } else {
            emptyList()
        }
    }
}

/**
 * Screen-time import controls embedded in the Export Data panel, next to the Health Connect card.
 * Lets the user grant "Usage access", keep screen time in sync, see today's focused/distracted split
 * with a per-app drill-down, and re-tag any app. Read-only and on-device.
 */
@Composable
internal fun ScreenTimeCard(
    viewModel: ScreenTimeViewModel = hiltViewModel(),
    onStartFocus: () -> Unit = {}
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val breakdown by viewModel.breakdown.collectAsStateWithLifecycle()
    val focusGoal by viewModel.focusGoal.collectAsStateWithLifecycle()
    val nudge by viewModel.distractionNudge.collectAsStateWithLifecycle()
    val nudgeNotifications by viewModel.nudgeNotifications.collectAsStateWithLifecycle()

    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel) {
        viewModel.syncOutcomes.collect { outcome ->
            haptics.performHapticFeedback(
                if (outcome is ScreenTimeSyncOutcome.Failure) {
                    HapticFeedbackType.Reject
                } else {
                    HapticFeedbackType.Confirm
                }
            )
        }
    }

    val accessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "Screen time",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "See how much of your day goes to focused apps vs. distractions, using Android's usage " +
                "data (the same source as Digital Wellbeing). Read-only — nothing leaves your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (status.hasAccess) {
            ScreenTimeAvailableControls(
                status = status,
                isRunning = isRunning,
                today = today,
                breakdown = breakdown,
                focusGoalMinutes = focusGoal,
                nudge = nudge,
                nudgeNotificationsEnabled = nudgeNotifications,
                onToggle = { enable ->
                    viewModel.setEnabled(enable)
                    if (enable) viewModel.runNow()
                },
                onSyncNow = viewModel::runNow,
                onSetFocusGoal = viewModel::setFocusGoal,
                onSetNudgeNotifications = viewModel::setNudgeNotifications,
                onRetag = viewModel::setCategory,
                onResetTag = viewModel::resetCategory,
                onStartFocus = onStartFocus
            )
        } else {
            Text(
                "Grant ChronosFlow usage access so it can read your screen time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ChronosOutlinedButton(
                onClick = { accessLauncher.launch(viewModel.usageAccessIntent()) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant usage access")
            }
        }
    }
}

@Composable
private fun ScreenTimeAvailableControls(
    status: ScreenTimeSyncStatus,
    isRunning: Boolean,
    today: AppUsageDay?,
    breakdown: List<AppUsageBreakdownItem>,
    focusGoalMinutes: Int,
    nudge: DistractionNudge?,
    nudgeNotificationsEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSetFocusGoal: (Int) -> Unit,
    onSetNudgeNotifications: (Boolean) -> Unit,
    onRetag: (String, UsageCategory) -> Unit,
    onResetTag: (String) -> Unit,
    onStartFocus: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Keep screen time in sync",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        ChronosSwitch(checked = status.enabled, onCheckedChange = onToggle)
    }
    val focusedToday = today?.productiveMinutes ?: 0
    today?.takeIf { it.totalMinutes > 0 }?.let { day ->
        Text(
            "Today · ${formatDurationLabel(day.productiveMinutes)} focused · " +
                "${formatDurationLabel(day.distractingMinutes)} distracting",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "${(day.focusRatio * 100).roundToInt()}% of tracked screen time was focused",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    nudge?.let {
        Text(
            "More distracted than usual today — ${formatDurationLabel(it.todayDistractingMinutes)} " +
                "vs ${formatDurationLabel(it.averageDistractingMinutes)} typical. A focus block might help.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        ChronosTextButton(onClick = onStartFocus) {
            Text("Start a focus block")
        }
    }

    if (focusGoalMinutes > 0) {
        val progress = (focusedToday.toFloat() / focusGoalMinutes).coerceIn(0f, 1f)
        Text(
            "${formatDurationLabel(focusedToday)} of ${formatDurationLabel(focusGoalMinutes)} " +
                "focus goal · ${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
    ChronosDurationSlider(
        durationMinutes = focusGoalMinutes,
        onDurationChange = onSetFocusGoal,
        range = 0..480,
        label = "Daily focus goal"
    )

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Distraction reminders",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        ChronosSwitch(checked = nudgeNotificationsEnabled, onCheckedChange = onSetNudgeNotifications)
    }

    if (breakdown.isNotEmpty()) {
        AppBreakdownList(breakdown = breakdown, onRetag = onRetag, onResetTag = onResetTag)
    }

    status.lastSyncedAtMillis?.let { millis ->
        Text(
            "Last synced ${DateUtils.getRelativeTimeSpanString(millis)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (status.lastResult.isNotEmpty()) {
        Text(
            status.lastResult,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    ChronosOutlinedButton(
        onClick = onSyncNow,
        enabled = status.enabled && !isRunning,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (isRunning) "Syncing…" else "Sync now")
    }
}

@Composable
private fun AppBreakdownList(
    breakdown: List<AppUsageBreakdownItem>,
    onRetag: (String, UsageCategory) -> Unit,
    onResetTag: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ChronosTextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Hide apps" else "Apps (${breakdown.size})")
    }
    if (expanded) {
        val shown = breakdown.take(BreakdownAppCap)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { item ->
                AppBreakdownRow(item = item, onRetag = onRetag, onResetTag = onResetTag)
            }
            if (breakdown.size > shown.size) {
                Text(
                    "Showing ${shown.size} of ${breakdown.size} apps",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBreakdownRow(
    item: AppUsageBreakdownItem,
    onRetag: (String, UsageCategory) -> Unit,
    onResetTag: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatDurationLabel(item.minutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ReTagOptions.forEachIndexed { index, (category, label) ->
                ChronosSegmentedButton(
                    selected = item.category == category,
                    onClick = { if (item.category != category) onRetag(item.packageName, category) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ReTagOptions.size),
                    label = { Text(label) }
                )
            }
        }
        if (item.isOverridden) {
            ChronosTextButton(onClick = { onResetTag(item.packageName) }) {
                Text("Use default category")
            }
        }
    }
}
