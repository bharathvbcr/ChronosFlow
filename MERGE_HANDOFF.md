# Handoff — finish merging the recovered floating-sidebar line

**Audience:** a fresh agent (Claude Fable) with no prior conversation context.
**Goal:** integrate a recovered, previously-lost branch of UI work into the current
work-in-progress branch, keeping the better version of each conflicting change, then
build and deploy to the user's phone + watch.

Read this file first, then `MERGE_COMPARISON.md` (the per-file decision sheet), then the
detailed group files under `tmp/merge-comparison/`.

---

## 1. What happened (why this merge exists)

The user has been building **ChronosFlow** (an Android multi-module app) largely through
agent sessions, and a chunk of work was **never committed and got lost** during branch
churn:

- A previous session implemented a **modern floating-window sidebar**, a **full-page
  backdrop** (`ChronosBackdrop` + themes), and **focus-page improvements**, and committed
  them on a feature branch (`agent-app-control-wear-live-updates`), tip = **`47c609f`**.
- The user later "merged and deleted" that branch — but the merge commit `bd09aae`
  actually merged a *different* line (`de5a8da`, a fetched `origin/main`), **not** `47c609f`.
  So `47c609f` was orphaned (dangling) and its UI work vanished from the tree.
- Meanwhile, in the current session, the user redid other work on top of `main`: a **Room
  DB v17 schema** (`MIGRATION_16_17`, exported `17.json`, a destructive-downgrade guard in
  `DataModule.kt`) plus a focus/daydial refactor. (Context: installing an app whose DB
  schema was older/different than the on-device DB caused launch crashes — Room
  downgrade + identity-hash mismatch. The on-device data was wiped with `pm clear` to
  recover. **Do not regress the DB work or you risk re-introducing that crash.**)

The lost commit was recovered and **secured as branches** so git can't garbage-collect it.
Now the two lines must be merged, keeping the best of each.

## 2. Git state — refs you will use

| Name | Git | Meaning |
|------|-----|---------|
| **BASE** | `15f1934` | Common ancestor of both lines |
| **CURRENT** (work here) | branch **`recover-merge-floating-sidebar`** = `d5bdb39` | This session's work committed as one WIP snapshot: v17 DB + focus/daydial refactor + the `de5a8da` line (Goals/Journal/Sleep, widget, appfunctions) |
| **RECOVERED** (merge in) | branch **`recovered-lost-work`** = `47c609f` | The lost line: floating sidebar, full-page backdrop, focus-page UI, widget hub, GenAI, onboarding, auto-backup, review-unification |
| restore point | tag **`pre-merge-restore`** = `d5bdb39` | Clean pre-merge state of CURRENT |
| fallback | branch **`recovered-prev`** = `ab381ae` | Commit before `47c609f` on the lost branch |
| untouched | branch **`main`** = `bd09aae` | Do **not** commit here until the merge is verified |

You should be **on `recover-merge-floating-sidebar`** with a **clean tree**. Verify:
```
git -C "C:\Users\bhara\Downloads\Code\ChronosFlow" status -sb
git -C "C:\Users\bhara\Downloads\Code\ChronosFlow" branch --contains 47c609f   # must list recovered-lost-work
```
A trial `git merge --no-commit --no-ff recovered-lost-work` previously produced **48
conflicts** and was **aborted** (nothing is half-merged). Conflict list snapshot:
`tmp/conflicts.txt`.

## 3. What's needed and why — the decisions

Full per-file table is in **`MERGE_COMPARISON.md`**. Summary of the reasoning:

- The two lines are **complementary, not competing**, so **~75% of conflicts are "merge
  both" (union)**. RECOVERED owns the **UI shell** (floating sidebar, backdrop, full-bleed
  scroll under the glass top bar, page-header chrome); CURRENT owns **new features**
  (Goals/Journal/Sleep, split-session focus engine) and **DB expansion tables**.
- **"Just keep one side" is wrong both ways.** Keeping only CURRENT drops the floating
  sidebar, backdrop, Focus widget, Wear, auto-backup, review-unification. Keeping only
  RECOVERED drops Goals/Journal/Sleep, reflow/applyRoutine, and the DB expansion tables.

### ⚠️ Five cross-cutting issues — resolve these FIRST (they span many files)

