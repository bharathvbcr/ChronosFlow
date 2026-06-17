package com.chronosflow.widget

import com.chronosflow.core.domain.wear.WearActionContract
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * Receives watch-originated actions over the [com.google.android.gms.wearable.MessageClient] and
 * routes each one through the *same* use cases the home-screen widgets use (via
 * [WidgetActionEntryPoint]), so a tap on the wrist and a tap on a widget are indistinguishable
 * to the rest of the app.
 *
 * After applying the action it refreshes every glanceable surface through [ChronosWidgetHub],
 * which also re-publishes the day-summary mirror — closing the loop so the watch immediately
 * reflects the new state. The Data Layer callback already runs off the main thread, so the
 * suspend use cases are driven with [runBlocking].
 */
class WearActionListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearActionContract.ACTION_PATH) return
        val (type, arg) = WearActionContract.decode(event.data) ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WidgetActionEntryPoint::class.java
        )
        // The watch is talking to us, so keep the background refresh loop armed even for users
        // with no home-screen widgets — otherwise their watch tiles/complications go stale while
        // both apps are closed (the loop is otherwise only scheduled by widgets).
        entryPoint.wearLinkStatusStore().recordWatchActivity()
        WidgetRefreshWorker.ensureScheduled(applicationContext)
        runBlocking {
            when (type) {
                WearActionContract.TYPE_SYNC -> {
                    // The watch opened with a missing/stale mirror and asked for a fresh push.
                    // refreshAll below re-publishes, so no other work is needed here.
                }
                WearActionContract.TYPE_FOCUS -> {
                    val dispatcher = entryPoint.focusWidgetCommandDispatcher()
                    if (WearActionContract.isFocusStart(arg)) {
                        dispatcher.start(applicationContext, WearActionContract.focusStartSeconds(arg) ?: 0)
                    } else {
                        dispatcher.dispatch(applicationContext, arg)
                    }
                }
                WearActionContract.TYPE_HABIT ->
                    entryPoint.completeHabitByIdUseCase()(arg, LocalDate.now())
                WearActionContract.TYPE_TASK ->
                    entryPoint.toggleTaskCompletionUseCase()(arg)
                WearActionContract.TYPE_DOSE ->
                    entryPoint.recordMedicationWidgetActionUseCase()(arg, true)
                WearActionContract.TYPE_BLOCK -> {
                    val block = entryPoint.timeBlockRepository().getTimeBlockById(arg)
                        ?: return@runBlocking
                    entryPoint.timeBlockCompletionHandler().complete(block)
                }
                else -> return@runBlocking
            }
            ChronosWidgetHub.refreshAll(applicationContext)
        }
    }
}
