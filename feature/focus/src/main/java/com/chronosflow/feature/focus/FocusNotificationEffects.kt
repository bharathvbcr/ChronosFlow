package com.chronosflow.feature.focus

import android.content.Context
import android.content.Intent
import android.os.Build

fun Context.sendFocusServiceCommand(
    action: String,
    timeLeft: Int,
    totalSeconds: Int,
    sessionId: String?,
    blockId: String? = null,
    archiveOnStop: Boolean = false,
    logActualOnStop: Boolean = true,
    wasSkip: Boolean = false,
    adjustSeconds: Int? = null
) {
    val intent = Intent(this, FocusService::class.java).apply {
        this.action = action
        putExtra(FocusService.EXTRA_TIME_LEFT_SECONDS, timeLeft)
        putExtra(FocusService.EXTRA_TOTAL_SECONDS, totalSeconds)
        putExtra(FocusService.EXTRA_SESSION_ID, sessionId)
        blockId?.let { putExtra(FocusService.EXTRA_BLOCK_ID, it) }
        putExtra(FocusService.EXTRA_ARCHIVE_ON_STOP, archiveOnStop)
        putExtra(FocusService.EXTRA_LOG_ACTUAL_ON_STOP, logActualOnStop)
        putExtra(FocusService.EXTRA_WAS_SKIP, wasSkip)
        adjustSeconds?.let { putExtra(FocusService.EXTRA_ADJUST_SECONDS, it) }
    }
    if (action == FocusService.ACTION_STOP) {
        startService(intent)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}
