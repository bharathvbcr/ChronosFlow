package com.chronosflow.assist

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.chronosflow.core.ai.ProactiveAssistGenerator
import com.chronosflow.core.ai.ProactiveAssistInput
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
    private val proactiveAssistGenerator: ProactiveAssistGenerator
) : DefaultLifecycleObserver {
    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(Dispatchers.Default) {
            runCatching { refreshDigest() }
        }
    }

    private suspend fun refreshDigest() {
        val todayIso = LocalDate.now().toString()
        val nowEpochMs = System.currentTimeMillis()
        if (proactiveAssistGenerator.cachedCopy(todayIso, nowEpochMs) != null) return

        val overview = dayOverviewUseCase()
        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
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
            nextBlockTitle = overview.nextBlock?.title
        )
        proactiveAssistGenerator.refresh(input, nowEpochMs)
    }
}
