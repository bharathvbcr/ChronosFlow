# ChronosFlow Production Roadmap

This roadmap is the target architecture and delivery plan. For current shipped behavior and current alpha contract, use `README.md`, `README_MINIMAL.md`, and `docs/product-contract.md`.

Target: Android 16/17 high-end day-planning app centered around the Chronos Dial, a 24-hour radial planner.

This roadmap assumes ChronosFlow already has the intended module shell:

```text
:app
:core:ai
:core:data
:core:domain
:core:notifications
:core:ui
:feature:daydial
:feature:focus
:feature:tasks
```

The current priority is not adding more isolated features. The priority is making the Chronos Dial the canonical operating surface for the day: every task, habit, medication reminder, calendar event, and focus session becomes either an intent, a scheduled arc, or an actual-time trace.

## 1. Architectural Stabilization and Domain Cleanup

### Domain Ownership

`DayPlanEntity` should not own nested task, habit, or medication blobs. It should be the daily aggregate header. `TimeBlockEntity` should be the schedule projection. Tasks, habits, medications, and calendar events should remain independent canonical records.

The join model should stay explicit:

```kotlin
data class TimeBlock(
    val id: String,
    val date: LocalDate,
    val title: String,
    val category: String,
    val startMinuteOfDay: Int,
    val durationMinutes: Int,
    val timezone: String,
    val provenance: BlockProvenance,
    val flexibility: BlockFlexibility,
    val energyLevel: EnergyIntensity,
    val source: String,
    val taskId: String?,
    val calendarEventId: Long?,
    val medicationPlanId: String?,
    val habitId: String?,
    val isLocked: Boolean,
    val isProtected: Boolean,
    val recurrenceRuleId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

`DayPlan` should become a domain read model assembled from repositories:

```kotlin
data class DayPlan(
    val date: LocalDate,
    val timezone: ZoneId,
    val status: DayPlanStatus,
    val blocks: List<TimeBlock>,
    val conflicts: List<ScheduleConflict>,
    val review: DailyReviewSummary?
)
```

### Rule

- Tasks, habits, and medications define intent.
- `TimeBlock` defines placement on the Chronos Dial.
- `DayPlan` is the assembled daily schedule and review surface.
- `DailyReview` and `ActualTimeSegment` define what really happened.

### Target File Tree

```text
:core:domain
  model/
    DayPlan.kt
    DayPlanStatus.kt
    TimeBlock.kt
    Task.kt
    Habit.kt
    MedicationPlan.kt
    ScheduleConflict.kt
    DailyReview.kt
    ActualTimeSegment.kt
    ExactAlarmPolicy.kt
  planner/
    ConflictDetectionEngine.kt
    DayPlanAssembler.kt
    DialGeometry.kt
    PlannerCommand.kt
    PlannerCommandHistory.kt
    PlannerService.kt
  repository/
    DayPlanRepository.kt
    TaskRepository.kt
    HabitRepository.kt
    MedicationRepository.kt
    ReviewRepository.kt
    AlarmRequestRepository.kt
  usecase/
    ObserveDayPlanUseCase.kt
    MoveBlockUseCase.kt
    ResizeBlockUseCase.kt
    CreateBlockUseCase.kt
    LogActualTimeUseCase.kt
    CompleteDailyReviewUseCase.kt
    ScheduleMedicationReminderUseCase.kt

:core:data
  db/
    ChronosDatabase.kt
  model/
    DayPlanEntity.kt
    TimeBlockEntity.kt
    TaskEntity.kt
    HabitEntity.kt
    MedicationPlanEntity.kt
    DailyReviewEntity.kt
    ActualTimeSegmentEntity.kt
    AlarmRequestEntity.kt
  dao/
    DayPlanDao.kt
    TimeBlockDao.kt
    TaskDao.kt
    HabitDao.kt
    MedicationDao.kt
    ReviewDao.kt
    AlarmDao.kt
  mapper/
    DayPlanMapper.kt
    TimeBlockMapper.kt
    TaskMapper.kt
    HabitMapper.kt
    MedicationMapper.kt
    ReviewMapper.kt
  repository/
    DayPlanRepositoryImpl.kt
    TimeBlockRepositoryImpl.kt
    TaskRepositoryImpl.kt
    HabitRepositoryImpl.kt
    MedicationRepositoryImpl.kt
    ReviewRepositoryImpl.kt
    AlarmRequestRepositoryImpl.kt
  datastore/
    ChronosPreferencesDataSource.kt
