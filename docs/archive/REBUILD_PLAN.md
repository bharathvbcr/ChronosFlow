# ChronosFlow Rebuild Plan & AI Prompts

This is a rebuild blueprint for implementation sequencing in a fresh repository context; it is not the current status source of truth. Use `README.md` and `docs/product-contract.md` for today's delivered behavior.

This document outlines the architecture, specifications, and a phased prompt guide to rebuild ChronosFlow from scratch in a new IDE. The goal of this rebuild is to ensure a clean, modular architecture, avoiding the "clumsiness" and "over-feature" bloat of the previous iteration, while keeping the core 24-hour visual planning experience intact.

---

## 1. App Specifications & Flow

**App Name:** ChronosFlow
**Core Concept:** A daily operating surface where tasks, reminders, routines, and focus sessions are mapped onto a 24-hour circular "Chronos Dial." It is time-first, not list-first.
**Primary Navigation:** Today (Dial), Plan (Lists/Inbox), Focus (Active Timer), Command Palette (Global actions).

### Core Features (MVP to Polish)
1. **Chronos Dial:** A 24-hour circular view. Outer ring (fixed calendar events), Middle ring (planned time blocks), Inner ring (tasks/habits/medication), Center (current block summary), Now Hand (current time).
2. **Task Planner:** Create, prioritize, and schedule tasks directly into time blocks.
3. **Focus Engine:** Pomodoro/custom timer launched from a time block. Features foreground notifications and Android 16 Live Updates.
4. **Habits & Medication:** Time-window habits and exact-alarm medication tracking. Handled as inputs to the daily dial.
5. **Mood/Energy Check-ins:** Short logging to track optimal deep-work windows.
6. **Daily Review:** End-of-day comparison between planned time vs. actual time.
7. **AI Assistant:** Suggests daily plans, finds deep-work windows, and repairs overloaded schedules. Requires manual review before applying changes.

### Tech Stack & Architecture
*   **Language:** Kotlin
*   **UI:** Jetpack Compose (Material 3 Expressive, Edge-to-Edge, Adaptive layouts)
*   **Architecture:** Clean Architecture + MVVM/MVI
*   **Local Storage:** Room Database
*   **Async/Concurrency:** Kotlin Coroutines & Flow
*   **Background Work:** WorkManager (sync/AI), Foreground Services (Focus Timer), AlarmManager (`SCHEDULE_EXACT_ALARM` for meds).
*   **Modularization Strategy:**
    *   `:app` (Wiring, DI, Navigation)
    *   `:core:design` (Theme, Compose components)
    *   `:core:domain` (Pure models, UseCases, Time Math)
    *   `:core:data` (Room, DAOs, Repositories)
    *   `:feature:dial`, `:feature:tasks`, `:feature:focus`, `:feature:ai`

---

## 2. Phase-by-Phase Execution Prompts

To rebuild the app cleanly, use the following prompts in sequence. **Do not move to the next phase until the current one compiles, runs, and is tested.**

### Phase 1: Project Setup & Core Scaffolding
**Prompt:**
> "I want to start a brand new Android project for 'ChronosFlow' from scratch. Please set up the base project using Kotlin, Jetpack Compose, and a multi-module architecture. 
> 
> 1. Create the `settings.gradle.kts` to include the following modules: `:app`, `:core:design`, `:core:domain`, `:core:data`, and `:feature:dial`.
> 2. Set up a Version Catalog (`libs.versions.toml`) including Compose BOM, Material 3, Room, Coroutines, ViewModel, and Hilt/Koin (for DI).
> 3. In `:core:design`, create a base Material 3 Theme (`Color.kt`, `Theme.kt`, `Type.kt`).
> 4. In `:app`, set up the `MainActivity` with Edge-to-Edge and a basic Compose Navigation scaffold with a bottom navigation bar pointing to 'Today', 'Plan', and 'Focus'. Ensure the app compiles and runs."

