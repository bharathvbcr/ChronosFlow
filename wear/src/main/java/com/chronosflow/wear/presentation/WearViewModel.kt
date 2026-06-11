package com.chronosflow.wear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.chronosflow.core.domain.wear.WearActionContract
import com.chronosflow.wear.DaySummaryStore
import com.chronosflow.wear.WearActionSender
import com.chronosflow.wear.WearFocusStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Backs the watch app. Reads the phone mirrors as reactive state ([daySummary], [focus]) and
 * turns user taps into [WearActionSender] messages.
 *
 * Per-item actions (habit / task / dose) update the local mirror *optimistically* so the wrist
 * feels instant; the phone then performs the real mutation and re-publishes the authoritative
 * summary, which reconciles any drift. Focus controls are not optimistically applied — the live
 * timer math is owned by the phone — so the Focus screen reflects the mirror as soon as it lands.
 */
class WearViewModel(application: Application) : AndroidViewModel(application) {

    val daySummary = DaySummaryStore.state(application)
    val focus = WearFocusStateStore.state(application)

    /** One-shot success label ("Done" / "Taken") for the confirmation overlay; null when idle. */
    private val _confirmation = MutableStateFlow<String?>(null)
    val confirmation: StateFlow<String?> = _confirmation.asStateFlow()

    fun dismissConfirmation() {
        _confirmation.value = null
    }

    fun markHabitDone(habitId: String) {
        val current = daySummary.value
        val updated = current.habits.map { if (it.id == habitId && !it.done) it.copy(done = true) else it }
        if (updated != current.habits) {
            DaySummaryStore.write(
                getApplication(),
                current.copy(
                    habits = updated,
                    habitsDone = updated.count { it.done }
                )
            )
        }
        WearActionSender.send(getApplication(), WearActionContract.TYPE_HABIT, habitId)
        _confirmation.value = "Done"
    }

    fun completeTask(taskId: String) {
        val current = daySummary.value
        val remaining = current.tasks.filterNot { it.id == taskId }
        if (remaining.size != current.tasks.size) {
            DaySummaryStore.write(
                getApplication(),
                current.copy(
                    tasks = remaining,
                    openTaskCount = (current.openTaskCount - 1).coerceAtLeast(0)
                )
            )
        }
        WearActionSender.send(getApplication(), WearActionContract.TYPE_TASK, taskId)
        _confirmation.value = "Done"
    }

    fun takeDose(planId: String) {
        val current = daySummary.value
        val updated = current.meds.map { if (it.id == planId && !it.taken) it.copy(taken = true) else it }
        if (updated != current.meds) {
            DaySummaryStore.write(
                getApplication(),
                current.copy(
                    meds = updated,
                    medsDueCount = updated.count { !it.taken }
                )
            )
        }
        WearActionSender.send(getApplication(), WearActionContract.TYPE_DOSE, planId)
        _confirmation.value = "Taken"
    }

    /** Starts a focus session; [durationMinutes] null defers to the phone's default length. */
    fun startFocus(durationMinutes: Int? = null) =
        sendFocus(WearActionContract.focusStart(durationMinutes?.let { it * 60 }))

    fun pauseFocus() = sendFocus(WearActionContract.FOCUS_PAUSE)
    fun resumeFocus() = sendFocus(WearActionContract.FOCUS_RESUME)
    fun stopFocus() = sendFocus(WearActionContract.FOCUS_STOP)

    private fun sendFocus(action: String) {
        WearActionSender.send(getApplication(), WearActionContract.TYPE_FOCUS, action)
    }
}
