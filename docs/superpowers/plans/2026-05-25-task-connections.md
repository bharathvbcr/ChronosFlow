# Task Connections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add durable linked-contact snapshots and mixed launchable task actions to ChronosFlow tasks, wire them into the task form and task cards, and surface the best task actions from urgent reminders.

**Architecture:** Expand the `Task` domain model with typed connection objects, persist them with dedicated Room child tables beside the existing checklist structure, add Compose editing/picking flows in `feature/tasks`, and let urgent task notifications fetch the task's saved primary actions when the alarm fires. The contact picker uses Android's system picker and persists a snapshot immediately so the task remains useful after the picker grant expires.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Android Contacts provider, Intents/URIs, JUnit, MockK, coroutines-test

---

### Task 1: Add domain connection models and use-case coverage

**Files:**
- Create: `core/domain/src/main/java/com/chronosflow/core/domain/model/TaskConnectionModels.kt`
- Modify: `core/domain/src/main/java/com/chronosflow/core/domain/model/Task.kt`
- Modify: `core/domain/src/main/java/com/chronosflow/core/domain/usecase/AddTaskUseCase.kt`
- Modify: `core/domain/src/test/java/com/chronosflow/core/domain/usecase/AddTaskUseCaseTest.kt`

- [x] **Step 1: Write the failing use-case test that forwards contact snapshots and actions**

```kotlin
@Test
fun `invoke saves linked contact and task actions`() = runTest {
    val linkedContact = TaskContactSnapshot(
        displayName = "Alex Johnson",
        lookupKey = "lookup-1",
        methods = listOf(
            TaskContactMethod(
                id = "m1",
                kind = ContactMethodKind.PHONE,
                label = "mobile",
                value = "+15551234567",
                normalizedValue = "+15551234567",
                isPrimary = true
            )
        )
    )
    val actions = listOf(
        TaskAction(
            id = "a1",
            type = TaskActionType.WEBSITE,
            label = "Client site",
            value = "https://example.com",
            isPrimary = true
        )
    )

    coEvery { repository.saveTask(any()) } returns Unit

    useCase(
        title = "Follow up",
        linkedContact = linkedContact,
        actions = actions
    )

    coVerify {
        repository.saveTask(
            match {
                it.linkedContact == linkedContact &&
                    it.actions == actions
            }
        )
    }
}
```

- [x] **Step 2: Run the domain test to verify it fails**

Run: `./gradlew :core:domain:testDebugUnitTest --tests com.chronosflow.core.domain.usecase.AddTaskUseCaseTest`
Expected: FAIL because `Task` and `AddTaskUseCase` do not support connection fields yet.

- [x] **Step 3: Add minimal typed task-connection domain models and wire them into `Task` and `AddTaskUseCase`**

```kotlin
data class Task(
    // existing fields...
    val linkedContact: TaskContactSnapshot? = null,
    val actions: List<TaskAction> = emptyList()
)
```

- [x] **Step 4: Re-run the domain test to verify it passes**

Run: `./gradlew :core:domain:testDebugUnitTest --tests com.chronosflow.core.domain.usecase.AddTaskUseCaseTest`
Expected: PASS

### Task 2: Persist task connections through Room

**Files:**
- Modify: `core/data/src/main/java/com/chronosflow/core/data/ChronosDatabase.kt`
- Modify: `core/data/src/main/java/com/chronosflow/core/data/dao/TaskDao.kt`
- Modify: `core/data/src/main/java/com/chronosflow/core/data/repository/TaskRepositoryImpl.kt`
- Modify: `core/data/src/main/java/com/chronosflow/core/data/mapper/Mappers.kt`
- Modify: `core/data/src/main/java/com/chronosflow/core/data/model/TaskEntity.kt`
- Create: `core/data/src/main/java/com/chronosflow/core/data/model/TaskConnectionEntities.kt`
- Create: `core/data/src/main/java/com/chronosflow/core/data/model/TaskWithConnections.kt`
- Modify: `core/data/src/test/java/com/chronosflow/core/data/repository/TaskRepositoryImplTest.kt`

- [x] **Step 1: Write the failing repository test for saving and loading connection rows**

