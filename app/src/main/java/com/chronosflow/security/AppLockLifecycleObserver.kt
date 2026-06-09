package com.chronosflow.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.chronosflow.core.data.security.AppLockSessionController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockLifecycleObserver @Inject constructor(
    private val appLockSessionController: AppLockSessionController
) : DefaultLifecycleObserver {

    private var startedCount = 0

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        val fromBackground = startedCount > 0
        startedCount++
        appLockSessionController.onAppForegrounded(fromBackground = fromBackground)
    }

    override fun onStop(owner: LifecycleOwner) {
        appLockSessionController.onAppBackgrounded()
    }
}
