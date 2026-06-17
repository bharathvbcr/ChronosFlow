# Navigation architecture (Navigation 3)

ChronosFlow's app navigation runs on **Jetpack Navigation 3** (`androidx.navigation3`) with a
**hybrid model**: cross-section navigation lives in a real Nav3 back stack, while the Day screen's
tabs are in-place UI state. This document explains the design, the rationale, and how to extend it.

> Migrated from Navigation 2 (NavHost/NavController, string routes) on 2026-06-13/14. The old
> string-route + per-route save/restore policy model is gone.

## TL;DR

- Routes are **type-safe `@Serializable` `NavKey`s** (`ChronosRoute`), not strings.
- The back stack only models **cross-section** navigation: **Day is always the root**, and at most
  one sub-section (Tasks / Habits / Goals / Medication) sits on top of it.
- **Day tabs (Plan / Today / Focus / Review) are NOT back-stack entries.** They are in-place tabs
  inside `DayDialScreen`, driven by UI state on `ChronosNavigationState`. The Day `NavEntry` stays a
  single stable entry so its composition and internal `AnimatedContent` animation are preserved.
- There is **one source of truth** for the Day tab: `ChronosNavigationState.requestedDayTarget`.

## Key types

| Type | File | Responsibility |
|------|------|----------------|
| `ChronosRoute` | `navigation/ChronosRoute.kt` | Sealed `NavKey` route hierarchy + shell destination metadata + deep-link mapping |
| `ChronosNavigationState` | `navigation/ChronosNavigationState.kt` | State holder **and** navigator (back stack + Day-tab state + navigate/goBack/selectDayTarget) |
| `ChronosNavDisplay` | `navigation/ChronosNavGraph.kt` | Renders `NavDisplay` with the `entryProvider` + cross-section transitions |
| `ChronosNavigationShell` | `navigation/ChronosNavigationShell.kt` | Compact bottom nav / adaptive rail, quick-add, selection, hosts `ChronosNavDisplay` |

## Routes (`ChronosRoute`)

A sealed interface implementing `androidx.navigation3.runtime.NavKey`. Each destination is a
`@Serializable data class` whose navigation arguments are strongly-typed nullable fields (no more
URL-encoded query strings):

```kotlin
@Serializable data class Day(val target: String? = null, val capture: String? = null) : ChronosRoute
@Serializable data class Tasks(val taskId: String? = null, val target: String? = null, val capture: String? = null) : ChronosRoute
@Serializable data class Habits(val target: String? = null, val capture: String? = null) : ChronosRoute
@Serializable data class Goals(val target: String? = null, val capture: String? = null) : ChronosRoute
@Serializable data class Medication(val target: String? = null, val capture: String? = null) : ChronosRoute
```

Each keeps a companion `const val section` + `createRoute(...)` factory (returns the `NavKey`) so
call sites read naturally: `ChronosRoute.Day.createRoute(ChronosRoute.Day.TARGET_PLAN)`.

`Focus` and `Review` are **namespace objects, not destinations** — they are Day *tabs*
(`Day(TARGET_FOCUS_PLANNER)` / `Day(TARGET_INSIGHTS)`), kept as namespaces only so existing
`ChronosRoute.Focus.*` references resolve.

`@Serializable` requires the `org.jetbrains.kotlin.plugin.serialization` plugin (applied in `:app`).

## State holder + navigator (`ChronosNavigationState`)

Follows the androidx nav3 `TopLevelBackStack` recipe. It owns:

- `sectionStacks: Map<String, SnapshotStateList<NavKey>>` — one stack per top-level section, each
  seeded with its base route. (Plain `mutableStateListOf`, not `rememberNavBackStack` — `NavDisplay`
  accepts a plain `SnapshotStateList`, and this keeps the holder unit-testable. The hybrid stack is
  depth ≤ 2 and deterministically rebuildable.)
- `backStack: SnapshotStateList<NavKey>` — the **flattened** list `NavDisplay` renders. Always
  `[Day()]`, or `[Day(), <subSection>]` when a sub-section is active.