### Phase 2: Domain Models & Data Layer
**Prompt:**
> "Let's build the Data and Domain layers for ChronosFlow. The app relies on mapping everything to a 24-hour timeline.
> 
> 1. In `:core:domain`, create data classes for: `TimeBlock` (id, title, startTime, endTime, color, type), `Task` (id, title, isCompleted, linkedTimeBlockId), and `FocusSession` (id, timeBlockId, duration, completed).
> 2. In `:core:data`, set up the Room Database (`ChronosDatabase`). Create DAOs for `TimeBlockDao` and `TaskDao` with Flow-based queries (e.g., `getBlocksForDate(date)`).
> 3. Implement the Repositories bridging Domain and Data.
> 4. Write unit tests for the DAOs and Repositories to ensure time overlapping logic and CRUD operations work perfectly. Keep it clean and avoid adding habits or medications yet."

### Phase 3: The Chronos Dial (Signature UI)
**Prompt:**
> "Now we build the core UI component: The Chronos Dial in the `:feature:dial` module. This is a 24-hour circular planner.
> 
> 1. Create a custom Compose Canvas component called `ChronosDial`. 
> 2. It needs to draw a 24-hour clock face. 
> 3. It should accept a list of `TimeBlock` domain models and draw them as colored arcs on the middle ring based on their `startTime` and `endTime`.
> 4. Add a 'Now Hand' that represents the current system time.
> 5. Center the dial with a text summary of the currently active `TimeBlock`.
> 6. Wire this up to a `DialViewModel` that fetches today's mock `TimeBlock`s from the repository. Display this on the 'Today' tab."

### Phase 4: Task Management & Daily Planning
**Prompt:**
> "Let's implement the 'Plan' tab in `:feature:tasks`.
> 
> 1. Create a screen that shows a list of uncompleted tasks (an Inbox) and a daily schedule view.
> 2. Implement a `TaskViewModel` with MVI/MVVM to handle intents: AddTask, ToggleTaskCompletion, and ScheduleTask.
> 3. Create a Bottom Sheet or Dialog that allows a user to take an uncompleted task and convert it into a `TimeBlock` (requiring a start time and end time).
> 4. Once scheduled, the task should appear as a block on the Chronos Dial. Ensure Room DB transactions handle linking the task to the new time block safely."

### Phase 5: Focus Engine & Foreground Service
**Prompt:**
> "Let's build the 'Focus' feature in `:feature:focus`. This turns a planned time block into an active working session.
> 
> 1. Create a `FocusTimerService` (Foreground Service) that manages a countdown timer (e.g., Pomodoro). It needs a persistent notification showing time remaining.
> 2. Implement Android 16 progress-centric notification style if possible, falling back gracefully for older versions.
> 3. Create a `FocusScreen` that connects to this service, displaying a large timer, a 'Start/Pause' button, and the name of the active `TimeBlock`.
> 4. When the timer finishes, save a `FocusSession` record to the database."

### Phase 6: Habits, Medications, & Exact Alarms
**Prompt:**
> "Now let's safely add Habits and Medications without bloating the core flow. 
> 
> 1. Add `Habit` and `Medication` models to `:core:domain` and update the Room Database. 
> 2. Build an `AlarmManager` wrapper in `:core:data` specifically using `SCHEDULE_EXACT_ALARM` for medications, with a permission request flow. 
> 3. Habits should use flexible time windows (e.g., 'Morning') and show up as small indicators on the inner ring of the `ChronosDial` Canvas.
> 4. Create a unified command palette (a floating search/action bar) on the main screen that allows quick-adding tasks, habits, or medications."

### Phase 7: Daily Review & AI Assistant
**Prompt:**
> "Finally, let's implement the Daily Review and AI features.
> 
> 1. Create a `ReviewScreen` that pops up at the end of the day. It compares the sum of `FocusSessions` against planned `TimeBlocks`.
> 2. In `:feature:ai`, set up a repository that can construct a prompt containing today's incomplete tasks and free time slots.
> 3. Use an AI/LLM SDK to generate a suggested schedule. 
> 4. VERY IMPORTANT: Do not auto-apply the AI schedule. Present the suggested `TimeBlocks` in a review sheet where the user must explicitly tap 'Apply Plan'. Save approved blocks to Room."
