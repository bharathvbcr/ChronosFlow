package com.ChronosFlow.VBCR.core.ai.genai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permissive default used in unit tests and library modules.
 * The app module binds [ProcessLifecycleAppForegroundGate] instead.
 */
@Singleton
class DefaultAppForegroundGate @Inject constructor() : AppForegroundGate {
    override fun isAppInForeground(): Boolean = true
}