```

### First Refactor Pass

Extract these responsibilities from `DayDialViewModel`:

- Free-time calculation -> `DayPlanAssembler` or `ConflictDetectionEngine`
- Planned-vs-actual math -> `DailyReviewCalculator`
- Drag validation -> `MoveBlockUseCase` and `ResizeBlockUseCase`
- Reminder scheduling -> `ScheduleMedicationReminderUseCase` and `AlarmRequestRepository`
- AI suggestion application -> `ApplyAiPlanUseCase`

The ViewModel should orchestrate UI state only.

## 2. Chronos Dial Engineering Specs

### Rendering Model

Keep Compose `Canvas`, but split rendering from geometry and hit testing.

```text
ChronosDial.kt
  Draws the dial and delegates math.

DialGeometry.kt
  minuteToAngle()
  angleToMinute()
  blockToArc()
  ringForRadius()
  hitTest(offset): DialHit
  snap(minute, grid)
```

Dial rings:

```text
Outer ring: calendar and fixed anchors
Middle ring: scheduled TimeBlock arcs
Inner ring: tasks, habits, medications, and compact status arcs
Center: now state, selected block details, quick action affordance
```

### Circular Touch Targets

Hit testing should be radial:

```text
distance = hypot(pointer.x - center.x, pointer.y - center.y)
angle = atan2(pointer.y - center.y, pointer.x - center.x)
minute = normalizedAngleToMinute(angle)
ring = radiusBand(distance)
```

Each selected block should expose three interaction zones:

```text
Move zone: arc body
Start resize zone: 44dp minimum handle centered on start angle
End resize zone: 44dp minimum handle centered on end angle
```

Use a sealed gesture state:

```kotlin
sealed interface DialDragMode {
    data class Move(val blockId: String, val anchorMinute: Int) : DialDragMode
    data class ResizeStart(val blockId: String) : DialDragMode
    data class ResizeEnd(val blockId: String) : DialDragMode
    data class Create(val startMinute: Int) : DialDragMode
}
```

During drag:

- Update an in-memory preview every pointer frame.
- Snap to 5 or 15 minute increments based on zoom mode.
- Run conflict preview through `ConflictDetectionEngine`.
- Commit to Room only on drag end.
- Emit haptics only when crossing snap, conflict, locked, or commit boundaries.

### Now Hand

The Now hand should update independently from the full screen state.

```kotlin
val nowMinute by produceState(initialValue = currentMinute()) {
    while (true) {
        value = currentMinute()
        delay(nextMinuteBoundaryDelay())
    }
}
```

Only the dial canvas should observe this ticker. Do not pipe minute ticks through a large `DayDialUiState`, or the entire screen will recompose every minute.

For 120fps polish:

- Animate the hand angle inside the dial only.
- Keep block arcs stable unless schedule state changes.
- Use immutable UI models.
- Avoid allocating lists and brushes inside per-frame draw paths where possible.

## 3. Liquid Glass Design System Implementation

### Base Theme

Use Material 3 Expressive and Material You dynamic color as the foundation. Dynamic color is available on Android 12 and above, so ChronosFlow should derive its base `ColorScheme` from `dynamicLightColorScheme` / `dynamicDarkColorScheme` when available, with a branded fallback.

Reference:

- https://developer.android.com/develop/ui/compose/designsystems/material3
- https://developer.android.com/jetpack/androidx/releases/compose-material3

### Glass Strategy

Glass should be a constrained surface treatment, not a full-screen filter.

```text
Tier 1: No blur, translucent tonal surface, all devices
Tier 2: RenderEffect blur for Android 12+, small bounded surfaces only
Tier 3: cached blurred backdrop layer for large panels and modal sheets
```

Implementation direction:

```kotlin
fun Modifier.chronosGlass(
    shape: Shape,
    tone: GlassTone,
    elevation: GlassElevation
) = composed {
    val colors = MaterialTheme.colorScheme
    val container = colors.surfaceContainerHigh.copy(alpha = tone.alpha)

    this
        .clip(shape)
        .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                renderEffect = RenderEffect
                    .createBlurEffect(tone.blurPx, tone.blurPx, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else Modifier)
        .background(container)
        .border(elevation.borderWidth, glassBorderBrush(colors.primary), shape)
}
```

Performance constraints:

- Never blur full-screen scrolling content.
- Clip before blur.
- Do not animate blur radius.
- Use alpha, tonal elevation, border highlight, and specular highlight animation instead.
- Cache expensive brushes.
- Macrobenchmark glass-heavy screens at 60Hz and 120Hz.

### Accessibility

Do not choose text color from the blurred pixels underneath. Glass containers must have a computed container color and a deterministic content color.

Rules:

- Use `surfaceContainer`, `surfaceContainerHigh`, or `surfaceContainerHighest` as the base.
- Add a scrim alpha based on light/dark theme and wallpaper dynamic color.
- Select `onSurface` or `onSurfaceVariant` against the final container.
- Raise scrim opacity until contrast reaches WCAG AA.
- Avoid placing critical medication/focus text on low-opacity glass.

## 4. Product Focus and Feature Promotion

Alpha active surface:

1. Chronos Dial Today
2. Day planning and rebalance
3. Focus execution
4. Global command palette

Parked feature promotion order:

1. Focus Engine refinements
2. Medication reliability as dial-backed reminders
3. AI-assisted planning and review sheets
4. Habit repair as scheduled/recovered dial intent

Reasoning:

- The alpha should not feel like a launcher full of disconnected feature screens.
- Focus Engine creates the daily loop and captures actual behavior.
- Medication Tracking is high-trust and high-retention, but only after reminder reliability is excellent and dial-backed.
- AI planning becomes useful only after the app has reliable planned-vs-actual history.
- Habit Repair should be insight-driven, not isolated streak mechanics.

### Daily Review Loop

At day close or next app open:

```text
1. Load planned TimeBlocks for date.
2. Load ActualTimeSegmentEntity rows.
3. Match actual segments by blockId first.
4. Fallback match by time overlap when blockId is absent.
5. Compute planned minutes, actual minutes, drift, missed blocks, interrupted blocks.
6. Persist DailyReviewEntity.
7. Emit ReviewInsight rows.
8. Feed structured review summaries into AI planning.
```

Required data model:

```kotlin
data class ActualTimeSegment(
    val id: String,
    val blockId: String?,
    val date: LocalDate,
    val startInstant: Instant,
    val endInstant: Instant?,
    val source: ActualTimeSource,
    val confidence: Float
)

