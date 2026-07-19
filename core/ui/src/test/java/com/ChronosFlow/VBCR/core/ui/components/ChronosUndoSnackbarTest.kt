package com.ChronosFlow.VBCR.core.ui.components

import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChronosUndoSnackbarTest {

    @Test
    fun `ActionPerformed invokes onUndo`() = runTest(UnconfinedTestDispatcher()) {
        val host = SnackbarHostState()
        var undone = false
        val job = launch {
            host.showChronosUndoSnackbar(
                message = "Item deleted",
                onUndo = { undone = true },
            )
        }
        runCurrent()

        val snackbar = host.currentSnackbarData
        assertNotNull(snackbar)
        assertEquals("Undo", snackbar!!.visuals.actionLabel)
        snackbar.performAction()
        runCurrent()

        assertTrue(undone)
        job.join()
    }

    @Test
    fun `dismiss without action does not invoke onUndo`() = runTest(UnconfinedTestDispatcher()) {
        val host = SnackbarHostState()
        var undone = false
        val job = launch {
            host.showChronosUndoSnackbar(
                message = "Item deleted",
                onUndo = { undone = true },
            )
        }
        runCurrent()

        val snackbar = host.currentSnackbarData
        assertNotNull(snackbar)
        snackbar!!.dismiss()
        runCurrent()

        assertFalse(undone)
        job.join()
    }
}
