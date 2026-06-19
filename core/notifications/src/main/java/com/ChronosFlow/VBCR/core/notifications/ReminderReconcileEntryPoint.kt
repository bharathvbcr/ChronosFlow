package com.ChronosFlow.VBCR.core.notifications

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderReconcileEntryPoint {
    fun pendingAlarmReconciler(): PendingAlarmReconciler
}
