package com.ChronosFlow.VBCR.feature.daydial.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ChronosFlow.VBCR.core.data.health.HealthConnectAvailability
import com.ChronosFlow.VBCR.core.data.health.HealthConnectWorkoutDataSource
import com.ChronosFlow.VBCR.core.domain.wear.WearLinkStatus
import com.ChronosFlow.VBCR.core.notifications.NotificationPermissions
import com.ChronosFlow.VBCR.core.ui.components.ChronosSettingsRow
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosWarningBanner
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.rememberPersistentUiBooleanSetting
import com.ChronosFlow.VBCR.feature.daydial.CalendarPermissionStatus
import com.ChronosFlow.VBCR.feature.daydial.HealthConnectSleepViewModel
import com.ChronosFlow.VBCR.feature.daydial.ScreenTimeViewModel
import com.ChronosFlow.VBCR.feature.daydial.delegate.AppLockSettingsState
import com.ChronosFlow.VBCR.feature.daydial.openCalendarPermissionSettings
import com.ChronosFlow.VBCR.feature.daydial.openHealthConnectInStore

internal fun privacyAppLockSummary(settings: AppLockSettingsState): String = when {
    settings.appLockEnabled && settings.lockOnResume -> "Locked on open and resume"
    settings.appLockEnabled -> "Locked on open"
    settings.requireAuthMedication || settings.requireAuthReview || settings.requireAuthDataExport ->
        "Protected areas only"
    else -> "Off"
}

internal fun privacySensitiveContentSummary(hideSensitiveTitles: Boolean): String =
    if (hideSensitiveTitles) "Titles hidden on watch and notifications" else "Titles visible"

internal fun privacyCloudSyncSummary(syncCloud: Boolean, syncStatus: String): String =
    if (syncCloud) "Checkpoints on · $syncStatus" else "Checkpoints off"

internal fun wearLinkConnectionSummary(status: WearLinkStatus?): String = when {
    status == null -> "Checking watch…"
    status.watchAppInstalled ->
        "Connected" + (status.connectedNodeName?.let { ": $it" }.orEmpty())
    status.watchConnected -> "Watch connected — app not installed"
    status.watchPaired -> "Watch paired but not reachable"
    else -> "No watch connected"
}

/** DevTime / Meridian — the companion app ChronosFlow shares tasks and events with. */
internal const val COMPANION_APP_PACKAGE = "com.Meridian.VBCR"

/**
 * Whether DevTime is installed (and visible to us via the manifest `<queries>` entry). This is the
 * UI-facing mirror of InteropClient.isPeerInstalled(); it's duplicated here because :feature:daydial
 * can't depend on the :app module where InteropClient lives.
 */
