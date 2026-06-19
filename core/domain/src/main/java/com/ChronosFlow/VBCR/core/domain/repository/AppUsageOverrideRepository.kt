package com.ChronosFlow.VBCR.core.domain.repository

import com.ChronosFlow.VBCR.core.domain.model.UsageCategory
import kotlinx.coroutines.flow.Flow

/**
 * User reclassifications of apps for the focused-vs-distracted split, keyed by package name.
 * An entry overrides the system-derived [UsageCategory] (e.g. tagging a "social" app as productive);
 * absence falls back to the default classification.
 */
interface AppUsageOverrideRepository {
    fun observeOverrides(): Flow<Map<String, UsageCategory>>
    suspend fun getOverrides(): Map<String, UsageCategory>
    suspend fun setOverride(packageName: String, category: UsageCategory)
    suspend fun clearOverride(packageName: String)
}
