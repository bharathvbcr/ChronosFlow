# ChronosFlow Implementation Plan

This is a historical implementation plan and checkpoint list. Use `README.md` and `docs/product-contract.md` as the canonical current progress contract.

## Overview
ChronosFlow is an Android 16 production-ready, Android 17 compatibility-tested, Material 3 Expressive-ready productivity suite. The product identity is a "time operating system" centered on the Chronos Dial: a 24-hour radial planner that makes the shape of the day visible at a glance.

## Baseline
- Compose uses the BOM through `androidx.compose:compose-bom:2026.04.01`.
- The production target is Android 16 (`targetSdk = 36/37` depending on current branch state) while compiling and compatibility-testing against Android 17 APIs.
- The app must stay adaptive across phone, tablet, foldable, desktop-window, ChromeOS, and bubble-sized containers.
- Exact alarms are reserved for user-specified precise reminders. Soft planner nudges should use inexact scheduling.
- Live Updates are limited to active, user-initiated focus/session journeys and use standard notification styles.

## Phase 1: Foundation & Data Layer
- [x] Initialize root Gradle settings and version catalog.
- [x] Configure app, data, AI, notification, feature, and benchmark modules.
- [x] Implement `TaskEntity` and first-pass `TimeBlockEntity`.
- [x] Set up Hilt dependency injection.
- [x] Implement Room database and base repositories.
- [x] Split pure domain models out of the Room-owned data module.
- [x] Add `DayPlanEntity` as a dedicated owner for per-date planner state.

## Phase 2: Chronos Dial Prototype
- [x] Implement `ChronosDial` Canvas drawing logic.
- [x] Add tap and drag-to-move handling.
- [x] Implement minute/angle conversion utilities.
- [x] Add drag edge resize handling.
- [x] Add multi-ring rendering for calendar, planned blocks, tasks, habits, and medication.
- [x] Add selected-block details sheet.

## Phase 3: Core Features
- [x] Task CRUD scaffold.
- [x] Exact-alarm scheduler scaffold for high-priority reminders.
- [x] Focus timer scaffold.
- [x] Add exact-alarm permission flow and fallback behavior when `SCHEDULE_EXACT_ALARM` is revoked.
- [x] Generate bounded future recurrence instances instead of repeating exact alarms.
- [x] Wire medication, habit, calendar, and task sources into `TimeBlockEntity`.

## Phase 4: Android 16 Production Readiness
- [x] Edge-to-edge entry point.
- [x] Adaptive layout scaffold.
- [x] Promoted notification manifest permission for eligible Live Updates.
- [x] Implement Android 16 progress-centric notification style for active focus sessions.
- [x] Request notification permission at runtime on supported API levels.
- [x] Audit large-screen quality and ignored orientation/resizability assumptions.

## Phase 5: Android 17 Compatibility, AI, and Optimization
- [x] Android 17 compatibility plan captured.
- [x] Gemini Nano/AICore planning scaffold.
- [x] Baseline profile and macrobenchmark module scaffold.
- [x] Run Android 17 emulator/device compatibility lane.
- [x] Add MessageQueue-safe test dependency review.
- [x] Add memory-budget checks for Today, Calendar, and Chronos Dial.
- [x] Add Baseline Profile coverage for launch, dial, edit block, and focus start.
- [x] Add Macrobenchmark coverage for drag, scroll, and edit flows.

