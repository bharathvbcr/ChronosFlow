package com.chronosflow.core.data.focus

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the latest mood/energy scores used for focus timer accent tinting when
 * Room check-ins are not yet loaded or the user has not checked in today.
 */
@Singleton
class FocusMoodAccentCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(blockId: String?, moodScore: Int, energyScore: Int) {
        val mood = moodScore.coerceIn(1, 5)
        val energy = energyScore.coerceIn(1, 5)
        val editor = prefs.edit()
            .putInt(KEY_GENERAL_MOOD, mood)
            .putInt(KEY_GENERAL_ENERGY, energy)
        blockId?.let { id ->
            editor
                .putInt(blockMoodKey(id), mood)
                .putInt(blockEnergyKey(id), energy)
        }
        editor.apply()
    }

    fun moodEnergyForBlock(blockId: String?): Pair<Int?, Int?> {
        if (blockId != null) {
            val blockMood = prefs.getInt(blockMoodKey(blockId), -1)
            val blockEnergy = prefs.getInt(blockEnergyKey(blockId), -1)
            if (blockMood in 1..5 && blockEnergy in 1..5) {
                return blockMood to blockEnergy
            }
        }
        val generalMood = prefs.getInt(KEY_GENERAL_MOOD, -1)
        val generalEnergy = prefs.getInt(KEY_GENERAL_ENERGY, -1)
        if (generalMood in 1..5 && generalEnergy in 1..5) {
            return generalMood to generalEnergy
        }
        return null to null
    }

    private fun blockMoodKey(blockId: String) = "block_mood_$blockId"
    private fun blockEnergyKey(blockId: String) = "block_energy_$blockId"

    companion object {
        private const val PREFS_NAME = "chronos_focus_mood_accent"
        private const val KEY_GENERAL_MOOD = "general_mood"
        private const val KEY_GENERAL_ENERGY = "general_energy"
    }
}
