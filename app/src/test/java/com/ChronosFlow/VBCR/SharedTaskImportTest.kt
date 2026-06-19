package com.ChronosFlow.VBCR

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.ChronosFlow.VBCR.core.notifications.SECTION_TASKS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SharedTaskImportTest {

    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver

    private fun registerStream(uri: Uri, content: String) {
        shadowOf(resolver).registerInputStream(uri, content.byteInputStream(Charsets.UTF_8))
    }

    @Test
    fun `shared ics file imports vtodo summaries as bulk tasks`() {
        val uri = Uri.parse("content://test/tasks.ics")
        registerStream(
            uri,
            """
            BEGIN:VCALENDAR
            BEGIN:VTODO
            SUMMARY:Submit tax return
            END:VTODO
            BEGIN:VEVENT
            SUMMARY:Team standup
            END:VEVENT
            BEGIN:VTODO
            SUMMARY;LANGUAGE=en:Pick up dry cleaning
            END:VTODO
            END:VCALENDAR
            """.trimIndent()
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        val launch = parseSharedTaskImport(intent, resolver)

        assertNotNull(launch)
        assertEquals(SECTION_TASKS, launch!!.section)
        // VEVENT (a calendar entry) is excluded; only the two VTODOs become tasks.
        assertEquals(listOf("Submit tax return", "Pick up dry cleaning"), launch.bulkCapture)
    }

    @Test
    fun `shared txt file imports each line as a task`() {
        val uri = Uri.parse("content://test/list.txt")
        registerStream(uri, "- Wash car\n- Renew passport\n- Book flights")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        val launch = parseSharedTaskImport(intent, resolver)

        assertNotNull(launch)
        assertEquals(listOf("Wash car", "Renew passport", "Book flights"), launch!!.bulkCapture)
    }

    @Test
    fun `single-line text file opens the add sheet`() {
        val uri = Uri.parse("content://test/single.txt")
        registerStream(uri, "Buy milk")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        val launch = parseSharedTaskImport(intent, resolver)

        assertNotNull(launch)
        assertEquals("Buy milk", launch!!.capture)
        assertNull(launch.bulkCapture)
    }

    @Test
    fun `plain text share is handled without reading any stream`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Call the dentist")
        }

        val launch = parseSharedTaskImport(intent, contentResolver = null)

        assertNotNull(launch)
        assertEquals("Call the dentist", launch!!.capture)
    }
}
