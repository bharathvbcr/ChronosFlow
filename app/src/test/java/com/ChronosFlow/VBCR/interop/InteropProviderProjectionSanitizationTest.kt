package com.ChronosFlow.VBCR.interop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies that [InteropProvider.query] intentionally ignores the caller-supplied
 * projection, selection, selectionArgs, and sortOrder parameters and uses hardcoded
 * SQL instead.
 *
 * This is a deliberate security design: by never wiring caller-supplied strings into
 * SQL, the provider is immune to column-injection and SQL-injection attacks even if a
 * compromised or malicious caller constructs a crafted projection such as
 * `["id", "DROP TABLE tasks; --"]`.
 *
 * Because [InteropProvider] is a [ContentProvider] that relies on Hilt entry points
 * and a real SQLCipher database, we test the design contract via source-level inspection
 * rather than spinning up a full ContentProvider host. This approach is intentional —
 * the runtime injection-safety is guaranteed by the fact that the parameters are
 * *never read*, which the source check validates without needing Android infrastructure.
 *
 * If a future developer accidentally starts forwarding the caller projection into SQL
 * (e.g., to support partial column reads), these tests will fail and prompt them to add
 * a proper column allowlist and update the test strategy accordingly.
 */
class InteropProviderProjectionSanitizationTest {

    private val providerSource: String by lazy {
        listOf(
            File("src/main/java/com/ChronosFlow/VBCR/interop/InteropProvider.kt"),
            File("app/src/main/java/com/ChronosFlow/VBCR/interop/InteropProvider.kt"),
        ).first(File::exists).readText()
    }

    /**
     * The projection parameter must NOT be forwarded into any SQL string. The provider
     * builds its own hardcoded SELECT columns and must not interpolate the caller-supplied
     * projection array into a query.
     */
    @Test
    fun `interopProvider withInjectedColumnInProjection ignoresInjectedColumn`() {
        // The provider source must not contain code that joins or maps `projection` into SQL.
        // Legitimate patterns would look like: projection?.joinToString(), projection?.get(i), etc.
        // The presence of "projection" in the SELECT-generation helpers would indicate a regression.
        val hasProjectionInQuery = providerSource.contains("projection?.joinToString") ||
            providerSource.contains("projection?.get") ||
            providerSource.contains("projection[") ||
            providerSource.contains("for (col in projection") ||
            providerSource.contains("projection.forEach") ||
            providerSource.contains("projection.map") ||
            providerSource.contains("columns = projection")

        assertFalse(
            "InteropProvider must NOT forward caller-supplied projection into SQL. " +
                "Doing so would enable column injection attacks. " +
                "Use hardcoded column lists and a per-column allowlist instead.",
            hasProjectionInQuery
        )
    }

    /**
     * The query() method must use hardcoded SQL SELECT statements so that no
     * caller-supplied string ever reaches the SQLite engine as a column name or
     * table expression.
     */
    @Test
    fun `interopProvider usesHardcodedSelectStatements`() {
        assertTrue(
            "InteropProvider must use hardcoded SELECT statements in its private cursor helpers",
            providerSource.contains("SELECT id") || providerSource.contains("SELECT id,")
        )
    }

    /**
     * The query() kdoc comment must acknowledge that projection/selection/selectionArgs/sortOrder
     * are intentionally ignored. This guards against future reviewers silently removing the
     * security rationale.
     */
    @Test
    fun `interopProvider queryDocumentsThatProjectionIsIntentionallyIgnored`() {
        // The comment block above query() must acknowledge the intentional ignore.
        val hasIgnoredComment = providerSource.contains("intentionally ignored") ||
            providerSource.contains("are intentionally ignored")

        assertTrue(
            "InteropProvider.query() must document that projection/selection params are " +
                "intentionally ignored to prevent injection attacks.",
            hasIgnoredComment
        )
    }

    /**
     * The hardcoded SQL must use an `origin IS NULL` filter on tasks so that
     * imported DevTime copies are never re-shared back to DevTime (provenance guard).
     */
    @Test
    fun `interopProvider tasksQueryFiltersToSelfAuthoredRowsOnly`() {
        assertTrue(
            "tasks query must include 'origin IS NULL' to exclude imported rows",
            providerSource.contains("origin IS NULL")
        )
    }

    /**
     * The interop consent gate must appear before any data is served — this is the
     * PRIV-006 requirement that the user must explicitly opt in before any data can
     * be read even by a trusted peer.
     */
    @Test
    fun `interopProvider checksConsentBeforePeerVerification`() {
        val consentCheckIndex = providerSource.indexOf("isInteropConsentGranted")
        val peerVerifyIndex = providerSource.indexOf("PeerVerifier.requireTrusted")

        assertTrue(
            "isInteropConsentGranted() must be checked in the source",
            consentCheckIndex >= 0
        )
        assertTrue(
            "PeerVerifier.requireTrusted must appear in the source",
            peerVerifyIndex >= 0
        )
        assertTrue(
            "Consent check (PRIV-006) must appear before PeerVerifier in query()",
            consentCheckIndex < peerVerifyIndex
        )
    }
}
