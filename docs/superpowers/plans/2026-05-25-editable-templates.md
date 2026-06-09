# Editable Templates Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make DayDial templates fully editable, including built-in templates, with shared block-editing controls and in-place editing of template block lists.

**Architecture:** Expand `DayDialTemplateState` from a rename-only state holder into a full draft editor that can create and edit template blocks in memory. Extract the reusable time/category editing controls from `SheetContent.kt` into shared DayDial UI helpers, then use those helpers in both the live block editor and the template editor dialog so template and block editing stay behaviorally aligned.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit, Gradle

---

### Task 1: Add state-layer coverage for editable built-ins and block draft operations

**Files:**
- Modify: `feature/daydial/src/test/java/com/chronosflow/feature/daydial/DayDialTemplateStateTest.kt`
- Modify: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialTemplateState.kt`

- [x] **Step 1: Write failing state tests for built-in editing, block updates, and apply-after-edit**

```kotlin
@Test
fun `saveEditedTemplate updates built in template blocks in place`() {
    val workday = builtInDayDialTemplates().first { it.id == "tpl_workday" }
    val edited = workday.copy(
        name = "Workday Prime",
        blocks = listOf(
            TemplateBlockBlueprint("Deep Work", 8 * 60, 120, "WORK"),
            TemplateBlockBlueprint("Lunch", 12 * 60, 45, "BREAK")
        )
    )

    val result = saveTemplateEdit(
        templateEditorMode = TemplateEditorMode.EDIT,
        allTemplates = builtInDayDialTemplates(),
        customTemplates = emptyList(),
        templateToEdit = workday,
        editedName = edited.name,
        draftBlocks = edited.blocks
    )

    val saved = result.templates.first { it.id == "tpl_workday" }
    assertEquals("Workday Prime", saved.name)
    assertEquals(2, saved.blocks.size)
    assertEquals("Deep Work", saved.blocks.first().title)
}

@Test
fun `moveDraftBlockUp swaps neighboring blocks`() {
    val drafts = listOf(
        TemplateBlockDraft(id = "a", title = "One", startText = "08:00", durationText = "30", category = "WORK"),
        TemplateBlockDraft(id = "b", title = "Two", startText = "09:00", durationText = "30", category = "WORK")
    )

    val moved = moveTemplateDraftBlock(drafts, index = 1, direction = -1)

    assertEquals(listOf("b", "a"), moved.map { it.id })
}

@Test
fun `applyTemplate uses edited built in blocks`() {
    val template = TemplateBlueprint(
        id = "tpl_workday",
        name = "Workday",
        blocks = listOf(TemplateBlockBlueprint("Maker Time", 10 * 60, 90, "WORK"))
    )
    val created = mutableListOf<String>()

    applyTemplateBlueprint(template) { title, _, _, _ -> created += title }

    assertEquals(listOf("Maker Time"), created)
}
```

- [x] **Step 2: Run the template state tests to verify they fail**

Run: `./gradlew :feature:daydial:testDebugUnitTest --tests com.chronosflow.feature.daydial.DayDialTemplateStateTest`
Expected: FAIL because draft helpers and editable built-in save behavior do not exist yet.

- [x] **Step 3: Add draft models and pure helpers inside `DayDialTemplateState.kt`, then wire `saveEditedTemplate` to update templates by stable `id`**

```kotlin
internal data class TemplateBlockDraft(
    val id: String,
    val title: String,
    val startText: String,
    val durationText: String,
    val category: String
)

