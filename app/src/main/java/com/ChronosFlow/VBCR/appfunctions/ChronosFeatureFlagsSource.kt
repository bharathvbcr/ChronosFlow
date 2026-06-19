package com.ChronosFlow.VBCR.appfunctions

import com.ChronosFlow.VBCR.core.ui.settings.ChronosFeatureFlags

/**
 * Supplies the current [ChronosFeatureFlags] to agent-facing AppFunctions so a feature toggled
 * off in Developer settings is also unavailable to system AI assistants. Injected (rather than
 * read straight off a Context) so it can be faked in unit tests.
 */
fun interface ChronosFeatureFlagsSource {
    suspend fun current(): ChronosFeatureFlags
}
