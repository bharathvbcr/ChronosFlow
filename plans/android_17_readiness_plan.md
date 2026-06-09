# ChronosFlow Android 17 Readiness Implementation Plan

This plan is a prior implementation plan; use `README.md` and `docs/product-contract.md` as the current behavior source.

## Overview
Keep ChronosFlow Android 16 production-ready while running an Android 17 compatibility lane for performance, security, local-network, large-screen, and test-stack changes.

## Scope Definition (CRITICAL)
### In Scope
- Register functionality via App Actions API for Gemini Agent integration.
- Implement adaptive 'App Bubble' support for the Chronos Dial.
- Track memory and performance diagnostics that matter for Android 17 compatibility.
- Keep future local-network sync behind an explicit runtime-permission decision.

### Out of Scope (DO NOT TOUCH)
- Cloud sync backend.
- Broad `ACCESS_LOCAL_NETWORK` declaration until ChronosFlow ships LAN sync or companion-device sync.

## Current State Analysis
- **Navigation**: `MainActivity` is edge-to-edge and resizable; `BubbleActivity` is available for the Chronos Dial bubble surface.
- **AI**: `shortcuts.xml` is registered for App Actions and the current AI/planner skeleton stays behind app-owned commands.
- **Monitoring**: `ChronosApplication` records `ApplicationExitInfo` availability and historical process exit counts during startup.

## Implementation Phases
### Phase 1: Agentic AI & App Actions
- **Goal**: Enable Gemini to "steer" users to ChronosFlow features.
- **Steps**:
  1. [x] Create `app/src/main/res/xml/shortcuts.xml` defining BIIs (Built-In Intents) for Task creation and Planner viewing.
  2. [x] Update `AndroidManifest.xml` to include meta-data for shortcuts.
- **Verification**: `gcloud` or manual intent testing via Google Assistant/Gemini overlay.

### Phase 2: Multitasking & Adaptive UI
- **Goal**: Enable App Bubbles for the Chronos Dial and ensure large-screen resizability.
- **Steps**:
  1. [x] Update `AndroidManifest.xml` to set `allowEmbedded="true"` and `resizeableActivity="true"`.
  2. [x] Implement a specialized `BubbleActivity` that hosts `DayDialScreen`.
- **Verification**: Launch `DayDialScreen` in a floating bubble via notification settings.

### Phase 3: Performance, Security, and Compatibility
- **Goal**: Keep Android 17 changes visible without depending on unstable or unnecessary platform surface.
- **Steps**:
  1. [x] Remove broad local-network permission from the manifest until LAN sync exists.
  2. [x] Add an Android 17 emulator/device smoke lane for Today, Chronos Dial, focus, alarms, and notifications.
  3. [x] Review Espresso, Robolectric, and any debug/test tooling for `MessageQueue` reflection assumptions.
  4. [x] Monitor memory pressure using `ApplicationExitInfo` once runtime instrumentation is added.
- **Verification**: Run `.\scripts\android17-smoke.ps1` on an API 37 device/emulator and confirm no local-network permission prompt appears in the current product surface. Run `.\scripts\android-memory-budget-smoke.ps1` to capture Today/Chronos Dial, Calendars, and Focus Planner total PSS under the configured memory cap.
