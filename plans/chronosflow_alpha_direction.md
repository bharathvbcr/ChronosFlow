# ChronosFlow Alpha Direction

## Product Goal

ChronosFlow should feel like a daily time operating surface, not a bundle of unrelated productivity features.

The alpha goal is to make the Chronos Dial the canonical place where the user can:

- see today,
- plan the next useful block,
- start or recover focus,
- inspect the commands available in the app.

## Active Layout

Primary surfaces:

- Today: current day state, active block, next block, free time, missed block recovery.
- Plan: build and rebalance the day.
- Focus: start or resume a protected focus interval.
- Command palette: global search and quick access for current app actions.

The command palette stays global through the app shell and should remain available from the DayDial header, non-Day screens, and Ctrl+K.

## Parked Features

These modules remain in the codebase for later wiring, but they are not primary app destinations in alpha:

- standalone Tasks screen,
- standalone Habits screen,
- standalone Medication screen,
- standalone Review screen.

Their domain data still matters. The intended path is to promote them back only when each one feeds the dial as an intent, scheduled arc, reminder, or actual-time trace.

## Layout Rule

Do not add another top-level tab just because a feature module exists. New work should first answer:

- what daily planning job it helps,
- where it appears on the dial,
- which command palette action exposes it,
- what evidence proves it is wired end to end.

## Next Promotion Criteria

A parked feature can return to primary navigation only when it has:

- a clear dial entry point,
- a persistence-backed flow,
- command palette coverage,
- focused UI or instrumentation tests,
- a concise product reason beyond "the screen exists."
