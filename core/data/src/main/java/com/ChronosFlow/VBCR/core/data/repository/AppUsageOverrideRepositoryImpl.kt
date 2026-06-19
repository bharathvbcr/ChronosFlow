package com.ChronosFlow.VBCR.core.data.repository

import com.ChronosFlow.VBCR.core.data.dao.AppUsageOverrideDao
import com.ChronosFlow.VBCR.core.data.model.AppUsageOverrideEntity
import com.ChronosFlow.VBCR.core.domain.model.UsageCategory
import com.ChronosFlow.VBCR.core.domain.repository.AppUsageOverrideRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AppUsageOverrideRepositoryImpl @Inject constructor(
    private val dao: AppUsageOverrideDao
) : AppUsageOverrideRepository {
    override fun observeOverrides(): Flow<Map<String, UsageCategory>> =
        dao.observeAll().map { rows -> rows.toCategoryMap() }

    override suspend fun getOverrides(): Map<String, UsageCategory> = dao.getAll().toCategoryMap()

    override suspend fun setOverride(packageName: String, category: UsageCategory) =
        dao.upsert(AppUsageOverrideEntity(packageName = packageName, category = category.name))

    override suspend fun clearOverride(packageName: String) = dao.deleteByPackage(packageName)

    private fun List<AppUsageOverrideEntity>.toCategoryMap(): Map<String, UsageCategory> =
        associate { row ->
            row.packageName to runCatching { UsageCategory.valueOf(row.category) }
                .getOrDefault(UsageCategory.NEUTRAL)
        }
}
