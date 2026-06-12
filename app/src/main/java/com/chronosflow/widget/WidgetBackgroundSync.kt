package com.chronosflow.widget

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Refreshes every widget and the watch summary the moment the app goes to background — the
 * instant the user is about to see a home-screen widget again. Without this, edits made inside
 * the app (completing a task, rescheduling a block) sit stale on widgets and tiles for up to
 * the [WidgetRefreshWorker] 10-minute tick.
 */
object WidgetBackgroundSync {

    fun register(context: Context) {
        val appContext = context.applicationContext
        val owner = ProcessLifecycleOwner.get()
        owner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    owner.lifecycleScope.launch(Dispatchers.Default) {
                        runCatching { ChronosWidgetHub.refreshAll(appContext) }
                    }
                }
            }
        )
    }
}
