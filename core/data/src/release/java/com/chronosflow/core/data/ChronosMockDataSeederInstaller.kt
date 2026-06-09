package com.chronosflow.core.data

import androidx.room.RoomDatabase

internal object ChronosMockDataSeederInstaller {
    @Suppress("UNUSED_PARAMETER")
    fun installIfEnabled(builder: RoomDatabase.Builder<ChronosDatabase>) = Unit
}
