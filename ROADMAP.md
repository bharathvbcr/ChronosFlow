# ChronosFlow: Production-Grade Transformation Roadmap

This roadmap is a long-horizon implementation plan. For current status and shipped behavior, use `README.md` and `docs/product-contract.md` as the current contract.

## Context

ChronosFlow is a Jetpack Compose day-planning app with a 24-hour radial "Chronos Dial" planner. While the **domain layer** is well-architected (clean models, use cases, command pattern with undo/redo, conflict detection engine), the **feature layer** has repeatedly drifted toward disconnected screens. This plan keeps modular integrity while making the dial-centered flow the product spine.

**Tech Stack:** Kotlin 2.2.21, Compose BOM 2026.04.01, Room 2.8.3, Hilt 2.57.2, Navigation 2.9.6, minSdk 26, targetSdk/compileSdk 37.

---

## Implementation Status Snapshot

This roadmap is now mostly implemented at the source level. The current alpha direction is intentionally narrower than the full module map: the app shell should feel dial-first, with Today, Plan, Focus, and the command palette as the active product surface.

| Area | Current source status | Remaining non-emulation work |
|------|-----------------------|-------------------------------|
| Module dependencies | `:feature:tasks` is domain/UI-only; focus isolation has data available for tests. | Covered by `chronosCiCheck` module isolation builds. |
| DayDial ViewModel split | `DayDialViewModel` coordinates injected delegates for block, focus, AI, reminder, and review behavior. | Continue delegate-level unit coverage as behavior expands. |
| DayDial screen decomposition | The DayDial screen is split across coordinator, chrome, state/effects, tabs, sheet host, and focused UI files. | Keep gesture/sheet regression tests current. |
| Navigation architecture | `ChronosNavGraph`, `ChronosRoute`, and `ChronosBottomBar` keep routes compiled, while `ChronosRoute.activeTopLevel` limits the alpha entry topology to Day and Focus. | Keep parked feature routes available for later promotion, but do not expose them as primary entry points until dial wiring is proven. |
| Liquid Material UI | Shared glass/backdrop primitives are in `:core:ui`; DayDial, Focus, Tasks, Habits, Medication, and Review use the shared surfaces. | Add/refresh screenshot and accessibility checks. |
| Feature modules | Habits, Medication, and Review feature modules exist with screens, view models, command providers, and core data/domain integration, but are parked as standalone destinations in alpha. | Promote one only when it has a clear dial entry point, persistence-backed flow, command coverage, and focused tests. |
| Command palette | `CommandPaletteDialog` lives in `:core:ui`; the app shell currently registers DayDial and Focus commands only. Parked feature providers stay tested in their modules. | Provider metadata/callback tests and command-palette Android tests are in `chronosCiCheck`. |
| AI review staging | `AiReviewSheet` supports per-suggestion Accept/Reject/Modify plus visible `Apply All` and `Dismiss All` bulk actions. | Delegate tests and AI review Android test APK assembly are in `chronosCiCheck`. |
| Dial ring semantics | `ChronosDial` has three-ring assignment for calendar, normal blocks, and task/habit/medication blocks with ring-aware hit testing. | Keep ring semantic tests and interaction tests current. |
| Plan repair/deep work | `ChronosAIPlanner.repairDayPlan()` and `DeepWorkWindowDetector` are implemented with on-device heuristics and tests. | Improve quality heuristics as real usage data arrives. |
| Live Updates | `LiveUpdateRenderer` is wired behind `FocusProgressNotificationRenderer` with fallback support. | Device/emulator certification remains outside this non-emulation cleanup pass. |

The historical sections below are preserved as implementation context. Treat old baseline wording as stale when contradicted by the status snapshot above.

---

## Phase 1: Architecture Stabilization (Priority 1)

### 1.1 Fix Module Dependencies (1-2 days)

| Action | File | Detail |
|--------|------|--------|
| Remove unused `:core:data` dep | `feature/tasks/build.gradle.kts` | `TaskViewModel` only uses domain use cases — `:core:data` is unnecessary |
| Add test-time `:core:data` to focus | `feature/focus/build.gradle.kts` | Runtime works via Hilt at `:app` level, but isolation tests need it on classpath |

**Verification:** `./gradlew :feature:tasks:assembleDebug` succeeds without `:core:data`.

---

### 1.2 Split DayDialViewModel (3-5 days)

Decompose the 1165-line ViewModel into delegate classes:

