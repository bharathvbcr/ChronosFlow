# ChronosFlow Performance & Cloud Sync Implementation Plan

This is a historical implementation checklist. Use `README.md` and `docs/product-contract.md` for current behavior and scope decisions.

## Overview
Optimize ChronosFlow for production-grade performance and implement the foundational cloud synchronization layer using Firebase.

## Scope Definition (CRITICAL)
### In Scope
- Setup Baseline Profile generator module.
- Implement Macrobenchmark for the Chronos Dial scroll and interaction.
- Initialize Firebase Data Connect / Firestore for task sync.
- Implement a `SyncRepository` that coordinates local Room data with Cloud storage.

### Out of Scope (DO NOT TOUCH)
- Real-time collaborative editing (multi-user).
- Advanced AI inference (beyond skeletons).

## Implementation Phases

### Phase 1: Performance & Benchmarking (Phase 5)
- **Goal**: Ensure the Dial UI runs at 120fps and optimizes launch time.
- **Steps**:
  1. [x] Create `:benchmark` module with `MacrobenchmarkRule`.
  2. [x] Implement `BaselineProfileGenerator` scaffold for app launch.
  3. [x] Expand baseline profile capture to command palette, Plan, block creation, edit-block save, and focus start.
  4. [x] Add benchmark build type to `app/build.gradle.kts`.
  5. [x] Add `scripts/physical-benchmark.ps1` so production performance verification refuses emulator measurements and records physical-device output locations.
  6. [x] Expand macrobenchmark coverage beyond launch to exercise Plan, Focus, Today return, command-palette open/close, edit-block save, scroll, and Chronos Dial drag interactions.
  7. [x] Add `scripts/android-memory-budget-smoke.ps1` to install the debug app, drive Today/Chronos Dial, Calendars, and Focus Planner, then fail if total PSS exceeds the configured cap.
- **Verification**: Run `.\scripts\physical-benchmark.ps1` with a physical Android device attached for production timing. Run `.\scripts\android-memory-budget-smoke.ps1` on a device/emulator for memory-budget smoke. Source and flow verification passed with `:benchmark:compileBenchmarkKotlin`, `:benchmark:assembleBenchmark`, `BaselineProfileGenerator` on the API 37 emulator, and the new drag/scroll/edit macrobenchmark method on the emulator with `androidx.benchmark.suppressErrors=EMULATOR`; emulator timing remains non-representative.

### Phase 2: Cloud Sync Foundation (Phase 6)
- **Goal**: Enable cross-device task synchronization.
- **Steps**:
  1. [x] Use cloud-only Firebase/Firestore sync; do not declare `ACCESS_LOCAL_NETWORK` for the current scope.
  2. [x] Initialize the Firebase Firestore SDK path in `:core:data` with a config-tolerant remote gateway.
  3. [x] Create `RemoteTaskEntity` and `RemoteTimeBlockEntity` payloads for Firestore.
  4. [x] Implement `SyncWorker` and `SyncWorkScheduler` using `WorkManager` to push local task/time-block snapshots to the cloud, and enqueue that work after local task/time-block mutations.
- **Verification**: Local sync payload, disabled-state, and mutation-enqueue tests pass; live Firebase Console verification still requires a real Firebase project config (`google-services.json`) and credentials.
