package com.ChronosFlow.VBCR.feature.tasks

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentKind
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachmentStorageMode
import com.ChronosFlow.VBCR.core.domain.model.TaskAttachment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class TaskAttachmentDraftsTest {
    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            parsedUri("content")
        }
    }

    @After
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `buildTaskAttachmentDrafts maps metadata for linked image and normalizes name`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver

        val uriString = "content://example.com/legacy/documents/brief.pdf"
        val uri = uriWithPath(uriString)
        val cursor = mockedMetadataCursor(displayName = "brief.pdf", sizeBytes = 2048L)
        every {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                any(),
                any(),
                any()
            )
        } returns cursor
        every { contentResolver.getType(uri) } returns "image/png"

        val drafts = buildTaskAttachmentDrafts(context, listOf(uri))

        assertEquals(1, drafts.size)
        assertEquals("brief.pdf", drafts[0].displayName)
        assertEquals(TaskAttachmentKind.IMAGE, drafts[0].kind)
        assertEquals(TaskAttachmentStorageMode.LINKED, drafts[0].storageMode)
        assertTrue(drafts[0].sourceUri?.isNotBlank() == true)
    }

    @Test
    fun `buildTaskAttachmentDrafts uses fallback display name when metadata name is blank`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver

        val uriString = "content://example.com/legacy/notes.txt"
        val uri = uriWithPath(uriString)
        val cursor = mockedMetadataCursor(displayName = "", sizeBytes = 300L)
        every {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                any(),
                any(),
                any()
            )
        } returns cursor
        every { contentResolver.getType(uri) } returns "text/plain"

        val drafts = buildTaskAttachmentDrafts(context, listOf(uri))

        assertEquals(1, drafts.size)
        assertEquals("notes.txt", drafts[0].displayName)
        assertEquals(TaskAttachmentKind.FILE, drafts[0].kind)
    }

    @Test
    fun `buildTaskAttachmentDrafts drops uris with unusable metadata`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver

        every {
            contentResolver.query(
                any(),
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                any(),
                any(),
                any()
            )
        } returns null

        val drafts = buildTaskAttachmentDrafts(context, listOf(uriWithPath("content://example.com/missing")))

        assertEquals(0, drafts.size)
    }

    @Test
    fun `resolveTaskAttachmentDrafts returns linked attachment`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver

        val drafts = listOf(
            TaskAttachmentDraft(
                id = "linked-1",
                displayName = "Notes",
                mimeType = "text/plain",
                sizeBytes = 64L,
                kind = TaskAttachmentKind.FILE,
                storageMode = TaskAttachmentStorageMode.LINKED,
                sourceUri = "content://example.com/note.txt",
                persistedUriPermission = true
            )
        )

        val resolved = resolveTaskAttachmentDrafts(context, drafts)

        assertEquals(1, resolved.size)
        assertEquals("linked-1", resolved[0].id)
        assertEquals("content://example.com/note.txt", resolved[0].reference)
    }

    @Test
    fun `resolveTaskAttachmentDrafts imports source uri into app storage`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver

        val tempDir = File.createTempFile("task-attachment-tests", "")
            .apply { delete(); mkdirs() }
        every { context.filesDir } returns tempDir
        every { contentResolver.openInputStream(any()) } returns ByteArrayInputStream("hello attachment".toByteArray())

        val sourceUri = "content://example.com/imported/report.pdf"
        val drafts = listOf(
            TaskAttachmentDraft(
                id = "imported-1",
                displayName = "Report",
                mimeType = "application/pdf",
                sizeBytes = 128L,
                kind = TaskAttachmentKind.FILE,
                storageMode = TaskAttachmentStorageMode.IMPORTED,
                sourceUri = sourceUri
            )
        )

        val resolved = resolveTaskAttachmentDrafts(context, drafts)

        assertEquals(1, resolved.size)
        val attachment = resolved.first()
        assertEquals("imported-1", attachment.id)
        assertEquals(TaskAttachmentStorageMode.IMPORTED, attachment.storageMode)
        assertEquals(TaskAttachmentKind.FILE, attachment.kind)
        assertEquals("Report", attachment.displayName)
        assertEquals("application/pdf", attachment.mimeType)
        assertNotNull(attachment.reference)
        assertTrue(attachment.reference.startsWith("$TASK_ATTACHMENT_DIRECTORY/"))
        assertEquals(reportFileExists(tempDir, attachment.reference), true)
    }

    @Test
    fun `resolveTaskAttachmentDrafts drops blanks and invalid draft data`() = runTest {
        val context = mockk<Context>(relaxed = true)
        every { context.contentResolver } returns mockk(relaxed = true)

        val drafts = listOf(
            TaskAttachmentDraft(
                id = "linked-invalid",
                displayName = " ",
                mimeType = "text/plain",
                sizeBytes = 1L,
                kind = TaskAttachmentKind.FILE,
                storageMode = TaskAttachmentStorageMode.LINKED,
                sourceUri = "content://example.com/blank.txt"
            ),
            TaskAttachmentDraft(
                id = "linked-valid",
                displayName = "Plan",
                mimeType = "text/plain",
                sizeBytes = 2L,
                kind = TaskAttachmentKind.FILE,
                storageMode = TaskAttachmentStorageMode.LINKED,
                sourceUri = "content://example.com/plan.txt"
            )
        )

        val resolved = resolveTaskAttachmentDrafts(context, drafts)

        assertEquals(1, resolved.size)
        assertEquals("linked-valid", resolved[0].id)
    }

    private fun parsedUri(rawValue: String): Uri {
        return mockk<Uri>(relaxed = true) {
            every { scheme } returns rawValue.substringBefore(":", missingDelimiterValue = "")
        }
    }

    private fun mockedMetadataCursor(displayName: String, sizeBytes: Long?): Cursor {
        return mockk<Cursor>(relaxed = true) {
            every { getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
            every { getColumnIndex(OpenableColumns.SIZE) } returns 1
            every { moveToFirst() } returns true
            every { getString(0) } returns displayName
            if (sizeBytes != null) {
                every { getLong(1) } returns sizeBytes
                every { isNull(1) } returns false
            } else {
                every { isNull(1) } returns true
            }
        }
    }

    private fun uriWithPath(rawValue: String): Uri {
        return mockk<Uri>(relaxed = true) {
            every { lastPathSegment } returns rawValue.substringAfterLast('/')
        }
    }

    private fun reportFileExists(baseDir: File, reference: String): Boolean {
        return File(baseDir, reference).exists()
    }
}