1. **DB is v17 on BOTH sides with *different* migrations.**
   - CURRENT v17 = adds 5 tables (`goals`, `journal_entries`, `sleep_tracks`, `routines`,
     `routine_steps`) + link columns; `calendar_events` PK stays `[id]`.
   - RECOVERED v17 = recreates `calendar_events` with composite PK `[id, startAt]`; no new tables.
   - Neither is a superset. **Resolution: keep CURRENT's v17, add RECOVERED's calendar
     recreate as a new `MIGRATION_17_18` and bump `@Database(version = 18)`. Then
     REGENERATE the schema JSON** (build with Room `exportSchema`; do not hand-edit
     `17.json`/`18.json`). Getting this wrong reproduces the earlier launch crash.
   - Files: `core/data/.../ChronosDatabase.kt`, `schemas/.../17.json` (+ new `18.json`),
     `ChronosDatabaseMigrationTest.kt`, `DataModule.kt` (keep the migration list + the
     `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` guard).
2. **Duplicate focus-AI feature with different field names.** Both add a focus guidance
   card + next-block suggestion: CURRENT `focusNextBlockSuggestion`/`onAdvancePhase`;
   RECOVERED `nextFocusSuggestion`/`onRequestNextFocusSuggestion`. **Collapse to ONE API**
   or the UI renders two cards. Touches `DayDialScreenState.kt`, `DayDialViewModel.kt`,
   `ui/FocusTab.kt`.
3. **Review module deleted in RECOVERED.** RECOVERED removed `feature/review` and folded
   Review into the daydial/Insights tab (via `core/ai/ReviewAssistPlanner` +
   `DayDialReviewDelegate`). No capability is lost — **accept the deletion** — but verify
   the home-widget daily coach line is still written (`ProactiveAssistCache.putDailyCoachLine`
   equivalent) from the daydial path; if not, port that one call into `DayDialReviewDelegate`.
4. **Two assist-refresh mechanisms.** CURRENT `ProactiveAssistCache` vs RECOVERED
   `ProactiveAssistForegroundRefresher` + `WidgetBackgroundSync`. Pick one — **favor
   RECOVERED's** — and drop the other unless a live consumer survives. Files:
   `ChronosApplication.kt`, `widget/WidgetActionEntryPoint.kt`.