internal fun isCompanionAppInstalled(context: Context): Boolean = try {
    context.packageManager.getPackageInfo(COMPANION_APP_PACKAGE, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

internal fun companionAppSummary(installed: Boolean): String =
    if (installed) "DevTime connected" else "DevTime not installed"

/**
 * Shows whether DevTime is installed so the user knows if cross-app task/event sharing is active.
 * Re-checks on every resume (the user may install or remove DevTime while ChronosFlow is
 * backgrounded) and reports the result up via [onStatusChanged] so the section summary can update.
 */
@Composable
internal fun CompanionAppStatusCard(onStatusChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var installed by remember { mutableStateOf(isCompanionAppInstalled(context)) }
    val privacyPreferences = rememberPrivacyPreferences()
    var medicationSharingEnabled by remember {
        mutableStateOf(privacyPreferences.isMedicationSharingEnabled())
    }

    LaunchedEffect(Unit) { onStatusChanged(installed) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = isCompanionAppInstalled(context)
                installed = now
                onStatusChanged(now)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (installed) {
                "DevTime is installed. Tasks and events are shared securely between the two apps."
            } else {
                "DevTime isn't installed. ChronosFlow runs on its own — install DevTime to share tasks " +
                    "and events between the apps."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChronosSettingsRow(
            title = "Share medication names with companion app",
            subtitle = if (medicationSharingEnabled) {
                "Medication names are visible to DevTime"
            } else {
                "Medication data is not shared (default)"
            },
            checked = medicationSharingEnabled,
            onCheckedChange = { enabled ->
                medicationSharingEnabled = enabled
                privacyPreferences.setMedicationSharingEnabled(enabled)
            }
        )
    }
}

internal fun readingConnectionSummary(autoFetchEnabled: Boolean): String =
    if (autoFetchEnabled) "Browser link sharing · details auto-fetched" else "Browser link sharing · offline"

/**
 * Surfaces ChronosFlow's "incoming" connected-app capability: any app can share a link/text to it
 * via the "Save to ChronosFlow" share target, and (optionally) ChronosFlow reaches out over the
 * network to enrich saved links. Lives next to [CompanionAppStatusCard] under "Connected apps"
 * since both describe how ChronosFlow exchanges data with other apps.
 */
@Composable
internal fun ReadingConnectionCard() {
    val privacyPreferences = rememberPrivacyPreferences()
    var autoFetch by rememberPersistentUiBooleanSetting(
        ChronosUiSettingsKeys.KEY_READING_METADATA_AUTOFETCH,
        true
    )
    var acceptHandoffs by remember { mutableStateOf(privacyPreferences.isInboundHandoffAccepted()) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "\"Save to ChronosFlow\" appears in any app's share sheet. Share a link to add it to " +
                "your reading list, or share text to drop it in your inbox.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChronosSettingsRow(
            title = "Auto-fetch link details",
            subtitle = if (autoFetch) {
                "Fetches each saved link's title, icon, and read time (one network request per link)."
            } else {
                "Reading list stays fully offline — no requests to saved links."
            },
            checked = autoFetch,
            onCheckedChange = { autoFetch = it }
        )
        ChronosSettingsRow(
            title = "Accept items from Curio",
            subtitle = if (acceptHandoffs) {
                "Curio can save links to your reading list (with reminders), inbox, and tasks."
            } else {
                "Curio handoffs are turned off — nothing can be added from Curio."
            },
            checked = acceptHandoffs,
            onCheckedChange = { enabled ->
                acceptHandoffs = enabled
                privacyPreferences.setInboundHandoffAccepted(enabled)
            }
        )
    }
}

internal data class PrivacyPermissionStates(
    val notificationsGranted: Boolean,
    val promotedGranted: Boolean,
    val includePromotedPermission: Boolean,
    val exactAlarmsGranted: Boolean,
    val includeExactAlarmsPermission: Boolean,
    val calendarReadGranted: Boolean,
    val calendarWriteGranted: Boolean,
    val healthConnectGranted: Boolean,
    val includeHealthConnectPermission: Boolean,
    val workoutsGranted: Boolean,
    val includeWorkoutsPermission: Boolean,
    val usageAccessGranted: Boolean,
    val contactsGranted: Boolean
)

internal fun privacyPermissionsSummary(states: PrivacyPermissionStates): String {
    val flags = buildList {
        add(states.notificationsGranted)
        if (states.includePromotedPermission) add(states.promotedGranted)
        if (states.includeExactAlarmsPermission) add(states.exactAlarmsGranted)
        add(states.calendarReadGranted)
        add(states.calendarWriteGranted)
        if (states.includeHealthConnectPermission) add(states.healthConnectGranted)
        if (states.includeWorkoutsPermission) add(states.workoutsGranted)
        add(states.usageAccessGranted)
        add(states.contactsGranted)
    }
    val granted = flags.count { it }
    return when {
        granted == flags.size -> "All access granted"
        granted == 0 -> "No access granted"
        else -> "$granted of ${flags.size} granted"
    }
}

@Composable
internal fun rememberPrivacyPermissionStates(
    calendarPermissionStatus: CalendarPermissionStatus
): PrivacyPermissionStates {
    val context = LocalContext.current
    val healthConnectViewModel = hiltViewModel<HealthConnectSleepViewModel>()
    val screenTimeViewModel = hiltViewModel<ScreenTimeViewModel>()
    val healthStatus by healthConnectViewModel.status.collectAsStateWithLifecycle()
    val screenTimeStatus by screenTimeViewModel.status.collectAsStateWithLifecycle()
    val workoutDataSource = remember { HealthConnectWorkoutDataSource(context.applicationContext) }
    var healthConnectGranted by remember { mutableStateOf(false) }
    var workoutsGranted by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun refreshHealthConnectGrant() {
        healthConnectGranted = healthStatus.isAvailable && healthConnectViewModel.hasSleepReadPermission()
        workoutsGranted = healthStatus.isAvailable && workoutDataSource.hasWorkoutReadPermission()
    }

    LaunchedEffect(healthStatus) {
        refreshHealthConnectGrant()
    }

    DisposableEffect(lifecycleOwner, healthConnectViewModel, screenTimeViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                healthConnectViewModel.refresh()
                screenTimeViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val includePromotedPermission = Build.VERSION.SDK_INT >= 37
    val includeExactAlarmsPermission = Build.VERSION.SDK_INT >= 31
    val includeHealthConnectPermission = healthStatus.availability == HealthConnectAvailability.AVAILABLE

    return PrivacyPermissionStates(
        notificationsGranted = NotificationPermissions.hasStandardPermission(context),
        promotedGranted = NotificationPermissions.hasPromotedPermission(context),
        includePromotedPermission = includePromotedPermission,
        exactAlarmsGranted = canScheduleExactAlarmsCompat(context),
        includeExactAlarmsPermission = includeExactAlarmsPermission,
        calendarReadGranted = calendarPermissionStatus.readGranted,
        calendarWriteGranted = calendarPermissionStatus.writeGranted,
        healthConnectGranted = healthConnectGranted,
        includeHealthConnectPermission = includeHealthConnectPermission,
        workoutsGranted = workoutsGranted,
        includeWorkoutsPermission = includeHealthConnectPermission,
        usageAccessGranted = screenTimeStatus.hasAccess,
        contactsGranted = hasContactsPermission(context)
    )
}

@Composable
internal fun PrivacyPermissionsSection(
    calendarPermissionStatus: CalendarPermissionStatus,
    showCalendarPermissionRationale: Boolean,
    onDismissCalendarPermissionRationale: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestCalendarSync: () -> Unit,
    onRequestCalendarExportAccess: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mutedText = MaterialTheme.colorScheme.onSurfaceVariant
    val healthConnectViewModel = hiltViewModel<HealthConnectSleepViewModel>()
    val screenTimeViewModel = hiltViewModel<ScreenTimeViewModel>()
    val healthStatus by healthConnectViewModel.status.collectAsStateWithLifecycle()
    val screenTimeStatus by screenTimeViewModel.status.collectAsStateWithLifecycle()
    val workoutDataSource = remember { HealthConnectWorkoutDataSource(context.applicationContext) }
    var healthConnectGranted by remember { mutableStateOf(false) }
    var workoutsGranted by remember { mutableStateOf(false) }

    LaunchedEffect(healthStatus) {
        healthConnectGranted = healthStatus.isAvailable && healthConnectViewModel.hasSleepReadPermission()
        workoutsGranted = healthStatus.isAvailable && workoutDataSource.hasWorkoutReadPermission()
    }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        healthConnectViewModel.permissionContract()
    ) {
        healthConnectViewModel.onPermissionGrantResult()
    }
    val workoutPermissionLauncher = rememberLauncherForActivityResult(
        workoutDataSource.permissionRequestContract()
    ) { granted ->
        workoutsGranted = granted.containsAll(workoutDataSource.requiredPermissions)
    }
    val usageAccessLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        screenTimeViewModel.refresh()
    }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Recomposition picks up the new grant state.
    }

    val notificationsGranted = NotificationPermissions.hasStandardPermission(context)
    val promotedGranted = NotificationPermissions.hasPromotedPermission(context)
    val showPromotedPermission = Build.VERSION.SDK_INT >= 37
    val exactAlarmsGranted = canScheduleExactAlarmsCompat(context)
    val usageAccessGranted = screenTimeStatus.hasAccess
    val contactsGranted = hasContactsPermission(context)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Control which system access ChronosFlow can use. Toggles reflect Android permissions " +
                "and open the system prompt or settings when you change them.",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText
        )
        PermissionToggleRow(
            title = "Notifications",
            subtitle = if (notificationsGranted) "Reminders and focus alerts allowed" else "Tap to allow",
            checked = notificationsGranted,
            onCheckedChange = { enabled ->
                if (enabled) onRequestNotificationPermission() else openAppNotificationSettings(context)
            }
        )
        if (showPromotedPermission) {
            val promotionIntent = NotificationPermissions.promotionSettingsIntent(context)
            PermissionToggleRow(
                title = "Live updates",
                subtitle = if (promotedGranted) {
                    "Status bar and lock screen updates allowed"
                } else {
                    "Tap to allow promoted notifications"
                },
                checked = promotedGranted,
                onCheckedChange = {
                    promotionIntent?.let { context.startActivity(it) }
                        ?: if (!promotedGranted) onRequestNotificationPermission() else openAppNotificationSettings(context)
                }
            )
        }
        if (Build.VERSION.SDK_INT >= 31) {
            PermissionToggleRow(
                title = "Exact alarms",
                subtitle = if (exactAlarmsGranted) "On-time block and medication reminders" else "Tap to allow",
                checked = exactAlarmsGranted,
                onCheckedChange = { onOpenExactAlarmSettings() }
            )
        }
        PermissionToggleRow(
            title = "Calendar read",
            subtitle = if (calendarPermissionStatus.readGranted) {
                "Device events can be imported"
            } else {
                "Tap to allow calendar imports"
            },
            checked = calendarPermissionStatus.readGranted,
            onCheckedChange = { enabled ->
                when {
                    enabled && !calendarPermissionStatus.readGranted -> onRequestCalendarSync()
                    !enabled && calendarPermissionStatus.readGranted -> openCalendarPermissionSettings(context)
                }
            }
        )
        PermissionToggleRow(
            title = "Calendar write",
            subtitle = if (calendarPermissionStatus.writeGranted) {
                "Exported blocks stay linked"
            } else {
                "Tap to allow calendar exports"
            },
            checked = calendarPermissionStatus.writeGranted,
            onCheckedChange = { enabled ->
                when {
                    enabled && !calendarPermissionStatus.writeGranted -> onRequestCalendarExportAccess()
                    !enabled && calendarPermissionStatus.writeGranted -> openCalendarPermissionSettings(context)
                }
            }
        )
        when (healthStatus.availability) {
            HealthConnectAvailability.AVAILABLE -> {
                PermissionToggleRow(
                    title = "Health Connect sleep",
                    subtitle = if (healthConnectGranted) {
                        "Sleep sessions can be imported"
                    } else {
                        "Tap to allow sleep read access"
                    },
                    checked = healthConnectGranted,
                    onCheckedChange = { enabled ->
                        when {
                            enabled && !healthConnectGranted ->
                                healthConnectPermissionLauncher.launch(healthConnectViewModel.requestPermissions)
                            !enabled && healthConnectGranted ->
                                runCatching {
                                    context.startActivity(healthConnectViewModel.managePermissionsIntent())
                                }.onFailure { openCalendarPermissionSettings(context) }
                        }
                    }
                )
                PermissionToggleRow(
                    title = "Health Connect workouts",
                    subtitle = if (workoutsGranted) {
                        "Workouts can be imported into your journal"
                    } else {
                        "Tap to allow workout read access"
                    },
                    checked = workoutsGranted,
                    onCheckedChange = { enabled ->
                        when {
                            enabled && !workoutsGranted ->
                                workoutPermissionLauncher.launch(workoutDataSource.requestPermissions)
                            !enabled && workoutsGranted ->
                                runCatching {
                                    context.startActivity(workoutDataSource.managePermissionsIntent())
                                }.onFailure { openCalendarPermissionSettings(context) }
                        }
                    }
                )
            }
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED -> {
                PermissionToggleRow(
                    title = "Health Connect sleep",
                    subtitle = "Update Health Connect to import sleep",
                    checked = false,
                    onCheckedChange = { context.openHealthConnectInStore() }
                )
            }
            HealthConnectAvailability.NOT_SUPPORTED -> {
                PermissionToggleRow(
                    title = "Health Connect sleep",
                    subtitle = "Install Health Connect to import sleep",
                    checked = false,
                    onCheckedChange = { context.openHealthConnectInStore() }
                )
            }
        }
        PermissionToggleRow(
            title = "Usage access",
            subtitle = if (usageAccessGranted) {
                "Screen time can be read for Insights"
            } else {
                "Tap to grant usage access in system settings"
            },
            checked = usageAccessGranted,
            onCheckedChange = {
                usageAccessLauncher.launch(screenTimeViewModel.usageAccessIntent())
            }
        )
        PermissionToggleRow(
            title = "Contacts",
            subtitle = if (contactsGranted) {
                "Task actions can link to contacts"
            } else {
                "Tap to allow contact picker access"
            },
            checked = contactsGranted,
            onCheckedChange = { enabled ->
                when {
                    enabled && !contactsGranted ->
                        contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    !enabled && contactsGranted -> openCalendarPermissionSettings(context)
                }
            }
        )
        if (showCalendarPermissionRationale && !calendarPermissionStatus.allGranted) {
            ChronosWarningBanner(
                title = "Calendar access is needed",
                message = "ChronosFlow reads your device calendar to avoid duplicate plans and writes " +
                    "linked exports so block edits stay in sync."
            )
            ChronosTextButton(onClick = onDismissCalendarPermissionRationale) {
                Text("Not now")
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ChronosSettingsRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

internal fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

internal fun canScheduleExactAlarmsCompat(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return true
    return try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        alarmManager?.canScheduleExactAlarms() ?: true
    } catch (_: SecurityException) {
        false
    }
}
