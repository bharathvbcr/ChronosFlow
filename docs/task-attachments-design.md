# Task Attachments Design

ChronosFlow task attachments support Android's native Storage Access Framework picker for any document type the picker can return.

## Goals

- Let each task keep multiple file or image attachments.
- Let the user choose per attachment whether it stays linked to the original document URI or is imported into app-owned storage.
- Preserve a single featured image for richer task cards and task editing.
- Keep the data model additive beside existing task contacts and task actions.

## Storage model

- `LINKED` attachments persist the SAF `content://` URI and attempt to hold a persistable read permission.
- `IMPORTED` attachments copy bytes into `files/task_attachments/` and open through `FileProvider`.
- Attachments are stored as task child rows in `task_attachments`.

## UX model

- The task form exposes an `Attachments` section powered by `OpenMultipleDocuments("*/*")`.
- Each picked row exposes `Link` versus `Import copy`.
- Image rows can be marked as the single featured image.
- Task cards can open the primary action first, then fall back to the featured or first attachment.