| Delegate | Responsibility |
|----------|---------------|
| `DayDialBlockDelegate` | Block CRUD, drag/drop, move/resize, undo/redo |
| `DayDialFocusDelegate` | Focus session start/pause/resume/finish/extend |
| `DayDialAiDelegate` | AI plan generation, apply/reject suggestions, explain |
| `DayDialReminderDelegate` | Alarm scheduling, medication reliability |
| `DayDialReviewDelegate` | Daily review calculation, missed blocks, insights |
| `DayDialViewModel` (coordinator) | Date selection, state composition, delegate wiring |

**Pattern:** Delegates are plain `@Inject` classes receiving `CoroutineScope` and repositories. Not ViewModels themselves.

**Create:** `feature/daydial/src/main/java/com/chronosflow/feature/daydial/delegate/*.kt`  
**Modify:** `feature/daydial/.../DayDialViewModel.kt` — reduce to ~200-line coordinator.

---

### 1.3 Decompose DayDialScreen.kt (5-7 days)

Extract the monolithic composable (~32k tokens) into focused files:

```
feature/daydial/.../
  DayDialScreen.kt              (slim coordinator ~200 lines)
  ChronosDial.kt                (keep as-is)
  DialUtils.kt                  (keep as-is)
  delegate/                     (from 1.2)
  ui/
    TodayTab.kt
    PlanTab.kt
    FocusTab.kt
    InsightsTab.kt
    DayDialSidebar.kt
    SidebarPageContent.kt
    BottomFloatingNav.kt
    CommandPaletteDialog.kt
    SheetContent.kt
    BlockEditor.kt
  model/
    TimeBlockUiModel.kt
    TimeRangeUi.kt
    DayDialTab.kt
    SidebarPage.kt
    SheetTarget.kt
    CommandPaletteItem.kt
```

**Risk:** Gesture state breakage.  
**Mitigation:** Keep all `remember`/`rememberSaveable` at coordinator level; pass callbacks down.

---

### 1.4 Navigation Architecture (3-4 days)

Replace hardcoded 3-route `NavHost` in `MainActivity.kt` with proper structure:

**Create:**
- `app/.../navigation/ChronosNavGraph.kt` — NavHost with all routes
- `app/.../navigation/ChronosRoute.kt` — sealed interface for type-safe routes
- `app/.../navigation/ChronosBottomBar.kt` — Material 3 NavigationBar

**Modify:** `app/.../MainActivity.kt` — use new navigation components.

**Routes (expanded):** `day`, `focus?blockId={id}`, `tasks`, `habits`, `medication`, `review`

## Phase 2: Liquid Material UI/UX Overhaul (Priority 2)

### 2.1 Material 3 Expressive Tab Pills (2-3 days)

- Replace custom `BottomFloatingNav` with M3 `TabRow` using pill-shaped `Tab` segments
- Use `ChronosGlassTokens.CompactRadius = 18.dp` for pill corners
- Tabs: TODAY, PLAN, FOCUS, INSIGHTS

**Modify:** `feature/daydial/.../ui/BottomFloatingNav.kt`

### 2.2 Edge-to-Edge (2 days)

- Enable `enableEdgeToEdge()` in `MainActivity.onCreate()`
- Apply `WindowInsets` padding in all screens
- Transparent system bars with LiquidBackdrop drawing behind status bar

**Modify:** `MainActivity.kt`, all `*Screen.kt` files.

### 2.3 Bottom Navigation with Contextual Modes (3-4 days)

- Material 3 `NavigationBar` at app level
- Alpha destinations: Daily and Focus, with Today/Plan/Focus inside the DayDial shell
- Parked destinations: Tasks, Habits, Medication, and Review stay out of primary navigation until their dial handoff is proven
- Badge indicators: missed blocks count, active session pulse, unread insights

**Depends on:** Milestone 1.4

### 2.4 Command Palette (Global) (2-3 days)

- Extract `CommandPaletteDialog` from DayDialScreen to `:core:ui`
- Accessible from any screen via top-bar search icon
- Each promoted feature module provides a `CommandProvider` interface
- Alpha app shell registers DayDial and Focus commands only; parked feature providers remain module-owned for later promotion
- Fuzzy matching, keyboard shortcut support (Ctrl+K)

**Create:** `core/ui/.../components/CommandPalette.kt`

### 2.5 LiquidGlass Consistency Pass (2-3 days)

| Screen | Current State | Fix |
|--------|--------------|-----|
| FocusScreen | No glass effects | Add `liquidGlass()`, `LiquidBackdrop`, `ChronosGlassPanel` |
| TaskScreen | Partial (uses `ChronosGlassPanel`) | Add `liquidGlass()` to cards, standardize borders |
| New modules | N/A | Built with glass system from day one |

