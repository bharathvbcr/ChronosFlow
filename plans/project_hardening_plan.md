# Project Hardening & Domain Refinement Plan

This is a prior planning artifact; treat it as historical execution guidance and use `README.md` + `docs/product-contract.md` for current source-of-truth behavior.

## Overview
Stabilize the project infrastructure and fix the architectural boundary between data and domain. Implement the core "DayPlan" ownership and interaction foundations.

## Scope Definition (CRITICAL)
### In Scope
- Refactor `:core:domain` to contain pure data classes (POJOs) instead of Room entities.
- Add `DayPlanEntity` and `DayPlan` domain model for schedule ownership.
- Implement the Gradle Wrapper for reliable builds.
- Scaffold the Selected-Block Bottom Sheet and Highlight state.
- Implement `DialUtils` unit tests.

### Out of Scope (DO NOT TOUCH)
- Actual AI plan repair logic (detection only).
- Multi-user sync features.

## Current State Analysis
- **Build**: Gradle wrapper scripts are present at the repo root.
- **Architecture**: `core:domain` owns pure domain models. Room entities are contained in `core:data`, and app/feature/domain/UI modules no longer import `com.chronosflow.core.data.model.*`.
- **Dial**: Selected-block state, details/edit sheet routing, drag movement, and resize handling are implemented and covered by focused Day Dial tests.

## Implementation Phases

### Phase 1: Build & Domain Integrity
- **Goal**: Establish a "Source of Truth" in Domain and fix the build tool.
- **Steps**:
  1. [x] Run `gradle wrapper` to generate build scripts.
  2. [x] Create `core/domain/model/Task.kt` and `TimeBlock.kt`.
  3. [x] Create `core/data/mapper/Mappers.kt` for Entity <-> Domain conversion.
  4. [x] Update repositories to return domain models.
- **Verification**: `./gradlew :core:domain:assemble`

### Phase 2: Day Ownership & Persistence
- **Goal**: Add `DayPlan` to coordinate daily schedules.
- **Steps**:
  1. [x] Create `DayPlanEntity` in `core:data`.
  2. [x] Update `ChronosDatabase` and `DayPlanDao`.
  3. [x] Create day-plan assembly/use-case coverage for blocks plus day metadata.
- **Verification**: Database inspection or unit test for DAO.

### Phase 3: Interactive Refinement
- **Goal**: Visual feedback for selection and robust math.
- **Steps**:
  1. [x] Add `selectedBlockId` to `DayDialViewModel`.
  2. [x] Implement highlight drawing logic in `ChronosDial`.
  3. [x] Add dial math/render-model tests with edge cases.
- **Verification**: `./gradlew :feature:daydial:test`

### Phase 4: Selected-Block UI
- **Goal**: Bottom sheet for block editing.
- **Steps**:
  1. [x] Implement selected block edit/details sheet behavior in `:feature:daydial`.
  2. [x] Wire block edit/delete/lock-style actions through ViewModel/delegates.
- **Verification**: Manual interaction in Dial UI.
