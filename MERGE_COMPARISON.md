# Merge Comparison — recovering the lost floating-sidebar line

Use this as the decision sheet when you do the final merge. It compares the two
divergent lines file-by-file so you can keep the better version of each change.

## The three reference points

| Ref | Git | What it is |
|-----|-----|-----------|
| **BASE** | `15f1934` | Common ancestor (Modal pass 4) |
| **CURRENT** | branch `recover-merge-floating-sidebar` (`d5bdb39`, tag `pre-merge-restore`) | This session's work: **v17 DB schema** + focus/daydial refactor + the `de5a8da` line (goals / journal / sleep, widget, appfunctions) |
| **RECOVERED** | branch `recovered-lost-work` (`47c609f`) | The **lost** branch: modern **floating sidebar**, **full-page backdrop** (`ChronosBackdrop` + themes), **focus-page** improvements, widget hub, GenAI, onboarding, auto-backup, review-unification |

Both branches are saved permanently (no longer dangling). `pre-merge-restore`
is the clean pre-merge restore point. A trial merge produced **48 conflicts**.

## Bottom line

- The two lines are **mostly complementary**, so ~75% of conflicts are **MERGE BOTH** (union), not a choice.
- RECOVERED owns the **UI shell** (floating sidebar, backdrop, full-bleed scroll under the glass top bar, page-header chrome). CURRENT owns **new features** (Goals/Journal/Sleep, split-session focus engine) and the **DB expansion tables**.
- "Just keep CURRENT" silently drops the floating sidebar, backdrop, Focus widget, Wear, auto-backup, and review-unification. "Just keep RECOVERED" drops Goals/Journal/Sleep, reflow/applyRoutine, and the DB expansion tables. Hence **merge both** almost everywhere.

## ⚠️ Five cross-cutting issues to settle FIRST (they span multiple files)

1. **DB is v17 on BOTH sides with *different* migrations.** CURRENT v17 adds 5 tables (`goals`, `journal_entries`, `sleep_tracks`, `routines`, `routine_steps`); RECOVERED v17 recreates `calendar_events` with composite PK `[id, startAt]`. They are not supersets → **final DB should be v18 chaining both migrations**, then **regenerate the schema JSON**. (This is the same class of mismatch that caused the launch crash earlier — get it right or it crashes again.)
2. **Duplicate focus-AI feature, different field names.** Both add a focus guidance card + next-block suggestion, but CURRENT calls them `focusNextBlockSuggestion`/`onAdvancePhase` and RECOVERED `nextFocusSuggestion`/`onRequestNextFocusSuggestion`. **Collapse to one API** or you get duplicate cards. Touches DayDialScreenState, DayDialViewModel, FocusTab.
3. **Review module deleted in RECOVERED** (folded into daydial/Insights via `core/ai/ReviewAssistPlanner` + `DayDialReviewDelegate`). No capability is lost — **accept the deletion** — but verify the widget's daily coach line still gets written (`putDailyCoachLine`) from the daydial path.
4. **Dueling assist-refresh mechanism.** CURRENT `ProactiveAssistCache` vs RECOVERED `ProactiveAssistForegroundRefresher` + `WidgetBackgroundSync`. Conscious choice — **favor RECOVERED's**, drop CURRENT's collector unless a consumer survives.
5. **Support files must travel for it to compile.** From RECOVERED: `InsightsPeriod`, `AppEventLog`, `CurrentBlockNotificationCoordinator`, `ReviewAssistPlanner`, gap-fill planner, `DayDialNavigation.kt` (the actual `DayDialSidebar`: `ModalDrawerSheet` → floating glass `Surface`). From CURRENT: journal/trends delegates, routine use-cases, `feature:goals` module, `SidebarPage.GOALS`.

## Per-file decision table (48 conflicts)

Legend: **MB** = merge both · **KR** = keep recovered · **KC** = keep current