## Phase 3: Core Feature Build-Out (Priority 3)

### 3.1 `:feature:habits` Module (5-7 days)

**Infrastructure already exists:** `Habit` model, `HabitEntity`, `HabitDao`, `HabitRepository`, `HabitRepositoryImpl` are all implemented in core layers.

**Build:**
- `feature/habits/build.gradle.kts`
- `HabitScreen.kt` — list/grid view with streak visualization
- `HabitViewModel.kt` — CRUD, completion tracking, streak calculation
- `HabitStreakChart.kt` — calendar heatmap or ring chart

**Use cases to add in `:core:domain`:**
- `CompleteHabitUseCase`
- `GetActiveHabitsUseCase`
- `ObserveHabitStreaksUseCase`

**AI Integration:** "Missed-habit repair" suggestions — when a habit window passes without completion, the AI planner proposes a recovery slot in the next available free time.

**Dial Integration:** Inner ring of ChronosDial shows habit windows; tap to mark complete.

---

### 3.2 `:feature:medication` Module (5-7 days)

**Infrastructure already exists:** `MedicationPlan` model, `MedicationPlanEntity`, `MedicationDao`, `MedicationRepository`, `MedicationRepositoryImpl`, `ScheduleMedicationReminderUseCase`, `MedicationAlarmReceiver`, `AlarmScheduler`.

**Build:**
- `feature/medication/build.gradle.kts`
- `MedicationScreen.kt` — medication list, add/edit forms, dosage tracking
- `MedicationViewModel.kt` — CRUD, alarm scheduling, adherence calculation
- `MedicationAdherenceChart.kt` — compliance tracking visualization

**Key logic flow:**
```
ScheduleMedicationReminderUseCase
  -> AlarmScheduler.scheduleExact()
    -> AlarmManager.setExactAndAllowWhileIdle()
      -> MedicationAlarmReceiver (onReceive)
        -> Notification delivery
```

**Permission handling:** `SCHEDULE_EXACT_ALARM` with graceful degradation to inexact 10-minute windows via existing `AlarmScheduleResult.ExactDenied` path.

---

### 3.3 `:feature:review` Module (4-5 days)

**Infrastructure already exists:** `DailyReviewSummary`, `ReviewInsight`, `DailyReviewCalculator`, `CompleteDailyReviewUseCase`, `ReviewRepository`, `DailyReviewEntity`.

**Build:**
- `feature/review/build.gradle.kts`
- `ReviewScreen.kt` — planned vs actual breakdown, insight cards, weekly summary
- `ReviewViewModel.kt` — review data loading, insight generation
- `InsightCard.kt` — styled insight display with severity coloring

**Extract from:** DayDialScreen sidebar INSIGHTS tab content.

---

### 3.4 Chronos Dial Ring Formalization (3-4 days)

Formalize the three-ring semantic model:

| Ring | Radius Fraction | Content | Assignment Logic |
|------|----------------|---------|-----------------|
| **Outer** | 0.92f | Calendar imports | `block.calendarEventId != null` |
| **Middle** | 0.68f | Time blocks | `block.provenance in [USER_CREATED, AI_SUGGESTED, SYSTEM_GENERATED]` |
| **Inner** | 0.32f | Tasks/Habits/Meds | `block.taskId != null \|\| block.habitId != null \|\| block.medicationPlanId != null` |

**Modify:** `feature/daydial/.../ChronosDial.kt` — ring assignment logic in `drawBlocks()`.

**Interaction model:**
- Tap outer ring -> show calendar event detail
- Tap middle ring -> select block for edit/move/resize
- Tap inner ring -> quick-complete task/habit or acknowledge medication
- Long-press any ring -> create new block in that category

**Risk:** Breaking existing gesture handling.  
**Mitigation:** Feature-flag three-ring mode; keep current single-ring as fallback.

---

## Phase 4: AI & Platform Integration (Priority 4)

### 4.1 AI Review Sheet / Staging Area (4-5 days)

- Sheet UI showing each AI-suggested block with Accept/Modify/Reject per block
- Diff view: what changes vs current day plan (added blocks in green, displaced in amber)
- Explanation text per suggestion (from `ChronosAIPlanner.buildReviewExplanation`)
- "Apply All" and "Dismiss All" bulk actions

**Status:** Implemented. `AiReviewSheet` now exposes per-block Accept/Modify/Reject plus visible `Apply All` and `Dismiss All` bulk controls.