```kotlin
@Test
fun `saveTask persists contact snapshot and actions`() = runTest {
    val task = sampleTask(
        linkedContact = sampleContact(),
        actions = listOf(sampleWebsiteAction())
    )

    coEvery { taskDao.upsertTask(any()) } returns Unit
    coEvery { taskDao.replaceChecklistItems(any(), any()) } returns Unit
    coEvery { taskDao.replaceTaskContact(any(), any()) } returns Unit
    coEvery { taskDao.replaceTaskActions(any(), any()) } returns Unit

    repository.saveTask(task)

    coVerify { taskDao.replaceTaskContact("1", any()) }
    coVerify { taskDao.replaceTaskActions("1", any()) }
}
```

- [x] **Step 2: Run the data test to verify it fails**

Run: `./gradlew :core:data:testDebugUnitTest --tests com.chronosflow.core.data.repository.TaskRepositoryImplTest`
Expected: FAIL because the DAO/entities/mappers do not yet support connection persistence.

- [x] **Step 3: Add Room entities, relations, DAO replacement helpers, mappers, and a migration**

```kotlin
@Entity(tableName = "task_actions")
data class TaskActionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val type: String,
    val label: String,
    val value: String,
    val isPrimary: Boolean,
    val sortOrder: Int
)
```

- [x] **Step 4: Re-run the data test to verify it passes**

Run: `./gradlew :core:data:testDebugUnitTest --tests com.chronosflow.core.data.repository.TaskRepositoryImplTest`
Expected: PASS

### Task 3: Add task action validation and contact snapshot form logic

**Files:**
- Modify: `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TaskFormSheet.kt`
- Create: `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TaskConnectionDrafts.kt`
- Create: `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TaskContactPicker.kt`
- Modify: `feature/tasks/src/test/java/com/chronosflow/feature/tasks/TaskFormSheetLogicTest.kt`
- Modify: `feature/tasks/build.gradle.kts`

- [x] **Step 1: Write failing task-form logic tests for action normalization and validation**

```kotlin
@Test
fun normalizeTaskActionDraftMarksBlankLabelsInvalid() {
    val draft = TaskActionDraft(
        id = "a1",
        type = TaskActionType.WEBSITE,
        label = "",
        value = "https://example.com",
        isPrimary = false
    )

    assertNull(normalizeTaskActionDraft(draft))
}

@Test
fun normalizeTaskActionDraftBuildsWebsiteAction() {
    val draft = TaskActionDraft(
        id = "a1",
        type = TaskActionType.WEBSITE,
        label = "Docs",
        value = "example.com",
        isPrimary = true
    )

    assertEquals("https://example.com", normalizeTaskActionDraft(draft)?.value)
}
```

- [x] **Step 2: Run the task form logic tests to verify they fail**

Run: `./gradlew :feature:tasks:testDebugUnitTest --tests com.chronosflow.feature.tasks.TaskFormSheetLogicTest`
Expected: FAIL because task-action draft helpers do not exist yet.

- [x] **Step 3: Add connection draft models, validation helpers, and contact-picker snapshot parsing seams**

```kotlin
internal data class TaskActionDraft(
    val id: String,
    val type: TaskActionType,
    val label: String,
    val value: String,
    val isPrimary: Boolean
)
```

- [x] **Step 4: Re-run the task form logic tests to verify they pass**

Run: `./gradlew :feature:tasks:testDebugUnitTest --tests com.chronosflow.feature.tasks.TaskFormSheetLogicTest`
Expected: PASS

### Task 4: Wire connections through the task UI and view model

**Files:**
- Modify: `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TaskViewModel.kt`
- Modify: `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TaskScreen.kt`
- Modify: `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TaskFormSheet.kt`
- Modify: `feature/tasks/src/test/java/com/chronosflow/feature/tasks/TaskViewModelTest.kt`

- [x] **Step 1: Write the failing view-model test that forwards linked contact and actions**

```kotlin
@Test
fun `addTask forwards linked contact and actions`() = runTest {
    val linkedContact = sampleContact()
    val actions = listOf(sampleWebsiteAction())
    val created = sampleTask(linkedContact = linkedContact, actions = actions)

    coEvery {
        addTaskUseCase(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    } returns created

    viewModel.addTask(
        title = "Follow up",
        linkedContact = linkedContact,
        actions = actions
    )

    coVerify {
        addTaskUseCase(
            "Follow up",
            null,
            0,
            null,
            null,
            null,
            null,
            emptyList(),
            linkedContact,
            actions
        )
    }
}
```

- [x] **Step 2: Run the task view-model test to verify it fails**

