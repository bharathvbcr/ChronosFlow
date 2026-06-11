# ChronosFlow

ChronosFlow is an Android productivity suite built around a simple idea: your day should be visible, adjustable, and learn from how you actually spend your time.

The app combines a 24-hour radial planner, task management, habit tracking, medication reminders, focus sessions, calendar overlays, mood and energy check-ins, and AI-assisted day planning into one calm daily operating system.

## Product Positioning

ChronosFlow is designed as an Android 16 production-ready, Android 17 compatibility-tested, Material 3 Expressive-ready productivity app.

It is not just a task list. ChronosFlow helps users plan the day, execute the day, track what happened, and improve tomorrow.

## Core Experience

### Chronos Dial

The Chronos Dial is the app's signature planner: a 24-hour circular view of the day.

- Outer ring: calendar events and fixed commitments
- Middle ring: planned time blocks
- Inner ring: tasks, focus sessions, habits, and medication reminders
- Center summary: current block, next block, planned time, free time, and conflicts
- Now hand: current time indicator

Users can tap, drag, resize, lock, and edit time blocks directly on the dial.

### Daily Planner

ChronosFlow supports structured day planning with:

- Time blocks
- Task scheduling
- Free-time detection
- Conflict warnings
- Locked calendar commitments
- AI-generated plan suggestions
- Manual review before any AI change is applied

### Tasks

The task system supports:

- Task creation and completion
- Priorities
- Due dates
- Subtasks
- Recurrence
- Conversion from task to scheduled time block
- Focus-session launch from a scheduled task

### Habit Tracker

Habits are first-class planning objects, not just streak counters.

- Daily and weekly habits
- Flexible targets, such as 3 times per week
- Habit windows, such as workout between 6 AM and 10 AM
- Streaks and consistency scores
- Habit markers on the Chronos Dial
- Missed-habit repair suggestions
- Habit bundling, such as vitamins after breakfast

### Medication Reminders

ChronosFlow includes careful medication tracking and reminders.

- Medication schedules
- Exact reminders when appropriate
- Dose acknowledgement
- Missed-dose follow-up
- Refill reminders
- Notes for skipped doses or side effects

ChronosFlow tracks and reminds. It does not provide medical advice.

### Focus Engine

Focus sessions turn planned blocks into active work.

- Start focus from a time block
- Pomodoro or custom duration
- Active foreground notification
- Android 16 Live Update support for active sessions
- Distraction notes
- Completion tracking
- Focus history

### Mood and Energy Check-Ins

Short check-ins help ChronosFlow learn when users work best.

- Mood
- Energy
- Stress
- Focus level
- Optional notes
- Pattern discovery over time
- Better deep-work suggestions

### Sleep and Routines

ChronosFlow can model recurring personal rhythms.

- Sleep blocks
- Wind-down reminders
- Morning routines
- Evening routines
- Workout routines
- Study routines
- Planned versus actual comparison

### Daily Review

The daily review closes the feedback loop.

- What was planned
- What actually happened
- What was skipped
- Why it changed
- What should be adjusted tomorrow

This makes ChronosFlow a system for improving time use, not just recording tasks.

## AI Planning

ChronosFlow uses AI as an assistant, not an autopilot.

Supported planning flows:

- Generate an ideal day
- Repair an overloaded plan
- Find deep-work windows
- Reschedule missed habits
- Balance sleep, work, study, exercise, and breaks
- Explain why a plan was suggested

AI suggestions are shown in a review sheet before they modify the user's day.

## Android Platform Direction

ChronosFlow is designed for modern Android surfaces:

- Jetpack Compose
- Material 3 and Material 3 Expressive readiness
- Dynamic color on Android 12+
- Edge-to-edge layouts
- Adaptive phone, tablet, foldable, desktop-window, and bubble layouts
- Keyboard, mouse, trackpad, and stylus-friendly interactions
- Android 16 progress-centric notifications for active focus sessions
- Android 17 compatibility testing

## Exact Alarm Strategy

ChronosFlow uses alarms carefully.

Inexact scheduling is preferred for:

- Soft planner nudges
- Habit reminders
- Non-critical task reminders

Exact alarms are reserved for:

- User-specified precise reminders
- Medication reminders that need precision
- Focus check-ins tied to scheduled work blocks

The app uses `SCHEDULE_EXACT_ALARM` where appropriate and must provide fallback behavior when exact alarms are unavailable.

## Live Update Strategy

Live Updates are only used for active, user-initiated, time-sensitive sessions.

Good examples:

- Active focus session
- Active workout session
- Medication dose awaiting acknowledgement

Avoided examples:

- Passive planner status
- Generic upcoming events
- Promotional notifications

## Architecture

Current module direction:

```text
:app
:benchmark
:core:ai
:core:data
:core:notifications
:feature:daydial
:feature:focus
:feature:tasks
```

Planned domain cleanup:

```text
:core:domain
  - pure domain models
  - scheduling rules
  - planner use cases
  - conflict detection

:core:data
  - Room entities
  - DAOs
  - repositories
  - entity/domain mappers
```

## Planned Feature Modules

```text
:feature:habits
:feature:medication
:feature:review
:feature:routines
:feature:calendar
:feature:insights
```

## Development Status

ChronosFlow is currently in early implementation.

Implemented or scaffolded:

- Gradle project structure
- Compose BOM baseline
- Room data layer
- Task scaffold
- Chronos Dial scaffold
- Focus timer scaffold
- Alarm scheduler scaffold
- Android manifest permission posture
- Baseline profile and macrobenchmark scaffold
- AI planner scaffold

Still needed:

- Gradle wrapper
- Build verification
- Domain/data boundary cleanup
- `DayPlanEntity`
- Chronos Dial editor sheet
- Conflict detection
- Habit module
- Medication module
- Daily review module
- Runtime notification and exact-alarm permission flows
- Android 17 compatibility lane

## Build Notes

The repository expects a standard Android Gradle environment with:

- Android Studio or Android SDK
- JDK 17+
- Gradle wrapper, once added

After restoring or adding the Gradle wrapper, the expected verification command is:

```powershell
.\gradlew.bat :app:assembleDebug
```

Benchmark verification:

```powershell
.\gradlew.bat :benchmark:connectedCheck
```

## Design Principles

- Make time visible.
- Keep planning fast.
- Prefer calm hierarchy over visual noise.
- Let users review AI changes before applying them.
- Treat habits, medication, focus, sleep, and tasks as parts of one day.
- Support large screens and non-touch input from the start.
- Use exact alarms and Live Updates only where they are justified.

