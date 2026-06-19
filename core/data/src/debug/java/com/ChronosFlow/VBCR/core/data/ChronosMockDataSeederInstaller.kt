package com.ChronosFlow.VBCR.core.data

import androidx.room.RoomDatabase

internal object ChronosMockDataSeederInstaller {
    fun installIfEnabled(builder: RoomDatabase.Builder<ChronosDatabase>) {
        if (seedMockDataEnabled()) {
            builder.addCallback(ChronosMockDataSeeder())
        }
    }

    private fun seedMockDataEnabled(): Boolean =
        runCatching {
            Class.forName("com.ChronosFlow.VBCR.core.data.BuildConfig")
                .getField("SEED_MOCK_DATA")
                .getBoolean(null)
        }.getOrDefault(false)
}
