package com.ChronosFlow.VBCR.core.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.ChronosFlow.VBCR.core.domain.model.AppUsageSample
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads per-app foreground screen time from Android's [UsageStatsManager] — the same data source
 * Digital Wellbeing is built on. Requires the special "Usage access" grant
 * ([android.Manifest.permission.PACKAGE_USAGE_STATS]); there is no public Digital Wellbeing API.
 */
@Singleton
class UsageStatsDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val classifier: AppUsageClassifier
) {
    private val usageStatsManager: UsageStatsManager?
        get() = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** Intent that drops the user on the system "Usage access" screen to grant the permission. */
    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Whether this app currently holds the Usage Access grant. There is no runtime-permission
     * dialog for it, so we check the app-op the same way the platform does.
     */
    @Suppress("DEPRECATION") // checkOpNoThrow is the only option < API 29; unsafeCheckOpNoThrow is 29+.
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Per-app foreground time for [date], classified and sorted by minutes desc. Empty when access
     * is missing or the system has no data for that day. Excludes our own package.
     */
    fun usageForDay(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): List<AppUsageSample> {
        val manager = usageStatsManager ?: return emptyList()
        if (!hasUsageAccess()) return emptyList()

        val startMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        val aggregated = runCatching {
            manager.queryAndAggregateUsageStats(startMillis, endMillis)
        }.getOrNull() ?: return emptyList()

        return aggregated.values
            .asSequence()
            .filter { it.packageName != context.packageName }
            .mapNotNull { stat ->
                val minutes = TimeUnit.MILLISECONDS.toMinutes(stat.totalTimeInForeground).toInt()
                if (minutes <= 0) return@mapNotNull null
                AppUsageSample(
                    packageName = stat.packageName,
                    label = classifier.labelFor(stat.packageName),
                    category = classifier.categoryFor(stat.packageName),
                    minutes = minutes
                )
            }
            .sortedByDescending { it.minutes }
            .toList()
    }
}
