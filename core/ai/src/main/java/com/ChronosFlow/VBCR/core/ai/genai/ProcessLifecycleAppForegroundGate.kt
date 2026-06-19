package com.ChronosFlow.VBCR.core.ai.genai

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProcessLifecycleAppForegroundGate @Inject constructor() : AppForegroundGate {
    override fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }
}
