package com.ChronosFlow.VBCR.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Refreshes every widget and the watch summary on both app foreground and background.
 *
 * Background ([Lifecycle.Event.ON_STOP]) keeps widgets and tiles current the instant the user is
 * about to see a home-screen widget again — without it, edits made inside the app (completing a
 * task, rescheduling a block) sit stale for up to the [WidgetRefreshWorker] 10-minute tick.
 *
 * Foreground ([Lifecycle.Event.ON_START]) pushes a fresh day summary to the watch the moment the
 * phone app is opened. The widget-refresh worker only runs while a home-screen widget exists, so a
 * widget-free user who simply opens both apps would otherwise leave the watch showing "Not synced
 * yet" indefinitely.
 */
object WidgetBackgroundSync {

    fun register(context: Context) {
        val appContext = context.applicationContext
        // Lifecycle.addObserver is main-thread-only; hop to the main looper if called off-main.
        val mainLooper = Looper.getMainLooper()
        if (Looper.myLooper() == mainLooper) {
            registerOnMain(appContext)
        } else {
            Handler(mainLooper).post { registerOnMain(appContext) }
        }
    }

    private fun registerOnMain(appContext: Context) {
        val owner = ProcessLifecycleOwner.get()
        owner.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP) {
                    owner.lifecycleScope.launch(Dispatchers.Default) {
                        runCatching { ChronosWidgetHub.refreshAll(appContext) }
                    }
                }
            }
        )
    }
}