data class DailyReviewSummary(
    val date: LocalDate,
    val plannedMinutes: Int,
    val actualMinutes: Int,
    val missedMinutes: Int,
    val driftMinutes: Int,
    val completedBlockCount: Int,
    val missedBlockCount: Int,
    val insights: List<ReviewInsight>
)
```

Do not store actual time only on `TimeBlock`. A focus session can pause, resume, extend, or be interrupted. Raw actual segments must be preserved.

## 5. System Integration and Reliability

### Android 16 Live Update / Progress Notification

Android 16 introduces progress-centric notifications through `Notification.ProgressStyle`, intended for user-initiated start-to-end journeys. ChronosFlow should use this for active focus sessions.

Reference:

- https://developer.android.com/about/versions/16/features/progress-centric-notifications

Focus state machine:

```text
Idle
  -> Preparing(blockId)
  -> Running(sessionId, startedAt)
  -> Paused(pausedAt)
  -> Running
  -> Extending(newPlannedEnd)
  -> Completing
  -> Completed(actualSegments)
  -> Reviewing
  -> Archived

Failure states:
  InterruptedBySystem
  PermissionBlocked
  ServiceKilledRecoverable
```

Implementation split:

```text
:core:domain
  FocusSessionState.kt
  FocusSessionReducer.kt

:feature:focus
  FocusSessionViewModel.kt
  FocusForegroundService.kt

