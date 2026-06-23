package com.ChronosFlow.VBCR.core.data.focus

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot signal that the user tapped a split-session phase-boundary notification
 * ("Time for a break — tap to continue"). The notification fires from a background
 * component, but advancing the phase is in-app logic owned by the Day Dial layer, so
 * the tap is bridged here.
 *
 * Backed by a CONFLATED [Channel] rather than a SharedFlow so the request survives the
 * race where the tap launches the activity before the Day Dial ViewModel starts
 * collecting (the element is buffered and delivered to the first collector), while a
 * later re-subscription (e.g. a configuration change) does NOT replay it into a
 * duplicate phase advance.
 */
@Singleton
class FocusPhaseAdvanceBus @Inject constructor() {
    private val channel = Channel<Unit>(Channel.CONFLATED)

    /** Emits once per tap; consumed exactly once by the collecting ViewModel. */
    val requests: Flow<Unit> = channel.receiveAsFlow()

    /** Records that the user asked to continue to the next phase. */
    fun requestAdvance() {
        channel.trySend(Unit)
    }
}