internal fun moveTemplateDraftBlock(
    drafts: List<TemplateBlockDraft>,
    index: Int,
    direction: Int
): List<TemplateBlockDraft> {
    val targetIndex = index + direction
    if (index !in drafts.indices || targetIndex !in drafts.indices) return drafts
    val mutable = drafts.toMutableList()
    val current = mutable.removeAt(index)
    mutable.add(targetIndex, current)
    return mutable
}
```

- [x] **Step 4: Re-run the template state tests to verify they pass**

Run: `./gradlew :feature:daydial:testDebugUnitTest --tests com.chronosflow.feature.daydial.DayDialTemplateStateTest`
Expected: PASS

### Task 2: Extract shared DayDial block-editing controls from the sheet editor

**Files:**
- Create: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/DayDialBlockEditorFields.kt`
- Modify: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/SheetContent.kt`

- [x] **Step 1: Write the shared editor helper signatures and move the reusable time/category UI out of `SheetContent.kt`**

```kotlin
@Composable
internal fun DayDialBlockEditorFields(
    title: String,
    onTitleChange: (String) -> Unit,
    startText: String,
    onStartTextChange: (String) -> Unit,
    durationText: String,
    onDurationTextChange: (String) -> Unit,
    category: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val startMinute = parseEditorMinute(startText) ?: 9 * 60
    val durationMinutes = durationText.toIntOrNull()?.coerceIn(5, 240) ?: 25
    val endMinute = (startMinute + durationMinutes) % 1440

    OutlinedTextField(value = title, onValueChange = onTitleChange, label = { Text("Title") })
    BlockTimeRangeSummary(start = startText, endTimeStr = formatEditorMinute(endMinute), durMin = durationMinutes)
    OutlinedTextField(value = startText, onValueChange = onStartTextChange, label = { Text("Start HH:mm") })
    Slider(
        value = durationMinutes.toFloat(),
        onValueChange = { onDurationTextChange(it.toInt().toString()) },
        valueRange = 5f..240f,
        steps = 47
    )
    CategoryChipSelector(selectedCategory = category, onCategorySelected = onCategorySelected)
}
```

- [x] **Step 2: Replace the duplicated block-editor field section in `SheetContent.kt` with the shared composable**

```kotlin
DayDialBlockEditorFields(
    title = title,
    onTitleChange = { title = it },
    startText = start,
    onStartTextChange = { start = it },
    durationText = duration,
    onDurationTextChange = { duration = it },
    category = category,
    onCategorySelected = { category = it }
)
```

- [x] **Step 3: Run the daydial unit tests to verify the extraction did not break compilation**

Run: `./gradlew :feature:daydial:testDebugUnitTest`
Expected: PASS

### Task 3: Rebuild `DayDialTemplateState` to support full template editing

**Files:**
- Modify: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialTemplateState.kt`
- Modify: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/model/TemplateBlueprint.kt`

- [x] **Step 1: Remove the rename-only state shape and add full draft editing callbacks to `DayDialTemplateState`**

```kotlin
internal class DayDialTemplateState(
    val allTemplates: List<TemplateBlueprint>,
    val templateToEdit: TemplateBlueprint?,
    val isTemplateEditorOpen: Boolean,
    val isCreatingTemplate: Boolean,
    val editTemplateName: String,
    val draftBlocks: List<TemplateBlockDraft>,
    val setEditTemplateName: (String) -> Unit,
    val updateDraftBlockTitle: (String, String) -> Unit,
    val updateDraftBlockStart: (String, String) -> Unit,
    val updateDraftBlockDuration: (String, String) -> Unit,
    val updateDraftBlockCategory: (String, String) -> Unit,
    val addDraftBlock: () -> Unit,
    val removeDraftBlock: (String) -> Unit,
    val moveDraftBlockUp: (String) -> Unit,
    val moveDraftBlockDown: (String) -> Unit,
    val dismissTemplateEditor: () -> Unit,
    val saveEditedTemplate: () -> Unit,
    val saveCurrentAsTemplate: () -> Unit,
    val applyTemplate: (TemplateBlueprint) -> Unit,
    val importBackupText: (String) -> Unit,
    val editTemplate: (TemplateBlueprint) -> Unit,
    val duplicateTemplate: (TemplateBlueprint) -> Unit,
    val deleteTemplate: (() -> Unit)?
)
```

- [x] **Step 2: Stop using `editable` as the gate for built-in editing and update the model constructor defaults**

```kotlin
internal data class TemplateBlueprint(
    val id: String,
    val name: String,
    val blocks: List<TemplateBlockBlueprint>
)
```

- [x] **Step 3: Seed create-mode drafts from the current day and edit-mode drafts from the selected template**

```kotlin
private fun toDraft(block: TemplateBlockBlueprint): TemplateBlockDraft =
    TemplateBlockDraft(
        id = UUID.randomUUID().toString(),
        title = block.title,
        startText = formatEditorMinute(block.startMinute),
        durationText = block.durationMinutes.toString(),
        category = block.category
    )