:core:notifications
  FocusProgressNotificationRenderer.kt
  MedicationAlarmReceiver.kt
  AlarmScheduler.kt
```

The foreground service should be recoverable from persisted `FocusSessionEntity`, not from in-memory ViewModel state.

### Exact Alarm Strategy

Android requires checking `AlarmManager.canScheduleExactAlarms()` before exact scheduling on modern targets. `SCHEDULE_EXACT_ALARM` can be denied by default for new installs targeting Android 13 and above, and apps must degrade gracefully if denied.

References:

- https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
- https://developer.android.com/develop/background-work/services/alarms/schedule

Alarm priority:

```text
Medication reminders:
  exact alarm required when permission is granted
  user-facing degraded state if denied

Focus/block reminders:
  exact only when user explicitly asks
  otherwise inexact alarm or WorkManager

Daily review:
  inexact alarm/window is acceptable
```

Medication fallback policy:

```text
1. Persist AlarmRequestEntity before scheduling.
2. If canScheduleExactAlarms() is true:
     use setExactAndAllowWhileIdle.
3. If exact permission is denied:
     use setWindow fallback.
     show persistent in-app degraded reliability warning.
     expose action to open exact alarm settings.
4. On boot, timezone change, package update, and permission-state change:
     rebuild all pending medication alarms from AlarmRequestEntity.
5. On alarm fire:
     persist delivery result.
     show notification.
     update medication adherence state only after user action or timeout rule.
```

Medication "never fail" means observable redundancy, not pretending Android guarantees delivery. ChronosFlow must show the user when exact delivery is degraded.

## Phased Execution Plan

### Phase 0: Baseline

- Run compile and unit tests.
- Verify current DayDial and Focus flows.
- Identify demo/mock data paths and remove production leakage.
- Freeze current screenshots as visual regression references.

### Phase 1: Domain Extraction

- Add `ConflictDetectionEngine`.
- Add `DialGeometry`.
- Add `DayPlanAssembler`.
- Add `DailyReviewCalculator`.
- Move planner math and review math out of `DayDialViewModel`.

### Phase 2: Data Model Upgrade

- Add `ActualTimeSegmentEntity`.
- Add `DailyReviewEntity`.
- Add `MedicationPlanEntity`.
- Add `HabitEntity`.
- Add `AlarmRequestEntity`.
- Add migrations and mapper tests.

### Phase 3: Dial Interaction Rewrite

- Replace ad hoc touch handling with `DialHit` and `DialDragMode`.
- Add start/end resize handles.
- Add drag preview state.
- Commit persistence only on drag end.
- Add conflict preview and haptic boundary cues.

### Phase 4: Focus and Notification Reliability

- Persist focus session state.
- Move focus lifecycle to a reducer.
- Implement Android 16 progress-centric notification path.
- Keep a foreground-service fallback notification.
- Add service restart recovery tests.

### Phase 5: Medication Alarm Hardening

- Implement exact alarm permission flow.
- Add fallback scheduling policy.
- Rebuild alarms on boot/timezone/package/permission events.
- Add alarm delivery audit trail.

### Phase 6: Design System Polish

- Centralize Material 3 Expressive tokens in `:core:ui`.
- Replace one-off glass modifiers with `chronosGlass`.
- Add contrast resolver.
- Add macrobenchmarks for DayDial and glass-heavy sheets.
- Test on 60Hz and 120Hz devices.

### Phase 7: AI Review Sheets

- Feed AI structured `DailyReviewSummary`, not ViewModel state.
- Generate planning hypotheses from missed blocks, drift, focus underruns, medication adherence, and habit windows.
- Keep AI suggestions as optional blocks until accepted by the user.

## Completion Bar

ChronosFlow is production-grade when:

- The Chronos Dial is the single source of scheduled truth.
- Tasks, habits, medications, and calendar items have canonical records outside the dial.
- Every scheduled arc can be traced back to an intent.
- Every actual session can be traced back to raw time segments.
- Focus sessions survive process death.
- Medication reminders expose their reliability state.
- Glass UI preserves accessibility and frame time.
- The ViewModels orchestrate state instead of owning business logic.
