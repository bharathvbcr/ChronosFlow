package com.ChronosFlow.VBCR

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class AndroidBackupPolicyTest {

    @Test
    fun `manifest enables Android backup with explicit cloud and device transfer rules`() {
        val application = parseXml("src/main/AndroidManifest.xml").documentElement
            .elements("application")
            .single()

        assertEquals("true", application.androidAttr("allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.androidAttr("dataExtractionRules"))
        assertEquals("@xml/full_backup_content", application.androidAttr("fullBackupContent"))
    }

    @Test
    fun `android 12 plus backup rules protect encrypted storage and allow official phone transfer`() {
        val root = parseXml("src/main/res/xml/data_extraction_rules.xml").documentElement
        val cloudBackup = root.elements("cloud-backup").single()
        val deviceTransfer = root.elements("device-transfer").single()

        assertEquals("true", cloudBackup.attr("disableIfNoEncryptionCapabilities"))
        assertIncludes(cloudBackup, "sharedpref", ".")
        assertIncludes(cloudBackup, "file", "datastore/daydial_ui_settings.preferences_pb")
        assertIncludes(cloudBackup, "file", PORTABLE_BACKUP_PATH)
        assertIncludes(deviceTransfer, "sharedpref", ".")
        assertIncludes(deviceTransfer, "file", "datastore/daydial_ui_settings.preferences_pb")
        // Portable backup JSON is plain-text; device-transfer (ADB/cable) has no OS-level
        // encryption, so it must not ship. The section ships only explicitly included paths, so
        // omission is the exclusion mechanism — an explicit <exclude> outside an included path is
        // rejected by the FullBackupContent lint check that guards the release build.
        assertNoIncludes(deviceTransfer, "file", PORTABLE_BACKUP_PATH)
        // Hardening: the only file-domain includes allowed in device-transfer are the two known
        // safe paths. A future broad include (e.g. domain="file" path=".") would silently sweep
        // the plaintext export onto the wire — this pins that shut.
        assertEquals(
            "device-transfer file includes must stay exactly the two known-safe paths",
            listOf("datastore/daydial_ui_settings.preferences_pb", "task_attachments/"),
            deviceTransfer.elements("include")
                .filter { it.attr("domain") == "file" }
                .map { it.attr("path") }
                .sorted()
        )

        listOf(cloudBackup, deviceTransfer).forEach { section ->
            assertNoIncludes(section, "database")
            assertExcludes(section, "sharedpref", "chronos_secure_database.xml")
            assertExcludes(section, "sharedpref", "chronos_db_migration.xml")
        }
        // Attachment binaries fit only where no backup quota applies: device transfer
        // carries them, cloud backup (25 MB quota) must not.
        assertNoIncludes(cloudBackup, "file", "task_attachments/")
        assertIncludes(deviceTransfer, "file", "task_attachments/")
    }

    @Test
    fun `android 11 backup rules mirror the safe fallback policy`() {
        val root = parseXml("src/main/res/xml/full_backup_content.xml").documentElement

        assertIncludes(root, "sharedpref", ".")
        assertIncludes(root, "file", "datastore/daydial_ui_settings.preferences_pb")
        assertIncludes(root, "file", PORTABLE_BACKUP_PATH)
        assertNoIncludes(root, "database")
        assertExcludes(root, "sharedpref", "chronos_secure_database.xml")
        assertExcludes(root, "sharedpref", "chronos_db_migration.xml")
        assertNoIncludes(root, "file", "task_attachments/")
    }

    private fun parseXml(path: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))

    private fun Element.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttr(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)

    private fun assertIncludes(element: Element, domain: String, path: String) {
        assertNotNull(
            "Expected include domain=$domain path=$path",
            element.elements("include").firstOrNull {
                it.attr("domain") == domain && it.attr("path") == path
            }
        )
    }

    private fun assertNoIncludes(element: Element, domain: String) {
        assertTrue(
            "Expected no include domain=$domain",
            element.elements("include").none {
                it.attr("domain") == domain
            }
        )
    }

    private fun assertNoIncludes(element: Element, domain: String, path: String) {
        assertTrue(
            "Expected no include domain=$domain path=$path",
            element.elements("include").none {
                it.attr("domain") == domain && it.attr("path") == path
            }
        )
    }

    private fun assertExcludes(element: Element, domain: String, path: String) {
        assertTrue(
            "Expected exclude domain=$domain path=$path",
            element.elements("exclude").any {
                it.attr("domain") == domain && it.attr("path") == path
            }
        )
    }

    private fun Element.attr(name: String): String = getAttribute(name)

    private companion object {
        const val PORTABLE_BACKUP_PATH = "android_transfer/chronosflow_portable_backup.json"
    }
}
