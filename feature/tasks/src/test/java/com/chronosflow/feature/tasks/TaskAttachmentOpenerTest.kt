package com.chronosflow.feature.tasks

import android.content.Context
import com.chronosflow.core.domain.model.TaskAttachment
import com.chronosflow.core.domain.model.TaskAttachmentKind
import com.chronosflow.core.domain.model.TaskAttachmentStorageMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TaskAttachmentOpenerTest {
    @Test
    fun `primary attachment chooses featured image first`() {
        val featured = TaskAttachment(
            id = "featured",
            displayName = "Cover",
            kind = TaskAttachmentKind.IMAGE,
            storageMode = TaskAttachmentStorageMode.IMPORTED,
            reference = "task_attachments/cover.png",
            isFeaturedImage = true
        )
        val first = TaskAttachment(
            id = "first",
            displayName = "First",
            kind = TaskAttachmentKind.FILE,
            storageMode = TaskAttachmentStorageMode.IMPORTED,
            reference = "task_attachments/first.pdf"
        )

        assertEquals(featured, primaryTaskAttachment(listOf(featured, first)))
    }

    @Test
    fun `primary attachment falls back to first item when none are featured`() {
        val first = TaskAttachment(
            id = "first",
            displayName = "First",
            kind = TaskAttachmentKind.FILE,
            storageMode = TaskAttachmentStorageMode.IMPORTED,
            reference = "task_attachments/first.pdf"
        )
        val secondary = TaskAttachment(
            id = "secondary",
            displayName = "Second",
            kind = TaskAttachmentKind.FILE,
            storageMode = TaskAttachmentStorageMode.IMPORTED,
            reference = "task_attachments/secondary.pdf"
        )

        assertEquals(first, primaryTaskAttachment(listOf(first, secondary)))
    }

    @Test
    fun `resolveTaskAttachmentUri imported returns null when file missing`() {
        val context = mockk<Context>(relaxed = true)
        val filesDir = File.createTempFile("task-attachment-imported", "").apply {
            delete()
            mkdirs()
        }
        every { context.filesDir } returns filesDir

        val imported = TaskAttachment(
            id = "attachment",
            displayName = "Missing",
            kind = TaskAttachmentKind.FILE,
            storageMode = TaskAttachmentStorageMode.IMPORTED,
            reference = "task_attachments/missing.pdf"
        )

        assertNull(resolveTaskAttachmentUri(context, imported))
    }
}