- `topLevelSection`, `requestedDayTarget`, `requestedDayCapture`, `dayTargetGeneration` — UI state.
  `topLevelSection` and `requestedDayTarget` are `rememberSaveable` (survive config change / process
  death); the back stack itself is rebuilt from them.

### Navigation operations

```kotlin
fun navigate(route: ChronosRoute)   // Day target → selectDayTarget; else → switch section (replace head)
fun selectDayTarget(target: String?, capture: String? = null)  // surface a Day tab in-place
fun goBack()                        // sub-section → Day; Day is the exit root
val canGoBack: Boolean              // true when not at the Day root
```

- **Day targets never touch the back stack.** `navigate(Day(target))` sets `topLevelSection = DAY`,
  updates `requestedDayTarget`/`requestedDayCapture`, and bumps `dayTargetGeneration`.
- **`dayTargetGeneration` is bumped on every `selectDayTarget`, even re-selecting the same tab.**
  That's what drives the **Today double-tap "reset to today"** — `DayDialScreen` re-applies the
  launch target when the generation changes.
- **Cross-section** `navigate(Tasks(...))` replaces the section's stack head (so context args apply)
  and brings it to the top. `goBack()` from a sub-section resets that stack and returns to Day,
  restoring whatever Day tab `requestedDayTarget` holds.
- **`sectionTargetGeneration` is bumped on every cross-section `navigate`** — the sub-section analogue
  of `dayTargetGeneration`. A sub-section's transient target/capture (`add`/`context`) rides in its
  `NavKey`, so re-navigating to the *same* target yields a value-equal key that `NavDisplay` treats as
  no change. The generation lets a destination re-fire its one-shot even when the key is unchanged. It
  is plumbed to screens as `navTargetGeneration`; consume it via `OneShotNavTrigger` (core:ui) for an
  immediate one-shot, or fold it into the one-shot's `rememberSaveable` + `LaunchedEffect` keys.

### Why a single source of truth

Nav2's `NavController.navigate` was asynchronous (navigate → back-stack settles → recompose), so the
old shell needed an **optimistic** `localDayTarget` state machine to update the UI immediately. Nav3's
`selectDayTarget` is **synchronous**, so that machine was redundant and was removed. Having two
holders (`localDayTarget` + `requestedDayTarget`) had caused a real bug: after a cross-section
round-trip the selection desynced and the Today tab became a no-op. Now `renderedDayTarget =
currentDayTarget = navState.requestedDayTarget` — one source, no desync.

## Rendering (`ChronosNavDisplay` / entry provider)

`ChronosNavDisplay` renders:

```kotlin
NavDisplay(
    backStack = navState.backStack,
    onBack = { navState.goBack() },
    transitionSpec = { ... materialSharedAxis Forward ... },        // cross-section motion
    popTransitionSpec / predictivePopTransitionSpec = { ... Backward ... },
    entryProvider = entryProvider<NavKey> {
        entry<ChronosRoute.Day> { DayDialScreen(launchTarget = shellDayTarget, ...) }
        entry<ChronosRoute.Tasks> { key -> TaskScreen(initialContextTaskId = key.taskId, ...) }
        entry<ChronosRoute.Habits> { key -> ... }
        entry<ChronosRoute.Goals> { key -> ... }
        entry<ChronosRoute.Medication> { key -> SensitiveRouteGate { MedicationScreen(...) } }
    }
)
```

- **Transitions:** cross-section pushes/pops use the design-system shared-axis motion
  (`ChronosTransitionFactory.materialSharedAxis`, reduced-motion aware). Day-tab switches don't change
  the back stack, so `DayDialScreen`'s own `AnimatedContent` owns that motion.
- **Sensitive areas:** the Medication entry wraps content in `SensitiveRouteGate`, which owns the
  unlock UI via `LocalContext` — navigation into it is always safe (no pre-auth no-op).
- **Feature flags:** disabled features render a `ParkedFeatureDestination` placeholder.

## Deep links (notifications, widgets, Wear, AppFunctions)

> The Nav3 migration guide lists deep links as "unsupported by the guided path"; ChronosFlow wires
> them manually.

All external entry points funnel through a single intent extra and mapping:

1. Source builds an `Intent` with `EXTRA_INITIAL_SECTION` (+ optional `EXTRA_DAY_TARGET`).
   - Widgets: `widget/WidgetDeepLinks.kt` (`openSectionAction`).
   - Notifications: `core/notifications/NotificationLaunchIntent.kt`.
   - Wear / AppFunctions: same intent shape.
2. `MainActivity` parses it (`parseNotificationLaunch`) into a `NotificationLaunch`.
3. `ChronosRoute.routeForNotificationLaunch(launch, flags)` maps section/target → a type-safe
   `ChronosRoute` (feature-flag aware).
4. `navState.navigateFromNotificationLaunch(...)` navigates (Day targets go in-place; sections switch).

Day-target launches also seed the initial tab via `rememberChronosNavigationState(startDayTarget=...)`.

## How to add a destination

1. Add a `@Serializable data class X(...) : ChronosRoute` with a `companion { const val section; createRoute(...) }`.
2. If it's a top-level section: add it to `topLevelRoutes` / `topLevelSections`, add a `ShellDestination`,
   and add a `sectionStacks` entry in `newSectionStacks()`.
3. Add an `entry<ChronosRoute.X> { key -> XScreen(...) }` in `ChronosNavDisplay`.
4. If reachable from a notification/widget, extend `routeForNotificationLaunch`.
5. Add a case to `ChronosNavigationStateTest` and (if it has a shell tab) `shellDestinationFor`.

If it's a **Day tab** instead of a section, add a `Day.TARGET_*` constant, map it in
`dayDialTabForShellTarget` / `dayTargetForDayDialTab`, and surface it via `selectDayTarget` — do **not**
give it its own back-stack entry.

## Testing

`ChronosNavigationStateTest` (plain JVM, via `createChronosNavigationStateForTest(...)`) locks in the
back-stack and Day-tab behavior, including the regression that back from a sub-section restores the
prior Day tab. Route/shell-mapping logic is covered by `ChronosRouteShellDestinationTest`,
`ChronosRouteNotificationTest`, and `NotificationNavigationSpecTest`.

## Gotchas

- `rememberNavBackStack` returns `NavBackStack<NavKey>` (no type arg); `entry` is an `entryProvider`
  DSL scope member (no separate import); `NavDisplay` takes `backStack` + `entryProvider` (simpler than
  the migration guide's `entries`/`toEntries` helper).
- The Day `NavKey` in the back stack is always argument-free `Day()` — the tab/capture travel as state
  so the entry stays stable. Don't put the Day target into the back-stack key.
- Sub-section one-shot targets (`Tasks(target="add")`, `Tasks(taskId, target="context")`, …) DO live in
  the back-stack key, so they silently stop firing on repeat unless they also key on
  `navTargetGeneration`. Open such sheets via `OneShotNavTrigger`, or — for ones that must wait on async
  state (e.g. the context sheet waiting for its task to load) — include `navTargetGeneration` in both the
  consume flag and the effect keys while keeping the async input out of the consume flag. Regression
  coverage: `OneShotNavTriggerTest` (core:ui) + `ChronosNavigationStateTest` (app).
- **A predictive-back swipe composes the INCOMING entry during the gesture.** Backing out of a
  sub-section composes `DayDialScreen` for the preview *while `topLevelSection` is still the
  sub-section* — so any in-place `BackHandler`/`PredictiveBackHandler`/`ModalBottomSheet` inside Day
  becomes live mid-gesture and can swallow the back (the pop to home never commits) or revive a stale
  sheet. Guard every in-place back/modal handler in `DayDialScreen` with
  `isActiveSection = navState.topLevelSection == ChronosRoute.Day.section` (passed from
  `ChronosNavGraph`): the modal sheet host's inputs are nulled and the tab-fallback handler disabled
  when Day isn't the active section, and Day drops its transient sheet/selection when it goes inactive
  so the back lands on a clean dial. The same rule applies to any future destination that hosts its own
  back-consuming surface.
- Build on Windows: a `FileSystemException ... classes.jar ... used by another process` is an
  environment lock — `gradlew --stop` then rebuild. (Don't run `--stop` while another build is active.)