**Create:** `feature/daydial/.../ui/AiReviewSheet.kt`  
**Modify:** `DayDialAiDelegate` — add `acceptSuggestion(id)`, `rejectSuggestion(id)`, `modifySuggestion(id, block)`

---

### 4.2 Android 16 Live Updates (3-4 days)

- Replace standard foreground notification with Progress-type Live Updates (API 35+)
- Rich progress: elapsed/remaining time, segmented progress bar
- Action buttons: Pause, Stop, Extend (+15m)
- Graceful fallback to current `FocusProgressNotificationRenderer` on API < 35

**Modify:** `feature/focus/.../FocusService.kt`, `core/notifications/.../FocusProgressNotificationRenderer.kt`  
**Create:** `core/notifications/.../LiveUpdateRenderer.kt`

---

### 4.3 Plan Repair & Deep-Work Windows (3-4 days)

- Implement real logic in `ChronosAIPlanner.repairDayPlan()` (currently returns stub string)
- Deep-work window detection algorithm:
  1. Find all free-time segments > 90 minutes
  2. Rank by energy curve alignment (morning = high energy for most users)
  3. Protect from fragmentation by adjacent low-priority blocks
- Auto-resolve conflicts via `ConflictDetectionEngine`
- "Repair Plan" button in PlanTab when conflicts detected

**Modify:** `core/ai/.../ChronosAIPlanner.kt`  
**Create:** `core/ai/.../DeepWorkWindowDetector.kt`

---

### 4.4 Android 17 Compatibility Testing (2-3 days)

| Area | Test | File |
|------|------|------|
| Notification posture | Foldable states (folded/unfolded/tabletop) | All notification builders |
| Exact alarm permissions | Revocation mid-session, re-grant flow | `AlarmScheduler.kt` |
| Edge-to-edge | New system UI gesture conflicts | All screens |
| Privacy | Background activity restrictions | `FocusService.kt`, `ReminderBootReceiver.kt` |

---

## Critical Path & Parallelization

```
Track A (UI decomposition):  1.1 -> 1.2 -> 1.3 -> 2.1 -> 2.3 -> 2.5
Track B (Navigation):        1.4 -> 2.2 -> 2.3 (merges with Track A)
Track C (New features):      3.1, 3.2, 3.3 (parallel, after 1.1)
Track D (Dial & AI):         3.4 (after 3.1-3.3), 4.1 (after 1.2), 4.3 (after 4.1)
Track E (Platform):          4.2, 4.4 (independent, anytime)
```

**Estimated total:** 12-16 weeks with single developer; 6-8 weeks with 2 parallel tracks.

---

## Risk Matrix

| Risk | Phase | Severity | Mitigation |
|------|-------|----------|------------|
| DayDialScreen decomposition breaks gesture state | 1.3 | **HIGH** | Keep `remember` state at coordinator; pass callbacks |
| Room schema changes without migration | 3.x | **HIGH** | Migration tests; never alter columns without migration file |
| Dial ring formalization breaks touch handling | 3.4 | **HIGH** | Feature-flag; keep single-ring fallback |
| M3 Expressive APIs not stable | 2.1 | MEDIUM | Pin artifact version; `@OptIn` annotations |
| Navigation refactor breaks notification deep links | 1.4 | MEDIUM | Integration tests for all PendingIntent targets first |
| AI plan repair produces poor suggestions (no cloud model) | 4.3 | MEDIUM | Keep as structured heuristic; label "on-device suggestion" |
| New modules increase build time | 3.x | MEDIUM | Gradle build cache, configuration cache |
| Live Updates API device coverage | 4.2 | LOW | Version-gated with standard notification fallback |

---

## Verification Strategy

| Phase | Method |
|-------|--------|
| **1 (Architecture)** | Unit tests pass, build succeeds per-module, Compose Previews render |
| **2 (UI)** | Paparazzi/Roborazzi screenshot tests (light/dark/contrast), TalkBack walkthrough |
| **3 (Features)** | DAO instrumented tests, E2E flows (create -> complete -> verify), module isolation builds |
| **4 (Platform)** | Manual testing on API 35+ emulator, `AlarmScheduler` permission edge cases |

---

## Key Files Reference

