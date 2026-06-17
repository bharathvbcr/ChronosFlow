package com.chronosflow.assist

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.chronosflow.core.ai.ProactiveAssistGenerator
import com.chronosflow.core.ai.ProactiveAssistInput
import com.chronosflow.core.ai.genai.GenAiAssistCoordinator
import com.chronosflow.core.data.usage.ScreenTimeSyncManager
import com.chronosflow.core.domain.model.distractionNudge
import com.chronosflow.core.domain.repository.AppUsageRepository
import com.chronosflow.core.domain.usecase.GetChronosDayOverviewUseCase
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pre-generates the proactive daily digest while the app is foregrounded (Gemini Nano inference is
 * foreground-only) so background surfaces can read it from the cache without live inference. Skips
 * when a fresh same-day digest is already cached, so it runs at most a couple of times per day.
 */
@Singleton
class ProactiveAssistForegroundRefresher @Inject constructor(
    private val dayOverviewUseCase: GetChronosDayOverviewUseCase,
    private val proactiveAssistGenerator: ProactiveAssistGenerator,
    private val genAiAssistCoordinator: GenAiAssistCoordinator,
    private val appUsageRepository: AppUsageRepository,
    private val screenTimeSyncManager: ScreenTimeSyncManager
) : DefaultLifecycleObserver {
    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(Dispatchers.Default) {
            // Warm the on-device model first so the first plan/assist request of this foreground
            // session isn't cold. Runs regardless of whether the digest is already cached below.
            runCatching { genAiAssistCoordinator.prewarm() }
            runCatching { refreshDigest() }
        }
    }

    private suspend fun refreshDigest() {
        val today = LocalDate.now()
        val todayIso = today.toString()
        val nowEpochMs = System.currentTimeMillis()
        if (proactiveAssistGenerator.cachedCopy(todayIso, nowEpochMs) != null) return

        val overview = dayOverviewUseCase()
        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
        val screenTime = screenTimeSignals(today)
        val input = ProactiveAssistInput(
            dateIso = todayIso,
            plannedMinutes = overview.blocks.sumOf {
                (it.endMinuteOfDay - it.startMinuteOfDay).coerceAtLeast(0)
            },
            completedBlocks = overview.blocks.count { it.endMinuteOfDay <= nowMinute },
            openTaskCount = overview.openTasks.size,
            dueMedicationCount = overview.medications.count {
                !it.isTakenToday && it.reminderMinuteOfDay <= nowMinute
            },
            nextBlockTitle = overview.nextBlock?.title,
            focusedMinutes = screenTime.focusedMinutes,
            focusGoalMinutes = screenTime.focusGoalMinutes,
            distractionAboveUsual = screenTime.distractionAboveUsual
        )
        proactiveAssistGenerator.refresh(input, nowEpochMs)
    }

    private data class ScreenTimeSignals(
        val focusedMinutes: Int = 0,
        val focusGoalMinutes: Int = 0,
        val distractionAboveUsual: Boolean = false
    )

    /**
     * Reads today's focus/distraction signals from the screen-time store, but only when usage access
     * is granted and there is tracked data today — so the digest never references focus data for
     * users who haven't enabled screen time. The 14-day window matches the in-app distraction nudge.
     */
    private suspend fun screenTimeSignals(today: LocalDate): ScreenTimeSignals {
        if (!screenTimeSyncManager.status().hasAccess) return ScreenTimeSignals()
        val window = runCatching {
            appUsageRepository.getForDateRange(today.minusDays(13), today)
        }.getOrDefault(emptyList())
        val todayUsage = window.firstOrNull { it.date == today }?.takeIf { it.totalMinutes > 0 }
            ?: return ScreenTimeSignals()
        return ScreenTimeSignals(
            focusedMinutes = todayUsage.productiveMinutes,
            focusGoalMinutes = screenTimeSyncManager.focusGoalMinutes(),
            distractionAboveUsual = distractionNudge(window, today) != null
        )
    }
}
