package com.chronosflow.onboarding

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
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
import com.chronosflow.core.notifications.NotificationPermissions
import com.chronosflow.core.ui.settings.ChronosUiSettingsKeys
import com.chronosflow.core.ui.settings.rememberPersistentUiBooleanSetting
import kotlinx.coroutines.launch

/**
 * First-run introduction shown before the main shell. Explains the radial-dial metaphor, offers to
 * enable reminders, and lets the user opt into the Habits and Medication trackers before they land
 * on the planner. Completion is persisted so this is shown exactly once.
 */
@Composable
fun ChronosOnboarding(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })

    val habitsEnabled = rememberPersistentUiBooleanSetting(
        ChronosUiSettingsKeys.KEY_FEATURE_HABITS_ENABLED,
        true
    )
    val medicationEnabled = rememberPersistentUiBooleanSetting(
        ChronosUiSettingsKeys.KEY_FEATURE_MEDICATION_ENABLED,
        true
    )

    var notificationsGranted by remember {
        mutableStateOf(NotificationPermissions.hasStandardPermission(context))
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        notificationsGranted = NotificationPermissions.hasStandardPermission(context)
    }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationsGranted) {
            notificationsGranted = true
        } else {
            notificationLauncher.launch(NotificationPermissions.requiredPermissions())
        }
    }

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
                    TextButton(onClick = onComplete) { Text("Skip") }
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
                    1 -> OnboardingPage(
                        icon = Icons.Outlined.Notifications,
                        title = "Gentle nudges, never noise",
                        body = "Reminders keep block start times, breaks, medication, and your " +
                            "end-of-day review on schedule. You can fine-tune every reminder later " +
                            "in Settings.",
                        action = {
                            Button(
                                onClick = requestNotifications,
                                enabled = !notificationsGranted
                            ) {
                                Text(if (notificationsGranted) "Reminders enabled" else "Enable reminders")
                            }
                        }
                    )
                    else -> OnboardingPage(
                        icon = Icons.Filled.CheckCircle,
                        title = "Choose what to track",
                        body = "Beyond your schedule, ChronosFlow can track daily habits and " +
                            "medication. Turn on what fits — you can change this anytime.",
                        action = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OnboardingToggle(
                                    label = "Track habits",
                                    checked = habitsEnabled.value,
                                    onCheckedChange = { habitsEnabled.value = it }
                                )
                                OnboardingToggle(
                                    label = "Track medication",
                                    checked = medicationEnabled.value,
                                    onCheckedChange = { medicationEnabled.value = it }
                                )
                            }
                        }
                    )
                }
            }

            PageIndicator(
                pageCount = ONBOARDING_PAGE_COUNT,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Button(
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
private fun OnboardingToggle(
    label: String,
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
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "indicatorWidth")
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

private const val ONBOARDING_PAGE_COUNT = 3