| Category | Path |
|----------|------|
| Monolithic screen (decompose) | `feature/daydial/.../DayDialScreen.kt` |
| Monolithic VM (split) | `feature/daydial/.../DayDialViewModel.kt` |
| Dial canvas | `feature/daydial/.../ChronosDial.kt` |
| Dial geometry (domain) | `core/domain/.../planner/DialGeometry.kt` |
| Navigation entry | `app/.../MainActivity.kt` |
| AI Planner (stubs to implement) | `core/ai/.../ChronosAIPlanner.kt` |
| Alarm system | `core/notifications/.../AlarmScheduler.kt` |
| Medication receiver | `core/notifications/.../MedicationAlarmReceiver.kt` |
| Boot reschedule | `core/notifications/.../ReminderBootReceiver.kt` |
| Focus service | `feature/focus/.../FocusService.kt` |
| Focus notification | `core/notifications/.../FocusProgressNotificationRenderer.kt` |
| Glass modifier | `core/ui/.../theme/LiquidGlassModifier.kt` |
| Glass backdrop | `core/ui/.../components/LiquidBackdrop.kt` |
| Design tokens | `core/ui/.../theme/DesignTokens.kt` |
| Theme | `core/ui/.../theme/ChronosTheme.kt` |
| Room database | `core/data/.../ChronosDatabase.kt` |
| Entity mappers | `core/data/.../Mappers.kt` |
| Domain models | `core/domain/.../model/*.kt` |
| Planner service | `core/domain/.../planner/PlannerService.kt` |
| Conflict engine | `core/domain/.../planner/ConflictDetectionEngine.kt` |
| Focus reducer | `core/domain/.../planner/FocusSessionReducer.kt` |
| Settings | `settings.gradle.kts` |
| Version catalog | `gradle/libs.versions.toml` |

---

## Milestone Summary: "Vibe-Coded" to "Billion-Dollar Architecture"

| # | Milestone | Type | Est. Days | Delivers |
|---|-----------|------|-----------|----------|
| 1.1 | Fix module dependencies | Refactor | 1-2 | Clean dependency graph |
| 1.2 | Split DayDialViewModel into delegates | Refactor | 3-5 | Testable, maintainable state management |
| 1.3 | Decompose DayDialScreen.kt | Refactor | 5-7 | Modular composables with Previews |
| 1.4 | Navigation architecture | Refactor | 3-4 | Type-safe routes, BottomNav foundation |
| 2.1 | Material 3 Expressive Tab Pills | UI | 2-3 | Modern pill-shaped section tabs |
| 2.2 | Edge-to-Edge surfaces | UI | 2 | Immersive full-screen experience |
| 2.3 | Bottom Navigation contextual modes | UI | 3-4 | App-level navigation with badges |
| 2.4 | Command Palette (global) | UI/Feature | 2-3 | Spotlight-style rapid access |
| 2.5 | LiquidGlass consistency pass | UI | 2-3 | Unified visual identity across all screens |
| 3.1 | `:feature:habits` module | Feature | 5-7 | Habit tracking with streaks & AI repair |
| 3.2 | `:feature:medication` module | Feature | 5-7 | Med management with exact alarms |
| 3.3 | `:feature:review` module | Feature | 4-5 | Standalone insights & daily review |
| 3.4 | Chronos Dial ring formalization | Feature | 3-4 | Semantic 3-ring interaction model |
| 4.1 | AI Review Sheet staging area | AI | 4-5 | Per-block accept/reject flow |
| 4.2 | Android 16 Live Updates | Platform | 3-4 | Rich focus session notifications |
| 4.3 | Plan Repair & Deep-Work detection | AI | 3-4 | Intelligent schedule optimization |
| 4.4 | Android 17 compatibility testing | Platform | 2-3 | Forward compatibility certification |

**Total: 18 milestones | ~50-70 developer-days | 4 phases**

---

## Architecture State Transition

```
BEFORE (Original baseline):
  :app -> 3 hardcoded NavHost routes
  :feature:daydial -> 32k-token monolith, 1165-line VM, contains everything
  :feature:focus -> basic timer, no glass UI
  :feature:tasks -> direct :core:data dependency (violation)
  :feature:habits -> missing feature module (data layer only)
  :feature:medication -> missing feature module (data layer only)
  :feature:review -> missing feature module (embedded in DayDial sidebar)

AFTER (Target):
  :app -> ChronosNavGraph (type-safe routes), ChronosBottomBar (contextual modes)
  :feature:daydial -> slim coordinator + 10 focused composables + 5 delegates
  :feature:focus -> full glass UI, Live Updates, proper service lifecycle
  :feature:tasks -> clean domain-only dependency, glass consistency
  :feature:habits -> complete module with streaks, AI repair, dial integration
  :feature:medication -> complete module with alarms, adherence, charts
  :feature:review -> standalone insights, weekly summaries, actionable cards
  :core:ui -> global CommandPalette, enhanced LiquidGlass system
  :core:ai -> real plan repair, deep-work detection, per-block staging
```
