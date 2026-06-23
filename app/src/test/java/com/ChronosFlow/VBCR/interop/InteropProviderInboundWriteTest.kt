package com.ChronosFlow.VBCR.interop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Locks the security design of the inbound handoff write path added to [InteropProvider.insert].
 *
 * Like [InteropProviderProjectionSanitizationTest], the provider relies on Hilt entry points and a
 * real SQLCipher DB, so the gating contract is validated by source inspection rather than hosting a
 * full ContentProvider. The runtime guarantees follow from the gates being present and ordered; if a
 * future change removes one, these tests fail and force a deliberate decision.
 *
 * The trusted-peer assertions read [InteropContract] directly (it has no Android dependencies at
 * class-init time), pinning the Curio handoff contract on the ChronosFlow side.
 */
class InteropProviderInboundWriteTest {

    private val providerSource: String by lazy {
        listOf(
            File("src/main/java/com/ChronosFlow/VBCR/interop/InteropProvider.kt"),
            File("app/src/main/java/com/ChronosFlow/VBCR/interop/InteropProvider.kt"),
        ).first(File::exists).readText()
    }

    @Test
    fun `only the handoff path is writable`() {
        // insert() must bail out for any URI that is not the handoff path.
        assertTrue(
            "insert() must reject non-handoff URIs",
            providerSource.contains("matcher.match(uri) != CODE_HANDOFF) return null")
        )
        // update()/delete() stay no-ops — handoffs are insert-only.
        assertTrue(providerSource.contains("override fun update(") && providerSource.contains("): Int = 0"))
        assertTrue(providerSource.contains("override fun delete("))
    }

    @Test
    fun `inbound write is gated by the inbound flag and the peer signature`() {
        val insertBody = providerSource.substringAfter("override fun insert(")
        assertTrue(
            "insert() must check the dedicated inbound-accept flag",
            insertBody.contains("isInboundHandoffAccepted()")
        )
        assertTrue(
            "insert() must verify the calling package is a pinned peer",
            insertBody.contains("PeerVerifier.requireTrusted(ctx, callingPackage)")
        )
        // The inbound flag must be checked before the (more expensive) signature verification.
        assertTrue(
            "inbound flag must be checked before PeerVerifier",
            insertBody.indexOf("isInboundHandoffAccepted()") < insertBody.indexOf("PeerVerifier.requireTrusted")
        )
    }

    @Test
    fun `inbound write does NOT reuse the outbound sharing consent gate`() {
        // isInteropConsentGranted governs sharing data OUT (query). Reusing it for an inbound write
        // would force a receive-only user to also expose all their data.
        // Check for the CALL form (with parens); an explanatory comment may still name the flag.
        val insertBody = providerSource.substringAfter("override fun insert(")
        assertTrue(
            "insert() must not gate on the outbound sharing consent",
            !insertBody.contains("isInteropConsentGranted()")
        )
    }

    @Test
    fun `inbound tasks are tagged with the caller origin so they are not re-shared out`() {
        // The read cursors serve only `origin IS NULL`; tagging inbound rows keeps them private.
        assertTrue(
            "inbound task must set origin = caller",
            providerSource.contains("origin = caller")
        )
    }

    @Test
    fun `Curio is a pinned trusted peer`() {
        assertEquals("com.example", InteropContract.CURIO_PACKAGE)
        val curio = InteropContract.TRUSTED_PEERS.firstOrNull { it.packageName == InteropContract.CURIO_PACKAGE }
        assertTrue("Curio must be a trusted peer", curio != null)
        // Debug builds pin Curio's own debug keystore cert (it does not use the shared one).
        assertTrue(
            "Curio debug cert must be pinned in debug builds",
            curio!!.certSha256.contains(InteropContract.CURIO_DEBUG_CERT_SHA256)
        )
    }

    @Test
    fun `handoff contract literals match the Curio client schema`() {
        assertEquals("handoff", InteropContract.PATH_HANDOFF)
        assertEquals("kind", InteropContract.HANDOFF_KIND)
        assertEquals("url", InteropContract.HANDOFF_URL)
        assertEquals("title", InteropContract.HANDOFF_TITLE)
        assertEquals("text", InteropContract.HANDOFF_TEXT)
        assertEquals("reminder_at_epoch_ms", InteropContract.HANDOFF_REMINDER_AT)
        assertEquals("notes", InteropContract.HANDOFF_NOTES)
        assertEquals("reading", InteropContract.KIND_READING)
        assertEquals("inbox", InteropContract.KIND_INBOX)
        assertEquals("task", InteropContract.KIND_TASK)
    }
}
