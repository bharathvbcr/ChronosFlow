# Focus Live Updates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android focus foreground notification own the active session lifecycle, use a real running timer metric on Android 17, and ensure notification actions mutate the actual timer state even when the UI is closed.

**Architecture:** Keep `FocusSessionReducer` as the state-transition engine, move active countdown ownership into `FocusService`, and make `FocusViewModel` a UI projection over repository-backed session state instead of the timer source. Notification rendering remains in `core/notifications`, with a small API expansion so the primary action can switch between pause and resume.

**Tech Stack:** Kotlin, Android foreground service, Jetpack Compose, Room-backed repository, JUnit, MockK, coroutines-test

---

### Task 1: Add service-runtime tests and seams

**Files:**
- Create: `feature/focus/src/test/java/com/chronosflow/feature/focus/FocusSessionRuntimeTest.kt`
- Modify: `feature/focus/build.gradle.kts`
- Modify: `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusService.kt`
- Create: `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusSessionRuntime.kt`

- [x] **Step 1: Write the failing runtime tests**

```kotlin
@Test
fun `pause freezes remaining time and resume restores running countdown`() {
    val runtime = FocusSessionRuntime(
        reducer = FocusSessionReducer(),
        now = { clock.instant() }
    )

    runtime.start(
        sessionId = "session-1",
        blockId = "block-1",
        timeLeftSeconds = 1500,
        totalSeconds = 1500
    )
    clock.advanceBySeconds(300)

    val paused = runtime.pause()
    clock.advanceBySeconds(120)
    val resumed = runtime.resume()

    assertEquals(1200, paused.timeLeftSeconds)
    assertEquals(1200, resumed.timeLeftSeconds)
    assertTrue(resumed.isRunning)
}
```

- [x] **Step 2: Run the new runtime test to verify it fails**

Run: `./gradlew :feature:focus:testDebugUnitTest --tests com.chronosflow.feature.focus.FocusSessionRuntimeTest`
Expected: FAIL because `FocusSessionRuntime` does not exist yet

- [x] **Step 3: Implement a minimal service-owned runtime helper**

```kotlin
internal class FocusSessionRuntime(
    private val reducer: FocusSessionReducer,
    private val now: () -> Instant = Instant::now
) {
    // Holds current state, total duration, and derives remaining time.
}
```

- [x] **Step 4: Re-run the runtime test to verify it passes**

Run: `./gradlew :feature:focus:testDebugUnitTest --tests com.chronosflow.feature.focus.FocusSessionRuntimeTest`
Expected: PASS

### Task 2: Add notification metric/action tests

**Files:**
- Modify: `core/notifications/src/test/java/com/chronosflow/core/notifications/LiveUpdateGatewayTest.kt`
- Modify: `core/notifications/src/main/java/com/chronosflow/core/notifications/LiveUpdateGateway.kt`
- Modify: `core/notifications/src/main/java/com/chronosflow/core/notifications/FocusProgressNotificationRenderer.kt`

- [x] **Step 1: Write failing tests for running timer metric and pause/resume primary action**

```kotlin
@Test
fun `android 17 metric style uses running timer for active sessions`() {
    assertEquals(
        LiveMetricTimerMode.RUNNING,
        liveMetricTimerMode(isPaused = false)
    )
}

@Test
fun `paused sessions use resume as the primary notification action`() {
    assertEquals(
        NotificationPrimaryAction.RESUME,
        notificationPrimaryAction(isPaused = true)
    )
}
```

- [x] **Step 2: Run the notification tests to verify they fail**

Run: `./gradlew :core:notifications:testDebugUnitTest --tests com.chronosflow.core.notifications.LiveUpdateGatewayTest`
Expected: FAIL because the new timer-mode/action helpers do not exist yet

- [x] **Step 3: Implement the minimal notification behavior changes**

```kotlin
internal enum class LiveMetricTimerMode { RUNNING, PAUSED }
internal enum class NotificationPrimaryAction { PAUSE, RESUME }
```

- [x] **Step 4: Re-run the notification tests to verify they pass**

Run: `./gradlew :core:notifications:testDebugUnitTest --tests com.chronosflow.core.notifications.LiveUpdateGatewayTest`
Expected: PASS

### Task 3: Move active session ownership into the foreground service

**Files:**
- Modify: `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusService.kt`
- Modify: `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusScreen.kt`
- Modify: `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusViewModel.kt`
- Modify: `app/src/main/java/com/chronosflow/widget/FocusWidgetAction.kt`

- [x] **Step 1: Make `FocusService` process start/pause/resume/extend/stop via the runtime helper**

```kotlin
when (intent?.action) {
    ACTION_START -> startOrResumeFromIntent(intent)
    ACTION_PAUSE -> pauseActiveSession()
    ACTION_RESUME -> resumeActiveSession()
    ACTION_EXTEND -> extendActiveSession()
    ACTION_STOP -> stopActiveSession(archive = intent.getBooleanExtra(EXTRA_ARCHIVE_ON_STOP, false))
}
```

- [x] **Step 2: Add a service ticker that refreshes the foreground notification from service-owned state**

```kotlin
timerJob = serviceScope.launch {
    while (runtime.isRunning) {
        updateForegroundNotification(runtime.snapshot())
        delay(1_000)
    }
}
```

- [x] **Step 3: Remove the UI-driven `ACTION_UPDATE` loop and have the screen send only explicit commands**

```kotlin
Button(onClick = {
    context.sendFocusServiceCommand(
        action = if (isPaused) FocusService.ACTION_RESUME else FocusService.ACTION_START,
        // ...
    )
})
```

- [x] **Step 4: Re-run the focus module tests**

Run: `./gradlew :feature:focus:testDebugUnitTest`
Expected: PASS

### Task 4: Keep UI state aligned with repository-backed session state

**Files:**
- Modify: `feature/focus/src/main/java/com/chronosflow/feature/focus/FocusViewModel.kt`

- [x] **Step 1: Observe recoverable session state and project `isRunning`, `isPaused`, `sessionId`, and `timeLeft` for the screen**

```kotlin
focusSessionRepository.observeRecoverableSession().collect { session ->
    sessionState = session ?: FocusSessionState.Idle
    syncUiFromSession(session)
}
```

- [x] **Step 2: Keep a UI-only ticker that derives display time from session state without owning the session**

```kotlin
uiTickerJob = viewModelScope.launch {
    while (sessionState is FocusSessionState.Running) {
        updateClockFromSession()
        delay(1_000)
    }
}
```

- [x] **Step 3: Verify the UI still resets cleanly when the active session is archived/stopped**

Run: `./gradlew :feature:focus:testDebugUnitTest`
Expected: PASS

### Task 5: Run focused verification

**Files:**
- Modify: `core/notifications/src/test/java/com/chronosflow/core/notifications/LiveUpdateGatewayTest.kt`
- Modify: `feature/focus/src/test/java/com/chronosflow/feature/focus/FocusSessionRuntimeTest.kt`

- [x] **Step 1: Run notification tests**

Run: `./gradlew :core:notifications:testDebugUnitTest --tests com.chronosflow.core.notifications.LiveUpdateGatewayTest`
Expected: PASS

- [x] **Step 2: Run focus tests**

Run: `./gradlew :feature:focus:testDebugUnitTest`
Expected: PASS

- [x] **Step 3: Run one cross-module confidence check**

Run: `./gradlew :core:notifications:testDebugUnitTest :feature:focus:testDebugUnitTest`
Expected: PASS
