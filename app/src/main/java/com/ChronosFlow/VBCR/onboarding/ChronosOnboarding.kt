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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ChronosFlow.VBCR.core.ui.components.ChronosSwitch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ChronosFlow.VBCR.core.data.health.HealthConnectSleepDataSource
import com.ChronosFlow.VBCR.core.ui.motion.ChronosMotionDefaults
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionDirection
import com.ChronosFlow.VBCR.core.ui.motion.ChronosTransitionFactory
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
    val reduceMotion = rememberChronosUiSettings().reduceMotionEnabled
    val pageEnter = remember(reduceMotion) {
        ChronosTransitionFactory.fadeScale(
            durationMillis = if (reduceMotion) {
                ChronosMotionDefaults.ReducedDurationMillis
            } else {
                ChronosMotionDefaults.DefaultDurationMillis
            },
            easing = ChronosMotionDefaults.MaterialStandardEasing,
            direction = ChronosTransitionDirection.Neutral,
            enterScale = if (reduceMotion) 1f else ChronosMotionDefaults.SharedAxisEnterScale,
            exitScale = 1f
        )
    }

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
                .padding(horizontal = ChronosSpacing.Medium, vertical = ChronosSpacing.Standard)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!isLastPage) {
                    ChronosTextButton(onClick = onComplete) { Text("Skip") }
                } else {
                    Spacer(modifier = Modifier.height(ChronosSpacing.Hero))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                // Per-page enter: fade/scale via ChronosTransitionFactory; collapses to fade-only
                // under reduced motion. Pager still owns swipe between pages.
                var visible by remember(page) { mutableStateOf(false) }
                LaunchedEffect(page) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = pageEnter.enter,
                    exit = pageEnter.exit
                ) {
                    when (page) {
                        0 -> OnboardingPage(
                            title = "Your whole day, on one dial",
                            body = "ChronosFlow lays your day out as a 24-hour ring. Drop in blocks for " +
                                "work, breaks, and routines, then watch the dial track what actually " +
                                "happens against your plan.",
                            visual = {
                                OnboardingMiniDial(reduceMotionEnabled = reduceMotion)
                            }
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
            }

            PageIndicator(
                pageCount = ONBOARDING_PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(vertical = ChronosSpacing.Standard)
            )

            ChronosButton(
                onClick = { advance() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChronosSpacing.Hero + ChronosSpacing.Micro)
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
        Spacer(modifier = Modifier.height(ChronosSpacing.Small))
        OnboardingHeader(
            icon = Icons.Outlined.Tune,
            title = "Choose what to track",
            body = "Beyond your schedule, turn on only the trackers you want. Each adds its own " +
                "section to the app — you can change any of these later in Settings."
        )
        Spacer(modifier = Modifier.height(ChronosSpacing.Medium))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ChronosSpacing.Small)
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
        Spacer(modifier = Modifier.height(ChronosSpacing.Small))
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
        Spacer(modifier = Modifier.height(ChronosSpacing.Small))
        OnboardingHeader(
            icon = Icons.Outlined.Notifications,
            title = "Permissions you'll need",
            body = "We only ask for what your choices require, and you can grant these later in " +
                "Settings instead. Nothing here is required to start using the planner."
        )
        Spacer(modifier = Modifier.height(ChronosSpacing.Medium))

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
            Spacer(modifier = Modifier.height(ChronosSpacing.Compact))
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
        Spacer(modifier = Modifier.height(ChronosSpacing.Small))
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
        modifier = Modifier.size(ChronosSpacing.Hero + ChronosSpacing.Large)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(ChronosSpacing.Large + ChronosSpacing.Micro)
            )
        }
    }
    Spacer(modifier = Modifier.height(ChronosSpacing.Standard + ChronosSpacing.Micro))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(ChronosSpacing.Compact))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OnboardingPage(
    title: String,
    body: String,
    visual: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        visual()
        Spacer(modifier = Modifier.height(ChronosSpacing.Large))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(ChronosSpacing.Compact))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(ChronosSpacing.Medium + ChronosSpacing.Micro))
            action()
        }
    }
}

/**
 * Lightweight decorative mini-dial for onboarding page 1 — static sample arcs (not live
 * schedule data) with a quiet glow pulse that snaps static under reduced motion.
 */
@Composable
private fun OnboardingMiniDial(
    reduceMotionEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val glow = remember { Animatable(if (reduceMotionEnabled) 0.45f else 0.28f) }
    LaunchedEffect(reduceMotionEnabled) {
        if (reduceMotionEnabled) {
            glow.snapTo(0.45f)
            return@LaunchedEffect
        }
        while (true) {
            glow.animateTo(
                0.55f,
                ChronosValueAnimationFactory.stateChange(reducedMotion = false)
            )
            glow.animateTo(
                0.28f,
                ChronosValueAnimationFactory.stateChange(reducedMotion = false)
            )
        }
    }
    val sampleArcs = remember(primary, secondary, tertiary, primaryContainer) {
        listOf(
            OnboardingSampleArc(startAngle = -75f, sweepAngle = 70f, color = primary),
            OnboardingSampleArc(startAngle = 20f, sweepAngle = 28f, color = tertiary),
            OnboardingSampleArc(startAngle = 70f, sweepAngle = 55f, color = secondary),
            OnboardingSampleArc(startAngle = 200f, sweepAngle = 48f, color = primaryContainer)
        )
    }
    val dialSize = ChronosSpacing.Hero * 2 + ChronosSpacing.Medium
    val stroke = ChronosSpacing.Small
    Canvas(
        modifier = modifier
            .size(dialSize)
            .semantics { contentDescription = "Sample day dial" }
    ) {
        val strokeWidth = stroke.toPx()
        drawArc(
            color = trackColor.copy(alpha = 0.22f + glow.value * 0.12f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        sampleArcs.forEach { arc ->
            drawArc(
                color = arc.color.copy(alpha = 0.72f + glow.value * 0.2f),
                startAngle = arc.startAngle,
                sweepAngle = arc.sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

private data class OnboardingSampleArc(
    val startAngle: Float,
    val sweepAngle: Float,
    val color: Color
)

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
                .padding(horizontal = ChronosSpacing.Standard + ChronosSpacing.Micro, vertical = ChronosSpacing.Compact),
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
            Spacer(modifier = Modifier.width(ChronosSpacing.Compact))
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
                    modifier = Modifier.size(ChronosSpacing.Medium)
                )
                Spacer(modifier = Modifier.width(ChronosSpacing.Compact))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(ChronosSpacing.Small))
            Text(
                text = why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(ChronosSpacing.Standard))
            if (granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(ChronosSpacing.Standard + ChronosSpacing.Micro)
                    )
                    Spacer(modifier = Modifier.width(ChronosSpacing.Small))
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
                targetValue = if (selected) ChronosSpacing.Medium else ChronosSpacing.Small,
                animationSpec = ChronosValueAnimationFactory.selection(reduceMotion),
                label = "indicatorWidth"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = ChronosSpacing.Micro)
                    .height(ChronosSpacing.Small)
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