5. **Support files must travel or it won't compile.** Pull in, from RECOVERED:
   `InsightsPeriod`, `AppEventLog`, `CurrentBlockNotificationCoordinator`,
   `ReviewAssistPlanner`, gap-fill planner, and **`feature/daydial/.../ui/DayDialNavigation.kt`**
   (this is the actual `DayDialSidebar`, changed from `ModalDrawerSheet` to a floating glass
   `Surface` — it is the heart of the floating sidebar and is NOT in the conflict list because
   it auto-merges; make sure RECOVERED's version wins). From CURRENT: journal/trends
   delegates, routine use-cases, the **`feature:goals`** module, `SidebarPage.GOALS`.

## 4. Recommended execution order

1. Settle the 5 cross-cutting issues above (decide the DB→v18 plan and the single focus-AI API).
2. Start the merge: `git merge --no-commit --no-ff recovered-lost-work`.
3. Resolve conflicts **in dependency order**, using `MERGE_COMPARISON.md` per file:
   **Group 4 Core (DB/settings/planner)** → **Group 3 app shell/routes/manifest** →
   **Group 2 Day logic/VM** → **Group 1 Day UI** → **Group 5 other features + docs**.
   - For each conflicted file, the comparison gives `MERGE BOTH` / `KEEP RECOVERED` /
     `KEEP CURRENT` and a concrete "how". For whole-file keeps:
     `git checkout --theirs <file>` (theirs = RECOVERED) or `git checkout --ours <file>`
     (ours = CURRENT), then `git add <file>`.
   - For the `feature/review` modify/delete conflict: `git rm feature/review/...ReviewViewModel.kt`
     (accept deletion).
4. Verify the support files (issue 5) are present; add the `feature:goals` include and drop
   the `feature:review` include in `settings.gradle.kts`.
5. Compile-fix loop until it builds (expect to reconcile the focus-AI API and DB).
6. Regenerate the Room schema JSON (it's produced by the build) and the GitNexus index
   (`npx gitnexus analyze`), and refresh the generated index line in `AGENTS.md`/`CLAUDE.md`.
7. Build + install + verify on devices (section 6).
8. Only after it runs clean: fast-forward `main` if the user approves
   (`git checkout main && git merge --ff-only recover-merge-floating-sidebar`).

## 5. Project rules you MUST follow (from `CLAUDE.md`)

This repo is indexed by **GitNexus** (MCP tools `gitnexus_*`). Per `CLAUDE.md`:
- **Run `gitnexus_impact({target, direction:"upstream"})` before modifying any
  function/class/method**, and report blast radius; warn on HIGH/CRITICAL.
- **Run `gitnexus_detect_changes()` before committing.** (For this large merge it returns a
  huge "critical-by-volume" result; that's expected — note it and proceed.)
- **Never rename via find-and-replace** — use `gitnexus_rename`.
- Prefer `gitnexus_query` / `gitnexus_context` over grep for understanding flows.
- Keep changes surgical; match local style.

## 6. Environment + build/install/verify (Windows)

**Gotchas (these are environmental, not code bugs):**
- `adb` is **not on PATH**. Full path:
  `C:\Users\bhara\AppData\Local\Android\Sdk\platform-tools\adb.exe`
- Plain `./gradlew` fails with "JAVA_HOME is not set". Use the wrapper
  **`scripts/gradlew-jbr.ps1`**, or set `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.
- Stale Kotlin/KSP incremental state can fail a build that "should" pass — `--rerun-tasks`
  on the module, or `scripts/gradlew-jbr.ps1 --stop`, then retry. (See memory
  `chronosflow-windows-build-gotchas`.)

**Build (debug APK):**
```
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew :app:assembleDebug         # output: app/build/outputs/apk/debug/app-debug.apk
```

**Devices:** Pixel 10 Pro XL (phone) + Pixel Watch 4. `:wear` is a `com.android.library`
(no standalone watch app) — the watch runs the **same `com.chronosflow` phone APK**. Install
the one APK to BOTH. **Transport-ids change between adb sessions — re-resolve every time:**
```
$adb = "C:\Users\bhara\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l        # note transport_id for Pixel_10_Pro_XL (phone) and Pixel_Watch_4 (watch)
& $adb -t <phoneId> install -r -d app\build\outputs\apk\debug\app-debug.apk
& $adb -t <watchId> install -r -d app\build\outputs\apk\debug\app-debug.apk
```

**Verify (no crash on launch):**
```
& $adb -t <phoneId> logcat -c
& $adb -t <phoneId> shell monkey -p com.chronosflow -c android.intent.category.LAUNCHER 1
# wait ~6s, then:
& $adb -t <phoneId> logcat -d -b crash         # must be EMPTY
& $adb -t <phoneId> shell pidof com.chronosflow # must print a PID (process alive)
```
Repeat for the watch. **Specifically confirm:** the floating sidebar + full-page backdrop
render, and there is **no** Room `IllegalStateException` (migration/identity-hash) in logcat.

## 7. Definition of done

- [ ] `recovered-lost-work` merged into `recover-merge-floating-sidebar`, all 48 conflicts
      resolved per `MERGE_COMPARISON.md`.
- [ ] DB ends at **v18** chaining CURRENT's expansion-tables + RECOVERED's calendar
      composite-key migrations; schema JSON regenerated; downgrade guard retained.
- [ ] Single focus-AI suggestion API (no duplicate card); `feature/review` deletion accepted
      with the widget coach-line preserved.
- [ ] `:app:assembleDebug` succeeds.
- [ ] Installed to phone + watch; both launch with **no crash**; floating sidebar + backdrop
      + focus-page improvements visible.
- [ ] GitNexus index + generated `AGENTS.md`/`CLAUDE.md` lines refreshed.

## 8. Safety / restore points

- Undo all merge work: `git reset --hard pre-merge-restore` (back to clean CURRENT).
- The lost line is always at `git checkout recovered-lost-work` (`47c609f`) and
  `recovered-prev` (`ab381ae`).
- `main` (`bd09aae`) is untouched — do not push/commit to it until verified and the user approves.
- Abort an in-progress merge anytime: `git merge --abort`.

---
*Companion files: `MERGE_COMPARISON.md` (per-file decisions) and
`tmp/merge-comparison/group1..5*.md` (detailed analysis per area).*
