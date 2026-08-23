# ChronosFlow Minimal Product Contract

ChronosFlow is a dial-first Android planner. The MVP is a focused day operating surface, not a broad tab collection.

## Scope

MVP surfaces:

| Surface | Phase | Purpose |
| --- | --- | --- |
| Today | MVP | Show the current day, Chronos Dial, active block, next block, free time, and conflicts. |
| Plan | MVP | Capture tasks, schedule work into time blocks, and expose planner conflicts before save. |
| Focus | MVP | Run an active focus session from a scheduled block and persist actual-time outcomes. |
| Launcher | MVP | Provide global command-palette access to create, search, and navigate without feature hunting. |

Stand-alone tasks, habits, medication, and review screens are parked from primary navigation until they have reliable dial handoff.

Phase 2 surfaces:

| Surface | Phase | Purpose |
| --- | --- | --- |
| Habits | Phase 2 | Promote habits into planned routines and repair missed routines through user-approved suggestions. |
| Medication | Phase 2 | Track precise reminders, acknowledgements, and refill/support metadata without medical advice. |
| Review | Phase 2 | Summarize planned versus actual time and capture improvement notes. |

Phase 3 capabilities:

| Capability | Phase | Purpose |
| --- | --- | --- |
| AI deep planning | Phase 3 | Generate and repair plans with explicit review before any write. |
| Cross-device sync | Phase 3 | Reconcile planner, task, and review state across devices. |
| Advanced analytics | Phase 3 | Derive longer-term energy, focus, and planning patterns. |

## Out Of Scope For MVP

- Autonomous AI writes.
- Social, team, or project-management workflows.
- Medical advice or dosage recommendations.
- Full analytics dashboards beyond the daily review loop.
- Feature-first navigation that bypasses Today, Plan, Focus, and Launcher as the primary surface.

## Acceptance Criteria

Today:

- The dial renders the selected day with current, planned, free, and conflicted time.
- Users can inspect, move, resize, and edit blocks without duplicate shell chrome.
- The current-time hand and active/next context remain visible for today's date.

Plan:

- Users can create tasks and schedule them into validated time blocks.
- Planner writes reject invalid overlaps unless the user explicitly resolves them.
- AI suggestions remain pending until accepted, edited, or rejected by the user.

Focus:

- A focus session starts from a scheduled block, owns its foreground notification, and survives UI closure.
- Pause, resume, extend, stop, and completion actions mutate the persisted session state.
- Completion writes actual-time evidence exactly once.

Launcher:

- The command palette opens from the app shell and exposes create, search, and navigation commands from visible workflows.
- Commands do not bypass sensitive-area locks or user confirmation gates.

Data integrity:

- Process death recovery restores active focus/session state where applicable.
- Persisted planner changes are deterministic and test-covered in domain/data layers.
- A planner write against schedule data that failed to load is rejected, never approved.
- Advanced surfaces stay parked unless reachable through an explicit command, drawer, or promoted workflow.
