# Task Connections Design

**Date:** 2026-05-25

**Goal:** Make ChronosFlow tasks more actionable by letting each task store a linked contact snapshot plus a powerful mixed action list that can include websites, documents, phone numbers, emails, maps, and custom deep links.

## Summary

Tasks currently store title, description, scheduling preferences, urgency, and checklist items. This design adds a new connections layer to the task itself:

- one optional linked contact snapshot
- zero or more task actions
- urgent reminders inherit the task's connections instead of defining their own override model

The app is still in an active build phase, so this design favors structured extensibility over minimum-change delivery. The result should be powerful enough to support task-centric outreach, follow-ups, docs, and reminders without immediately needing a second migration.

## Product Requirements

### Task links

Each task can store a mixed list of actions. Actions are launchable items with a label, type, and value.

Supported initial action types:

- `WEBSITE`
- `DOCUMENT`
- `PHONE`
- `EMAIL`
- `MAP`
- `CUSTOM_DEEP_LINK`

Examples:

- "Project docs" -> `https://...`
- "Client website" -> `https://...`
- "Call Alex" -> `tel:+1...`
- "Email launch team" -> `mailto:...`
- "Venue map" -> `geo:...`
- "App shortcut" -> custom app URI

### Linked contact

Each task can optionally store one selected contact. The contact is persisted as a snapshot so the task still has useful data after the picker grant expires or the process dies.

The snapshot includes:

- display name
- optional lookup key
- selected phone entries
- selected email entries
- method labels if available
- primary/default flags where known

### Reminder inheritance

Urgent reminders do not get a second contact or action model. When a reminder fires, it should surface the task's saved connections:

- the notification still opens the Tasks section as the main content tap action
- the notification may expose one or more quick actions based on the task's primary saved actions

## Android Contact Picker Guidance

The implementation should follow Android's system contact picker guidance:

- use the system picker instead of broad contact-list browsing UI
- do not require blanket `READ_CONTACTS` permission for basic selection flow
- treat the returned contact/session URI access as temporary
- query the selected data immediately and persist the snapshot needed by the task
- do not depend on reopening the temporary URI later

Because the app needs durable task-linked contact data, the design intentionally stores the contact details needed later instead of relying on the picker result grant.

## Domain Model

The `Task` domain model will be expanded with:

- `linkedContact: TaskContactSnapshot?`
- `actions: List<TaskAction>`

New domain types:

- `TaskContactSnapshot`
- `TaskContactMethod`
- `ContactMethodKind`
- `TaskAction`
- `TaskActionType`

Design constraints:

- domain types stay small and immutable
- action values are raw launchable values, not presentation-only text
- tasks can exist without any connections
- contact methods are stored independently from task actions so a selected contact remains intelligible even if no quick action is marked primary

## Persistence Model

Room storage will extend the existing task/checklist structure with dedicated child tables instead of packing everything into JSON.

### Tables

Add:

- `task_contact_snapshots`
- `task_contact_methods`
- `task_actions`

Keep:

- `tasks`
- `task_checklist_items`

Rationale:

- better migrations and testability
- preserves typed storage
- keeps future filtering/querying possible
- aligns with the existing checklist-child-table pattern

### Relationships

- one `TaskEntity` -> zero or one `TaskContactSnapshotEntity`
- one `TaskContactSnapshotEntity` -> many `TaskContactMethodEntity`
- one `TaskEntity` -> many `TaskActionEntity`

All child rows cascade on task delete.

## UX Design

### Task form

Add a new `Connections` section to `TaskFormSheet` after the description and before scheduling/priority details.

The section contains:

1. **Linked contact card**
   - `Pick contact` button
   - summary of selected contact name
   - visible saved phone/email rows
   - remove contact action

2. **Task action list**
   - rows for existing actions
   - add action flow
   - edit/remove actions
   - primary toggle for the best quick-launch action

### Add action flow

The add-action experience should let the user:

- choose an action type
- enter a label
- enter the raw value
- optionally mark the action as primary

Validation should be type-aware:

- `WEBSITE` and `DOCUMENT` accept web URLs
- `PHONE` accepts phone-like values
- `EMAIL` accepts email addresses or mailto-compatible targets
- `MAP` accepts map/geolocation URLs or geo URIs
- `CUSTOM_DEEP_LINK` accepts any URI-like string

### Task list

Task cards should show a compact summary when connections exist, for example:

- linked contact name
- count of actions or a primary action label

The task list does not need to expose the full action editor.

## Reminder and Launch Behavior

### Task surface

From the task screen, users should be able to launch saved actions directly from the task detail/form context. The list screen may expose only the most relevant shortcut if space allows.

### Urgent reminder surface

When an urgent task reminder fires:

- tapping the notification opens the Tasks section as it does now
- the notification body still uses the task title and description
- quick actions can be added for the task's primary saved actions, with a conservative cap to avoid clutter

Suggested priority order for notification shortcuts:

1. primary phone action
2. primary email action
3. primary website/document action

If no explicit primary action exists, use the first valid action.

## Error Handling

- invalid or unsupported action values are rejected in the form
- launching failures show a user-visible status/snackbar instead of crashing
- contact picker cancellation leaves the current task state unchanged
- tasks missing a contact or actions still save normally

## Migration Strategy

The Room database version will be incremented and a new migration will create the new child tables.

Existing tasks:

- remain valid without backfill
- load with `linkedContact = null`
- load with `actions = emptyList()`

No destructive migration should be introduced for this feature.

## Testing Strategy

The implementation should follow TDD and add focused coverage in the modules being changed:

- domain/use-case tests for expanded `Task`
- repository/mapper tests for contact snapshot + actions persistence
- task form logic tests for action validation and draft normalization
- view model tests for add/update argument forwarding
- notification tests for reminder routing and quick actions
- action-launch helper tests for URI building and intent selection

## Non-Goals

This change does not attempt to:

- sync contacts with live device changes
- support multiple linked contacts per task
- create a cross-feature global attachments platform
- add cloud sync or sharing behavior for task connections

## Recommended Implementation Direction

Use a strongly typed task-connections model with dedicated Room entities, a Compose-based connections editor in the task form, Android's system contact picker for contact snapshot capture, and task-derived reminder quick actions for the most relevant saved links.
