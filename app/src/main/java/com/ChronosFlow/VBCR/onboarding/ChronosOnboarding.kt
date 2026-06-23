package com.ChronosFlow.VBCR.onboarding

import com.ChronosFlow.VBCR.core.ui.components.ChronosButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosTextButton
import com.ChronosFlow.VBCR.core.ui.components.ChronosOutlinedButton

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ChronosFlow.VBCR.core.ui.components.ChronosSwitch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ChronosFlow.VBCR.core.data.health.HealthConnectSleepDataSource
import com.ChronosFlow.VBCR.core.ui.motion.ChronosValueAnimationFactory
import com.ChronosFlow.VBCR.core.ui.settings.rememberChronosUiSettings
import com.ChronosFlow.VBCR.core.ui.theme.ChronosSpacing
import com.ChronosFlow.VBCR.core.notifications.NotificationPermissions
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.rememberPersistentUiBooleanSetting
import kotlinx.coroutines.launch

/**
 * First-run introduction shown before the main shell. It explains the radial-dial metaphor, lets the
 * user pick exactly which trackers they want, and then asks only for the runtime permissions that
 * those choices actually need — each with a plain-language reason. Completion is persisted so this is
 * shown exactly once.
 */
@Composable
fun ChronosOnboarding(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })

    // Feature toggles — the user's choices here decide which permissions we ask for on the next page.
    val features = rememberOnboardingFeatureChoices()
    val sleepEnabled = features.first { it.key == ChronosUiSettingsKeys.KEY_FEATURE_SLEEP_ENABLED }.state
    val medicationEnabled = features.first { it.key == ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED }.state
    val habitsEnabled = features.first { it.key == ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED }.state
    val reviewEnabled = features.first { it.key == ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED }.state

    val isLastPage = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1
    val advance = {
        if (isLastPage) {
            onComplete()
        } else {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isLastPage) {
                    ChronosTextButton(onClick = onComplete) { Text("Skip") }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingPage(
                        icon = Icons.Outlined.Schedule,
                        title = "Your whole day, on one dial",
                        body = "ChronosFlow lays your day out as a 24-hour ring. Drop in blocks for " +
                            "work, breaks, and routines, then watch the dial track what actually " +
                            "happens against your plan."
                    )
                    1 -> OnboardingFeaturePage(features = features)
                    else -> OnboardingPermissionsPage(
                        notificationReasons = notificationReasons(
                            medicationEnabled = medicationEnabled.value,
                            habitsEnabled = habitsEnabled.value,
                            reviewEnabled = reviewEnabled.value
                        ),
                        sleepImportRequested = sleepEnabled.value
                    )
                }
            }

            PageIndicator(
                pageCount = ONBOARDING_PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            ChronosButton(
                onClick = { advance() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (isLastPage) "Get started" else "Next")
            }
        }
    }
}

/** A single tracker the user can opt into during onboarding, bound to its persisted feature flag. */
private class OnboardingFeatureChoice(
    val key: String,
    val label: String,
    val description: String,
    val state: MutableState<Boolean>
)

@Composable
private fun rememberOnboardingFeatureChoices(): List<OnboardingFeatureChoice> {
    return listOf(
        OnboardingFeatureChoice(
            key = ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED,
            label = "Habits",
            description = "Build streaks for daily routines like water, reading, or exercise.",
            state = rememberPersistentUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED,
                true
            )
        ),
        OnboardingFeatureChoice(
            key = ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED,
            label = "Medication",
            description = "Get dose reminders and keep an adherence history.",
            state = rememberPersistentUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED,
                true
            )
        ),
        OnboardingFeatureChoice(
            key = ChronosUiSettingsKeys.KEY_FEATURE_GOALS_ENABLED,
            label = "Goals",
            description = "Break long-term goals into milestones you can track.",
            state = rememberPersistentUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_GOALS_ENABLED,
                true
            )
        ),
        OnboardingFeatureChoice(
            key = ChronosUiSettingsKeys.KEY_FEATURE_JOURNAL_ENABLED,
            label = "Journal",
            description = "Capture quick notes and reflect on how your day went.",
            state = rememberPersistentUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_JOURNAL_ENABLED,
                true
            )
        ),
        OnboardingFeatureChoice(
            key = ChronosUiSettingsKeys.KEY_FEATURE_SLEEP_ENABLED,
            label = "Sleep",
            description = "Log sleep and see trends — optionally imported from Health Connect.",
            state = rememberPersistentUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_SLEEP_ENABLED,
                true
            )
        ),
        OnboardingFeatureChoice(
            key = ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED,
            label = "Daily review & insights",
            description = "End-of-day summaries and weekly trends from your schedule.",
            state = rememberPersistentUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_REVIEW_ENABLED,
                true
            )
        ),
        OnboardingFeatureChoice(
            key = ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED,
            label = "AI planning assistant",
            description = "On-device suggestions that help you plan and adjust your day.",
            state = rememberPersistentUiBooleanSetting(
                ChronosUiSettingsKeys.KEY_FEATURE_AI_ADVISOR_ENABLED,
                true
            )
        )
    )
}

