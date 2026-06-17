package com.chronosflow.core.ui.components

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class LauncherAppOption(
    val label: String,
    val packageName: String,
    val className: String,
    val launchValue: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChronosLauncherAppPicker(
    selectedLaunchValue: String?,
    onAppSelected: (LauncherAppOption) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Installed apps"
) {
    val context = LocalContext.current
    val apps = remember(context) { queryLauncherApps(context.packageManager) }
    var query by rememberSaveable { mutableStateOf("") }
    val filteredApps = remember(apps, query) {
        val normalizedQuery = query.trim()
        apps
            .filter { option ->
                normalizedQuery.isBlank() ||
                    option.label.contains(normalizedQuery, ignoreCase = true) ||
                    option.packageName.contains(normalizedQuery, ignoreCase = true) ||
                    option.className.contains(normalizedQuery, ignoreCase = true)
            }
            .take(12)
    }
    val normalizedSelection = selectedLaunchValue?.trim()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search apps") },
            placeholder = { Text("Journal") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (apps.isEmpty()) {
            Text(
                text = "No launchable apps are visible on this device. Enter a package name or deep link manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        if (filteredApps.isEmpty()) {
            Text(
                text = "No installed apps match that search.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filteredApps.forEach { option ->
                ChronosFilterChip(
                    selected = option.matchesLaunchValue(normalizedSelection),
                    onClick = { onAppSelected(option) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Apps,
                            contentDescription = null
                        )
                    },
                    label = { Text(option.label) }
                )
            }
        }
    }
}

fun queryLauncherApps(packageManager: PackageManager): List<LauncherAppOption> {
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(launcherIntent, 0)
    }
    return activities
        .mapNotNull { info ->
            val activityInfo = info.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName
                ?.takeIf(String::isNotBlank)
                ?: info.resolvePackageName?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val className = activityInfo.name
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let { name -> if (name.startsWith(".")) "$packageName$name" else name }
                ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: packageName
            LauncherAppOption(
                label = label,
                packageName = packageName,
                className = className,
                launchValue = "component:$packageName/$className"
            )
        }
        .distinctBy { option -> "${option.packageName}/${option.className}".lowercase() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { option -> option.label })
}

private fun LauncherAppOption.matchesLaunchValue(value: String?): Boolean {
    val selected = value?.takeIf(String::isNotBlank) ?: return false
    return selected.equals(launchValue, ignoreCase = true) ||
        selected.equals(packageName, ignoreCase = true) ||
        selected.equals("package:$packageName", ignoreCase = true)
}
