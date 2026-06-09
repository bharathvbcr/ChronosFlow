# Editable Templates Design

**Date:** 2026-05-25

**Goal:** Make DayDial templates fully editable, including built-in templates, by replacing the current read-only gate with a shared editor that supports renaming templates and editing their block lists in place.

## Summary

DayDial currently exposes built-in templates such as `Workday`, `Study Day`, and `Weekend`, but treats them as read-only. Tapping `Edit` on a built-in template shows a read-only message, while `Copy` creates a custom editable clone. The current template dialog also only supports renaming a custom template, not editing its block contents.

This design removes that split behavior. All templates should be editable through the same flow, including built-ins. The editor should support:

- changing the template name
- adding template blocks
- editing existing template blocks
- deleting template blocks
- reordering template blocks

The implementation should reuse the existing DayDial block-editing patterns wherever practical so the UI and validation rules stay consistent between live day blocks and template blocks.

## Product Requirements

### Built-in templates

Built-in templates are no longer read-only. Tapping `Edit` on a built-in template opens the full template editor and saves changes back to that template's existing identity.

Examples:

- editing `Workday` should keep the `tpl_workday` identity
- applying `Workday` after edits should use the edited block list
- built-in templates should not require copying before changes can be saved

### Custom templates

Custom templates continue to work through the same editor. Their editing behavior should match built-ins so users do not have to learn two different template models.

### Full template editing

The template editor must support:

- editing template name
- editing each block's title
- editing each block's start time
- editing each block's duration
- editing each block's category
- adding a new block to the template
- deleting a block from the template
- reordering blocks within the template

The first implementation does not need advanced scheduling intelligence such as overlap prevention, auto-normalization, or conflict repair beyond basic input validation.

## Architecture

### State ownership

`DayDialTemplateState` should evolve from a rename-only state holder into a full draft editor for templates.

It should manage:

- the current template being edited
- the draft template name
- a draft list of template blocks
- block-level add, update, remove, and reorder actions
- save and delete actions

The existing template editor mode concept can remain, but it should distinguish between:

- creating a new template from the current day
- editing an existing template in place

### Shared editor components

The current day-block editor in the DayDial sheet already defines the app's editing conventions for:

- title input
- start-time parsing
- duration controls
- category selection

Those controls should be extracted into shared composables or helper functions that can be reused by both:

- live day-block editing
- template-block editing

This keeps template editing visually and behaviorally aligned with the main DayDial editing flow and reduces drift between the two editors.

## Data Model

### Template blueprint

`TemplateBlueprint` should no longer use `editable` as a hard gate for whether the template can be opened in the editor.

Recommended direction:

- remove the read-only restriction from template editing behavior
- keep template identity stable through `id`
- allow built-in seed entries to be replaced by user-edited state with the same `id`

This design does not require introducing a second override model or a separate built-in/custom inheritance tree. A template is simply an editable blueprint with a stable identifier.

### Template draft model

The editing flow should use a draft representation for template blocks so the user can make multiple changes before saving.

The draft model should capture:

- stable per-row draft identity for Compose list editing
- block title
- block start minute
- block duration minutes
- block category

Draft rows do not need persistence outside the editor session.

## UX Design

### Templates sidebar

The Templates sidebar continues to show `Apply`, `Edit`, and `Copy`.

Behavior changes:

- `Edit` always opens the full editor, even for built-ins
- the old "built-in and read-only" message is removed
- `Copy` still duplicates a template into a new template entry

### Template editor

The template editor remains dialog-based unless implementation pressure makes a sheet clearly better. The dialog should contain:

1. template name field
2. list of draft template blocks
3. controls to add a new block
4. controls to reorder blocks
5. save, cancel, and delete actions

Each template block row should provide:

- title field
- start-time field
- duration control
- category selector
- delete action

Reordering can be implemented with simple move-up/move-down controls in the first pass if drag-and-drop adds unnecessary complexity.

### Creating a template from the current day

`Save current day as template` should still seed a new template from the current day's blocks, but the create flow should now open the same full editor used for existing templates. This lets users adjust the generated template before saving.

## Validation and Error Handling

The first implementation should include lightweight validation:

- template name cannot be blank
- block title cannot be blank
- block start time must parse to a valid minute
- block duration must stay within the same supported range already used by DayDial block editing
- category must resolve to a supported category value

Validation failures should stay local to the editor and use the existing user feedback pattern such as inline messaging or snackbar copy. Invalid drafts should not be saved.

This change does not require:

- overlap detection between template blocks
- auto-sorting or auto-merging blocks
- advanced conflict messaging

## Persistence Strategy

The current implementation keeps templates in UI state rather than durable storage. This design stays within that model unless adjacent work already introduces persistence.

Behavior within the current state model:

- built-in templates provide the initial seed list
- edited built-ins replace the seeded values in the editable template list by matching `id`
- custom templates remain appendable user-created entries

If future work adds persistence, the same identity model should carry forward so edited built-ins remain stable user-owned templates rather than copied aliases.

## Testing Strategy

Add focused tests around the template state layer and any shared editor helpers:

- editing a built-in template updates that template in place
- editing a custom template still works
- saving a template can change both name and blocks
- adding a draft block persists into the saved template
- deleting a draft block removes it from the saved template
- reordering draft blocks preserves the saved order
- applying an edited built-in template uses the updated blocks
- validation rejects blank names or invalid block drafts

If shared block-form helpers are extracted, add targeted tests only where logic exists. Avoid UI-snapshot-heavy tests unless there is real behavior that state tests cannot cover.

## Non-Goals

This change does not attempt to:

- add permanent template storage or sync
- introduce a multi-template inheritance system
- add schedule-conflict intelligence for template editing
- redesign the entire Templates sidebar
- unify templates across habits, medication, or other features

## Recommended Implementation Direction

Convert DayDial templates into a single fully editable model, extract shared block-editing controls from the existing DayDial block editor, and use those shared controls in a richer template editor that updates built-in and custom templates in place.
