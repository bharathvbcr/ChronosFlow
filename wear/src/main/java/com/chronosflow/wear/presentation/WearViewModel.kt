package com.chronosflow.wear.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chronosflow.core.domain.wear.WearActionContract
import com.chronosflow.wear.DaySummaryStore
import com.chronosflow.wear.WearActionSender
import com.chronosflow.wear.WearFocusStateStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /**
     * Where the watch is in pulling a fresh mirror from the phone. Drives the never-synced Now
     * screen: [SYNCING] shows active progress instead of an instant "open on phone" that looks
     * broken; [UNREACHABLE] means the phone didn't answer (offline / Play services absent) so the
     * open-on-phone escape hatch is the right call. Once a mirror lands it returns to [IDLE].
     */
    enum class SyncPhase { IDLE, SYNCING, UNREACHABLE }

    private val _syncPhase = MutableStateFlow(SyncPhase.IDLE)
    val syncPhase: StateFlow<SyncPhase> = _syncPhase.asStateFlow()

    private var syncTimeoutJob: Job? = null

    init {
        // Clear the syncing/unreachable hint the moment a real mirror arrives.
        viewModelScope.launch {
            daySummary.collect { summary ->
                if (summary.receivedAtMillis > 0L && _syncPhase.value != SyncPhase.IDLE) {
                    _syncPhase.value = SyncPhase.IDLE
                }
            }
        }
    }

    /** One-shot success label ("Done" / "Taken") for the confirmation overlay; null when idle. */
    private val _confirmation = MutableStateFlow<String?>(null)
    val confirmation: StateFlow<String?> = _confirmation.asStateFlow()

    /** Set when a completion couldn't be delivered to the phone; drives the failure overlay. */
    private val _failure = MutableStateFlow<String?>(null)
    val failure: StateFlow<String?> = _failure.asStateFlow()

    /**
     * Asks the phone to push a fresh day-summary mirror. Sent when the watch app opens so a stale
     * or never-synced watch reconciles immediately — the phone's other publish triggers only fire
     * on background / the widget worker, so an open-but-foreground phone wouldn't sync otherwise.
     * Best-effort and silent: a dropped message just means the watch keeps its last-known mirror.
     */
    fun requestSync() {
        // A mirror already on the watch reconciles silently in the background; only the
        // never-synced case needs the visible "Syncing…" progress.
        if (daySummary.value.receivedAtMillis == 0L) _syncPhase.value = SyncPhase.SYNCING
        WearActionSender.send(getApplication(), WearActionContract.TYPE_SYNC, "") { delivered ->
            if (!delivered && daySummary.value.receivedAtMillis == 0L) {
                _syncPhase.value = SyncPhase.UNREACHABLE
            }
        }
        // The phone answers by re-publishing; if no mirror has landed by the deadline, stop
        // implying progress so the open-on-phone hint can take over.
        syncTimeoutJob?.cancel()
        syncTimeoutJob = viewModelScope.launch {
            delay(SYNC_TIMEOUT_MILLIS)
            if (_syncPhase.value == SyncPhase.SYNCING) _syncPhase.value = SyncPhase.UNREACHABLE
        }
    }

    fun dismissConfirmation() {
        _confirmation.value = null
    }

    fun dismissFailure() {
        _failure.value = null
    }

    /**
     * Sends a per-item completion to the phone and, if it can't be delivered (phone out of reach),
     * swaps the optimistic success overlay for a failure one — so a tap that silently won't take
     * effect (the mirror will reconcile it back) isn't mistaken for a recorded action.
     */
    private fun sendItemAction(type: String, arg: String) {
        WearActionSender.send(getApplication(), type, arg) { delivered ->
            if (!delivered) {
                _confirmation.value = null
                _failure.value = "Couldn't reach phone"
            }
        }
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
        _confirmation.value = "Done"
        sendItemAction(WearActionContract.TYPE_HABIT, habitId)
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
        _confirmation.value = "Done"
        sendItemAction(WearActionContract.TYPE_TASK, taskId)
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
        _confirmation.value = "Taken"
        sendItemAction(WearActionContract.TYPE_DOSE, planId)
    }

    /**
     * Marks the block happening now complete. Optimistically clears the now-headline so the action
     * can't be double-fired; the phone applies the real completion and re-publishes the summary,
     * which reconciles the headline (and surfaces the next block).
     */
    fun completeNowBlock() {
        val current = daySummary.value
        val blockId = current.nowBlockId ?: return
        DaySummaryStore.write(
            getApplication(),
            current.copy(nowTitle = null, nowBlockId = null)
        )
        _confirmation.value = "Done"
        sendItemAction(WearActionContract.TYPE_BLOCK, blockId)
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

    private companion object {
        /** How long to show "Syncing…" before falling back to the open-on-phone hint. */
        const val SYNC_TIMEOUT_MILLIS = 6_000L
    }
}