Run: `./gradlew :feature:tasks:testDebugUnitTest --tests com.chronosflow.feature.tasks.TaskViewModelTest`
Expected: FAIL because the view model and screen do not yet carry connection fields.

- [x] **Step 3: Update the task screen and form to edit, display, and save task connections**

```kotlin
TaskFormSheet(
    // existing args...
    onConfirm = { title, description, priority, dueDate, alarmEnabled, preferredDurationMinutes,
        preferredStartMinuteOfDay, targetDate, checklist, linkedContact, actions ->
        // pass through to add/update
    }
)
```

- [x] **Step 4: Re-run the task view-model test to verify it passes**

Run: `./gradlew :feature:tasks:testDebugUnitTest --tests com.chronosflow.feature.tasks.TaskViewModelTest`
Expected: PASS

### Task 5: Launch task actions and surface reminder quick actions

**Files:**
- Create: `feature/tasks/src/main/java/com/chronosflow/feature/tasks/TaskActionLauncher.kt`
- Create: `feature/tasks/src/test/java/com/chronosflow/feature/tasks/TaskActionLauncherTest.kt`
- Modify: `core/notifications/src/main/java/com/chronosflow/core/notifications/AlarmReceiver.kt`
- Modify: `core/notifications/src/test/java/com/chronosflow/core/notifications/NotificationLaunchIntentTest.kt`

- [x] **Step 1: Write failing tests for primary task action launch intents**

```kotlin
@Test
fun `website action builds browse intent`() {
    val intent = buildTaskActionIntent(
        TaskAction(
            id = "a1",
            type = TaskActionType.WEBSITE,
            label = "Docs",
            value = "https://example.com",
            isPrimary = true
        )
    )

    assertEquals(Intent.ACTION_VIEW, intent.action)
    assertEquals("https://example.com", intent.dataString)
}
```

- [x] **Step 2: Run the task action launcher test to verify it fails**

Run: `./gradlew :feature:tasks:testDebugUnitTest --tests com.chronosflow.feature.tasks.TaskActionLauncherTest`
Expected: FAIL because the launcher helper does not exist yet.

- [x] **Step 3: Add task action launching and reminder notification shortcuts**

```kotlin
val primaryActions = task.actions
    .sortedByDescending { it.isPrimary }
    .take(3)
```

- [x] **Step 4: Re-run the focused notification and launcher tests**

Run: `./gradlew :feature:tasks:testDebugUnitTest --tests com.chronosflow.feature.tasks.TaskActionLauncherTest :core:notifications:testDebugUnitTest --tests com.chronosflow.core.notifications.NotificationLaunchIntentTest`
Expected: PASS

### Task 6: Run focused verification

**Files:**
- Modify: `core/domain/src/test/java/com/chronosflow/core/domain/usecase/AddTaskUseCaseTest.kt`
- Modify: `core/data/src/test/java/com/chronosflow/core/data/repository/TaskRepositoryImplTest.kt`
- Modify: `feature/tasks/src/test/java/com/chronosflow/feature/tasks/TaskFormSheetLogicTest.kt`
- Modify: `feature/tasks/src/test/java/com/chronosflow/feature/tasks/TaskViewModelTest.kt`
- Create: `feature/tasks/src/test/java/com/chronosflow/feature/tasks/TaskActionLauncherTest.kt`
- Modify: `core/notifications/src/test/java/com/chronosflow/core/notifications/NotificationLaunchIntentTest.kt`

- [x] **Step 1: Run domain tests**

Run: `./gradlew :core:domain:testDebugUnitTest --tests com.chronosflow.core.domain.usecase.AddTaskUseCaseTest`
Expected: PASS

- [x] **Step 2: Run data tests**

Run: `./gradlew :core:data:testDebugUnitTest --tests com.chronosflow.core.data.repository.TaskRepositoryImplTest`
Expected: PASS

- [x] **Step 3: Run task feature tests**

Run: `./gradlew :feature:tasks:testDebugUnitTest`
Expected: PASS

- [x] **Step 4: Run notification tests**

Run: `./gradlew :core:notifications:testDebugUnitTest --tests com.chronosflow.core.notifications.NotificationLaunchIntentTest`
Expected: PASS

- [x] **Step 5: Run one cross-module confidence check**

Run: `./gradlew :core:domain:testDebugUnitTest :core:data:testDebugUnitTest :feature:tasks:testDebugUnitTest :core:notifications:testDebugUnitTest`
Expected: PASS