### Day UI — floating sidebar / backdrop / tabs  → detail: `tmp/merge-comparison/group1-day-ui.md`
| File | Decision | How |
|------|----------|-----|
| DayDialMainContent.kt | MB | RECOVERED scaffold/backdrop/AI-strip routing as base; re-add CURRENT v17 params, reconcile the 2 focus-suggestion APIs |
| DayDialScreenChrome.kt | **KR** | This is the floating sidebar; re-apply CURRENT's 4 vmState fields + Goals/Journal/Sleep flag setters |
| ui/SidebarPageContent.kt | MB (RECOVERED-dominant) | Its hub-page deletion + backdrop swatches/MINIMAL theme; graft CURRENT dev-flags + routine apply-today/tomorrow |
| ui/FocusTab.kt | MB (CURRENT-dominant) | Keep CURRENT split-session engine; port RECOVERED `contentTopPadding` + auto-refresh effects |
| ui/SheetContent.kt | MB (RECOVERED-dominant) | RECOVERED Logs sheet + restructure; graft CURRENT Journal/SleepLog arms |
| ui/TodayTab.kt | MB (RECOVERED-dominant) | RECOVERED dial zoom/legend/night shading; insert CURRENT sleep-prompt card |
| ui/InsightsTab.kt | MB | RECOVERED period selector + CURRENT trends/journal/sleep; combine params |
| ui/PlanTab.kt | **KC** | Only graft RECOVERED's 5-line top-padding/label patch |

### Day logic / state / VM  → detail: `tmp/merge-comparison/group2-day-logic.md`
| File | Decision | How |
|------|----------|-----|
| DayDialScreenState.kt | MB | Keep CURRENT journal/sleep/goals flags + RECOVERED appEventLog/default flips; pick ONE focus-suggestion field |
| DayDialViewModel.kt | MB (the big one) | Merge ctor, dedupe the 2 focus planners, keep CURRENT routines/journal/sleep/trends/split-session + RECOVERED calendar-sync/app-log/current-block/period-insights/gap-fill |
| DayDialScreen.kt | MB (RECOVERED priority) | RECOVERED padding rework + compactMode removal + focus-page params; re-apply CURRENT onOpenGoals/routine/journal/sleep |
| DayDialCommandProvider.kt | MB | RECOVERED review-unification (drop daydial.insights); keep CURRENT journal/sleep + templates→routines |
| delegate/DayDialReviewDelegate.kt | MB | Additive both sides (CURRENT trend context + RECOVERED reviewAssistPlanner/digest/period-insights) |
| model/InsightsTabUiState.kt | MB | Union all appended fields |
| DayDialCommandProviderTest.kt | MB | Match merged provider; recompute literal count |
| DayDialViewModelTest.kt | MB | Reconstruct merged ctor; keep RECOVERED new tests |
| model/SidebarPageFeatureFlagsTest.kt | **KC** | CURRENT is the superset (REVIEW graduated + GOALS) |

### App shell / navigation / widget / appfunctions  → detail: `tmp/merge-comparison/group3-app-shell.md`
| File | Decision | How |
|------|----------|-----|
| navigation/ChronosNavigationShell.kt | MB | CURRENT nav-pill + quick-adds vs RECOVERED today-reset/badge; collide only in `ChronosCompactFloatingBottomBar`. (Sidebar UI is NOT here.) |
| navigation/ChronosRoute.kt | MB | RECOVERED review→Insights base + layer CURRENT's Goals routes |
| ChronosApplication.kt | MB (conscious) | Favor RECOVERED assist-refresh/auto-backup; drop CURRENT collector unless a consumer survives |
| appfunctions/ChronosAppFunctions.kt | MB | Union new functions; reconcile shared `@Inject` ctor + dup import |
| widget/ChronosGlanceWidget.kt | **KR** | Full rewrite to Focus widget; CURRENT coach-line subsumed |
| widget/WidgetActionEntryPoint.kt | **KR** | Must match kept widget/Wear; drop CURRENT proactiveAssistCache() |
| AndroidManifest.xml | MB | Pure union (CURRENT 2 receivers + RECOVERED widget hub/Wear/deep-link) |
| res/xml/chronos_widget_info.xml | MB | RECOVERED desc + CURRENT preview/targetCell attrs |
| ChronosRouteShellDestinationTest.kt | MB | Derive from merged route file |
| appfunctions/ChronosAppFunctionsTest.kt | MB | Union mocks + ctor args + both test sets |
| FeatureCommandProviderTest.kt | MB | RECOVERED base + CURRENT journal/sleep ids; recompute priority list |