@Composable
private fun OnboardingFeaturePage(features: List<OnboardingFeatureChoice>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        OnboardingHeader(
            icon = Icons.Outlined.Tune,
            title = "Choose what to track",
            body = "Beyond your schedule, turn on only the trackers you want. Each adds its own " +
                "section to the app — you can change any of these later in Settings."
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            features.forEach { feature ->
                OnboardingFeatureToggle(
                    label = feature.label,
                    description = feature.description,
                    checked = feature.state.value,
                    onCheckedChange = { feature.state.value = it }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OnboardingPermissionsPage(
    notificationReasons: List<String>,
    sleepImportRequested: Boolean
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    var notificationsGranted by remember {
        mutableStateOf(NotificationPermissions.hasStandardPermission(context))
    }
    var showNotificationRationale by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        notificationsGranted = NotificationPermissions.hasStandardPermission(context)
    }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationsGranted) {
            notificationsGranted = true
        } else {
            // NOTIF-001: Check shouldShowRequestPermissionRationale before launching the system
            // prompt. If Android says we should show a rationale (user denied once), surface an
            // explanation first so the user understands why the permission is needed.
            val shouldShowRationale = activity != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.POST_NOTIFICATIONS
                )
            if (shouldShowRationale) {
                showNotificationRationale = true
            } else {
                notificationLauncher.launch(NotificationPermissions.requiredPermissions())
            }
        }
    }

    if (showNotificationRationale) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text("Enable reminders?") },
            text = {
                Text(
                    "ChronosFlow uses notifications to deliver medication reminders, focus alerts, " +
                        "and daily review prompts. Without this permission those reminders stay " +
                        "silent. You can enable it later in your device Settings → Apps → " +
                        "ChronosFlow → Notifications."
                )
            },
            confirmButton = {
                ChronosTextButton(onClick = {
                    showNotificationRationale = false
                    notificationLauncher.launch(NotificationPermissions.requiredPermissions())
                }) { Text("Enable") }
            },
            dismissButton = {
                ChronosTextButton(onClick = { showNotificationRationale = false }) { Text("Not now") }
            }
        )
    }

    // Sleep import is the one permission that is strictly opt-in: only surface it when the user kept
    // the Sleep tracker on AND a Health Connect provider is actually installed on this device.
    val sleepDataSource = remember(context) { HealthConnectSleepDataSource(context.applicationContext) }
    val sleepProviderAvailable = remember(sleepDataSource) { sleepDataSource.isAvailable() }
    var sleepGranted by remember { mutableStateOf(false) }
    LaunchedEffect(sleepDataSource) {
        sleepGranted = sleepDataSource.hasSleepReadPermission()
    }
    val sleepLauncher = rememberLauncherForActivityResult(
        sleepDataSource.permissionRequestContract()
    ) { granted ->
        sleepGranted = granted.containsAll(sleepDataSource.requiredPermissions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        OnboardingHeader(
            icon = Icons.Outlined.Notifications,
            title = "Permissions you'll need",
            body = "We only ask for what your choices require, and you can grant these later in " +
                "Settings instead. Nothing here is required to start using the planner."
        )
        Spacer(Modifier.height(24.dp))

        PermissionCard(
            icon = Icons.Outlined.Notifications,
            title = "Reminders & alerts",
            why = "ChronosFlow notifies you about " + humanJoin(notificationReasons) +
                ". Without this, those reminders stay silent.",
            granted = notificationsGranted,
            grantedLabel = "Reminders enabled",
            actionLabel = "Enable reminders",
            onGrant = requestNotifications
        )

        if (sleepImportRequested && sleepProviderAvailable) {
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                icon = Icons.Outlined.Bedtime,
                title = "Import sleep from Health Connect",
                why = "Sleep data is read periodically in the background (approximately every " +
                    "6 hours) to help plan your day, even when the app is not open. " +
                    "You can still log sleep by hand if you skip this.",
                granted = sleepGranted,
                grantedLabel = "Sleep import connected",
                actionLabel = "Connect Health Connect",
                onGrant = { sleepLauncher.launch(sleepDataSource.requestPermissions) }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OnboardingHeader(
    icon: ImageVector,
    title: String,
    body: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(80.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(38.dp)
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(Modifier.height(28.dp))
            action()
        }
    }
}

@Composable
private fun OnboardingFeatureToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            ChronosSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    why: String,
    granted: Boolean,
    grantedLabel: String,
    actionLabel: String,
    onGrant: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(ChronosSpacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = grantedLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                ChronosOutlinedButton(onClick = onGrant) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                animationSpec = ChronosValueAnimationFactory.selection(reduceMotion),
                label = "indicatorWidth"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}

/** Build the dynamic list of reasons we use notifications, limited to the features the user kept on. */
private fun notificationReasons(
    medicationEnabled: Boolean,
    habitsEnabled: Boolean,
    reviewEnabled: Boolean
): List<String> = buildList {
    // Block start times and breaks come from the core planner, so they always apply.
    add("block start times and breaks")
    if (medicationEnabled) add("medication doses")
    if (habitsEnabled) add("habit nudges")
    if (reviewEnabled) add("your end-of-day review")
}

/** Joins phrases into natural English: "a", "a and b", or "a, b, and c". */
private fun humanJoin(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    2 -> "${items[0]} and ${items[1]}"
    else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
}

private const val ONBOARDING_PAGE_COUNT = 3
