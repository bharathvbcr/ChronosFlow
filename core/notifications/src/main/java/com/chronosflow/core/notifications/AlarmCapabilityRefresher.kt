package com.chronosflow.core.notifications

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Emits when the app returns to the foreground so feature screens can refresh
 * notification and exact-alarm permission state after system settings changes.
 */
@Singleton
class AlarmCapabilityRefresher @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DefaultLifecycleObserver {
    private val _refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshes: SharedFlow<Unit> = _refreshes.asSharedFlow()

    private var registered = false

    fun register() {
        if (registered) return
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        registered = true
    }

    override fun onStart(owner: LifecycleOwner) {
        _refreshes.tryEmit(Unit)
        ReminderReconcileScheduler.enqueue(context)
    }
}