### Core — DB / settings / planner / notifications  → detail: `tmp/merge-comparison/group4-core.md`
| File | Decision | How |
|------|----------|-----|
| core/data/ChronosDatabase.kt | MB ⚠️ | Base CURRENT v17 (5 tables); fold RECOVERED calendar composite-PK as **MIGRATION_17_18 → version 18** |
| schemas/.../17.json | **REGENERATE** | Don't hand-merge; rebuild after the .kt is final (CURRENT hash `77cc3b3…`, RECOVERED `0044aca…`) |
| ChronosDatabaseMigrationTest.kt | MB | Keep CURRENT migrate7To17 rename + both 16→17 tests |
| repository/CalendarEventRepositoryImpl.kt | MB | RECOVERED full rewrite (day-window, mirror cleanup, instance-unique ids) + CURRENT `energyLevel = classifyImportedEventEnergy(...)` line |
| core/domain/planner/PlannerService.kt | MB | CURRENT rebalanceDay anchoring + RECOVERED rebalance reporting helpers |
| core/ui/settings/ChronosUiSettings.kt | MB ⚠️ | Keep ALL keys both sides + RECOVERED enum **`ChronosBackdropTheme.MINIMAL`**; reconcile the 2nd graduation-marker name (COMPANION vs ASSISTANTS) |
| core/ui/settings/ChronosUiSettingsTest.kt | MB | Keep all 4 added tests; align marker/key names |
| core/notifications/res/values/colors.xml | MB | Union (notification_accent[_critical] + chronosflow_brand_accent) |

### Other features + docs/config  → detail: `tmp/merge-comparison/group5-features-docs.md`
| File | Decision | How |
|------|----------|-----|
| feature/focus/FocusService.kt | MB | CURRENT split-session `when` + RECOVERED Wear bridge + bg-crash try/catch; inject `wearFocusBridge.clear()` in terminal branches |
| feature/habits/HabitScreen.kt | MB (RECOVERED base) | RECOVERED ChronosPageHeader chrome + re-apply CURRENT animation wrappers |
| feature/medication/MedicationScreen.kt | MB (RECOVERED base) | Same; also preserve CURRENT `MedicationAdherencePanel` |
| feature/medication/MedicationViewModel.kt | MB | Union: CURRENT adherence + RECOVERED GenAI notes-rewrite/proofread |
| feature/tasks/TaskScreen.kt | MB (RECOVERED base) | RECOVERED chrome + CURRENT animation polish |
| feature/review/build.gradle.kts | **KR** | Accept module deletion |
| feature/review/ReviewScreen.kt | **KR** | Accept deletion (folded into daydial) |
| feature/review/ReviewViewModel.kt | **KR** | Accept deletion; verify `putDailyCoachLine` ported to DayDialReviewDelegate |
| AGENTS.md | KR | Generated index line; regenerate after merge |
| CLAUDE.md | KR | Generated index line; regenerate after merge |
| README.md | MB | RECOVERED product framing + CURRENT ML-Kit/AICore bullets; fix stale TODO list |
| app/res/values/strings.xml | MB | Superset, no key collision |
| settings.gradle.kts | MB | Keep CURRENT `include(":feature:goals")` + drop RECOVERED `include(":feature:review")` |

## Recommended merge order

1. Settle the **5 cross-cutting issues** above (esp. DB → v18, and the one focus-AI API).
2. Resolve **Core (Group 4)** first — DB + settings underpin everything else.
3. Then **app shell (Group 3)** routes/manifest, then **Day logic (Group 2)**, then **Day UI (Group 1)** which depends on both.
4. **Other features (Group 5)** last; accept the review-module deletion.
5. Pull in the **support files** (issue 5) so it compiles, regenerate the DB schema JSON and the GitNexus index, then build + install to phone/watch.

> Restore points if anything goes sideways: `git reset --hard pre-merge-restore` (current work) · `git checkout recovered-lost-work` (the lost line) · `main` is untouched at `bd09aae`.