```

- [x] **Step 4: Save create and edit flows by normalizing the draft list back into `TemplateBlockBlueprint` values**

```kotlin
val normalizedBlocks = draftBlocks.mapNotNull { draft ->
    val startMinute = parseEditorMinute(draft.startText) ?: return@mapNotNull null
    val durationMinutes = draft.durationText.toIntOrNull()?.coerceIn(5, 240) ?: return@mapNotNull null
    val title = draft.title.trim()
    if (title.isBlank()) return@mapNotNull null
    TemplateBlockBlueprint(
        title = title,
        startMinute = startMinute,
        durationMinutes = durationMinutes,
        category = draft.category.uppercase(Locale.getDefault())
    )
}
```

- [x] **Step 5: Re-run the template state tests to verify create, edit, reorder, and built-in updates pass**

Run: `./gradlew :feature:daydial:testDebugUnitTest --tests com.chronosflow.feature.daydial.DayDialTemplateStateTest`
Expected: PASS

### Task 4: Build the full template editor dialog with shared block fields

**Files:**
- Modify: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/DayDialTemplateState.kt`
- Modify: `feature/daydial/src/main/java/com/chronosflow/feature/daydial/ui/SidebarPageContent.kt`

- [x] **Step 1: Replace the rename-only dialog body with a template block list and per-row editing controls**

```kotlin
LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    items(templateState.draftBlocks, key = { it.id }) { draft ->
        ChronosListCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DayDialBlockEditorFields(
                    title = draft.title,
                    onTitleChange = { templateState.updateDraftBlockTitle(draft.id, it) },
                    startText = draft.startText,
                    onStartTextChange = { templateState.updateDraftBlockStart(draft.id, it) },
                    durationText = draft.durationText,
                    onDurationTextChange = { templateState.updateDraftBlockDuration(draft.id, it) },
                    category = draft.category,
                    onCategorySelected = { templateState.updateDraftBlockCategory(draft.id, it) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { templateState.moveDraftBlockUp(draft.id) }) { Text("Move up") }
                    TextButton(onClick = { templateState.moveDraftBlockDown(draft.id) }) { Text("Move down") }
                    TextButton(onClick = { templateState.removeDraftBlock(draft.id) }) { Text("Delete") }
                }
            }
        }
    }
}
```

- [x] **Step 2: Add an explicit `Add block` action and keep save/delete/cancel actions in the dialog footer**

```kotlin
TextButton(onClick = templateState.addDraftBlock) {
    Text("Add block")
}
```

- [x] **Step 3: Update the sidebar helper copy so `Edit` now reflects the richer behavior**

```kotlin
Text(
    "Apply, edit, or duplicate reusable day blueprints.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)
```

- [x] **Step 4: Run the daydial unit tests to verify the richer editor compiles and state-driven behavior still passes**

Run: `./gradlew :feature:daydial:testDebugUnitTest`
Expected: PASS

### Task 5: Verify the finished feature end-to-end at the module level

**Files:**
- Modify: `feature/daydial/src/test/java/com/chronosflow/feature/daydial/DayDialTemplateStateTest.kt`

- [x] **Step 1: Add one final regression test covering the full built-in edit flow**

```kotlin
@Test
fun `editing built in template then applying it uses updated blocks`() {
    val workday = builtInDayDialTemplates().first { it.id == "tpl_workday" }
    val saved = saveTemplateEdit(
        templateEditorMode = TemplateEditorMode.EDIT,
        allTemplates = builtInDayDialTemplates(),
        customTemplates = emptyList(),
        templateToEdit = workday,
        editedName = "Workday",
        draftBlocks = listOf(
            TemplateBlockDraft(
                id = "1",
                title = "Custom Focus",
                startText = "10:00",
                durationText = "90",
                category = "WORK"
            )
        )
    ).templates.first { it.id == "tpl_workday" }

    assertEquals("Custom Focus", saved.blocks.single().title)
    assertEquals(10 * 60, saved.blocks.single().startMinute)
}
```

- [x] **Step 2: Run the focused test suite**

Run: `./gradlew :feature:daydial:testDebugUnitTest --tests com.chronosflow.feature.daydial.DayDialTemplateStateTest`
Expected: PASS

- [x] **Step 3: Run the full module test suite**

Run: `./gradlew :feature:daydial:testDebugUnitTest`
Expected: PASS
