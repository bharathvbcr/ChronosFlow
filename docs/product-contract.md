# ChronosFlow Product Contract

## Product Promise

ChronosFlow makes time the organizing primitive. Tasks, routines, focus sessions, reminders, and reviews are useful only when they become visible commitments, adjustable plans, or actual-time evidence.

## Primary Workflows

1. Capture intent in Plan or through the Launcher.
2. Map intent to a visible time block on the Chronos Dial.
3. Execute the scheduled block through Focus.
4. Review planned versus actual outcomes at the end of the day.

## Visible MVP Surfaces

| Surface | Status | Contract |
| --- | --- | --- |
| Today | MVP | Primary daily operating view with dial, now/next context, and immediate adjustments. |
| Plan | MVP | Task capture, scheduling, conflict handling, and user-reviewed suggestions. |
| Focus | MVP | Active execution state, foreground notification ownership, and actual-time logging. |
| Launcher | MVP | Global command access to avoid burying core actions in feature tabs. |

## Parked Surfaces

| Surface | Phase | Promotion Gate |
| --- | --- | --- |
| Habits | Phase 2 | Habit objects can reliably project onto the dial and daily repair flow. |
| Medication | Phase 2 | Reminder precision, acknowledgement, and safety copy are verified. |
| Review | Phase 2 | Actual-time data is reliable enough for daily summaries. |
| AI deep planning | Phase 3 | Suggestions are explainable, reviewable, and never auto-applied. |
| Sync and analytics | Phase 3 | Conflict handling and privacy boundaries are explicit. |

## Hard Constraints

- One app shell owns global navigation and chrome.
- Feature screens must not create duplicate top bars, bottom bars, or independent app shells.
- AI and assistant flows suggest; the user applies.
- Sensitive data surfaces respect app-lock and privacy-mode gates.
- All planner/focus writes must preserve data integrity across process death.
- Planner mutations fail closed: if the day's schedule cannot be loaded, the write is rejected — never approved against a phantom-empty day.
- Every reminder is delivered on exactly one surface: folding must never swallow a reminder whose live surface is suppressed, and notification/PendingIntent identities stay stable and collision-free across process death.
- Exact alarms are reserved for user-critical timing; soft nudges must tolerate inexact scheduling.

## Major Flow Acceptance Criteria

Today:

- The selected day renders as a Chronos Dial with visible planned, actual, free, and conflicted time.
- Block interactions are contextual and reversible until committed.
- The UI remains usable on phone, tablet, foldable, and desktop-window sizes.

Plan:

- Tasks can be captured with enough context to become schedulable.
- Scheduling validates conflicts, recurrence, and source ownership before persistence.
- User-reviewed AI suggestions can be accepted, edited, rejected, or dismissed.

Focus:

- The service, not the UI loop, owns active countdown state.
- Notification actions pause, resume, extend, and stop the actual session.
- Actual-time logs are written once per completed or stopped linked block.

Launcher:

- The command palette is globally reachable from the shell.
- Commands expose core actions without requiring users to traverse parked feature surfaces.
- Sensitive commands remain guarded by the same app-lock and confirmation policies as direct UI actions.

Review:

- Daily review compares planned blocks, actual-time logs, skipped work, and user notes.
- Review output can inform future suggestions without mutating the plan automatically.
