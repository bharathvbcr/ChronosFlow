package com.ChronosFlow.VBCR.interop

import android.net.Uri
import com.ChronosFlow.VBCR.BuildConfig

/**
 * Cross-app sharing contract between ChronosFlow and DevTime (Meridian).
 *
 * Both apps expose a read-only [InteropProvider] under `content://<applicationId>.share/<path>`.
 * Each app can query the *other* app's provider and import copies into its own database. The
 * projection below is a small normalized schema so neither side needs to know the other's tables.
 *
 * This file is intentionally mirrored (byte-for-byte equivalent, minus [SELF_PACKAGE]/peer) in the
 * DevTime repo — there is no shared Gradle module between the two projects.
 */
object InteropContract {
    const val DEVTIME_PACKAGE = "com.Meridian.VBCR"
    const val CHRONOSFLOW_PACKAGE = "com.ChronosFlow.VBCR"

    /**
     * Curio — the research-index app that hands items off INTO ChronosFlow (a saved bookmark to the
     * reading list with a "remind me to read later" time, the quick-capture inbox, or a follow-up
     * task). Unlike DevTime, Curio does not expose a peer provider we read; it is a write-only
     * client of our [PATH_HANDOFF] path. See [InteropProvider.insert].
     */
    const val CURIO_PACKAGE = "com.example"

    /** This app. */
    const val SELF_PACKAGE = CHRONOSFLOW_PACKAGE
    /** The app we share with. */
    const val DEVTIME = DEVTIME_PACKAGE

    /** Authority of the peer (DevTime) provider we read from. */
    const val PEER_AUTHORITY = "$DEVTIME_PACKAGE.share"

    // Paths (one per shareable data type).
    const val PATH_TASKS = "tasks"
    const val PATH_EVENTS = "events"
    const val PATH_ZONES = "zones"
    const val PATH_PEOPLE = "people"
    const val PATH_HABITS = "habits"
    const val PATH_MEDICATIONS = "medications"
    const val PATH_GOALS = "goals"

    // ── Inbound handoff (write path) ─────────────────────────────────────────
    // A trusted peer (Curio) inserts rows here to create a reading-list item, an inbox capture, or
    // a task inside ChronosFlow. This is the only writable path on the provider; every other path
    // is read-only. Handled by InteropProvider.insert(), gated by PeerVerifier + the inbound-accept
    // preference. Inbound rows are tagged with the caller's package as their `origin` so they are
    // never re-shared OUT through the read cursors (which only serve `origin IS NULL`).
    const val PATH_HANDOFF = "handoff"

    /** Discriminator column on a [PATH_HANDOFF] insert: one of [KIND_READING], [KIND_INBOX], [KIND_TASK]. */
    const val HANDOFF_KIND = "kind"
    /** http/https link (required for [KIND_READING], optional otherwise). */
    const val HANDOFF_URL = "url"
    /** Display title for the created item. */
    const val HANDOFF_TITLE = "title"
    /** Free text body (used as the inbox note or the task description). */
    const val HANDOFF_TEXT = "text"
    /** Epoch-millis reminder time for a [KIND_READING] item; omit/null for no reminder. */
    const val HANDOFF_REMINDER_AT = "reminder_at_epoch_ms"
    /** Optional personal note carried onto the created item. */
    const val HANDOFF_NOTES = "notes"

    const val KIND_READING = "reading"
    const val KIND_INBOX = "inbox"
    const val KIND_TASK = "task"

    // Normalized columns. Cursor rows MUST be added in exactly the array order below.
    val TASK_COLUMNS = arrayOf(
        "external_id", "title", "notes", "due_at", "is_completed", "priority", "timezone", "updated_at",
    )
    val EVENT_COLUMNS = arrayOf(
        "external_id", "title", "description", "start_at", "end_at", "timezone", "is_all_day", "location", "updated_at",
    )
    val ZONE_COLUMNS = arrayOf("external_id", "zone_id", "display_name", "is_home")
    val PEOPLE_COLUMNS = arrayOf(
        "external_id", "name", "zone_id", "location_name", "work_start_hour", "work_end_hour",
    )
    val HABIT_COLUMNS = arrayOf("external_id", "title", "cadence", "is_active", "streak_count")
    val MEDICATION_COLUMNS = arrayOf("external_id", "name", "dosage", "unit", "is_active")
    val GOAL_COLUMNS = arrayOf("external_id", "title", "description", "category", "is_completed")

    /**
     * A peer we accept queries from. [certSha256] is the set of accepted signing-cert SHA-256
     * hashes (hex; colons and case are ignored). A non-empty set is enforced: only an app signed
     * by one of these certs may read our data, so an impostor reusing the package name is rejected.
     * An empty set falls back to trust-on-first-use (package name only) — do not ship that.
     */
    data class TrustedPeer(val packageName: String, val certSha256: Set<String>)

    /**
     * Both apps' debug builds are signed with the shared Android debug keystore
     * (`~/.android/debug.keystore`), so they share one signing certificate. Extracted with
     * `apksigner verify --print-certs app-debug.apk`.
     */
    const val DEBUG_SIGNING_CERT_SHA256 =
        "CB3AD2AF1E28C9C9F0C2EEB0474D64A096E6A0DEAE2CA99660A3A431A643CC61"

    /**
     * DevTime's RELEASE signing-cert SHA-256. Must be filled before shipping release builds.
     * Obtain with: apksigner verify --print-certs devtime-release.apk
     * Leave empty only during development — the init{} block below will catch any release build
     * that ships with an empty pin.
     */
    const val DEVTIME_RELEASE_CERT_SHA256 = ""

    /**
     * Curio's DEBUG signing-cert SHA-256. Curio does NOT use the shared `~/.android/debug.keystore`;
     * it signs debug builds with its own checked-in `debug.keystore`, so its debug cert differs from
     * [DEBUG_SIGNING_CERT_SHA256]. Obtained with:
     *   keytool -list -v -keystore curio/debug.keystore -alias androiddebugkey -storepass android
     */
    const val CURIO_DEBUG_CERT_SHA256 =
        "B4CE786F8CDC77C41CC0640566D425D81C83256C4C77C775A727627BFA0A5CDD"

    /**
     * Curio's RELEASE signing-cert SHA-256. Must be filled before shipping release builds that
     * accept Curio handoffs. Obtain with: apksigner verify --print-certs curio-release.apk
     */
    const val CURIO_RELEASE_CERT_SHA256 = ""

    /**
     * Apps allowed to talk to this provider, pinned to their signing certificate. Add each peer's
     * RELEASE signing-cert SHA-256 to its set before shipping release builds (release APKs are
     * signed with a different key than the debug cert).
     *
     * DevTime is a read peer (it queries our shareable data). Curio is a write-only handoff client
     * (it inserts into [PATH_HANDOFF]). PeerVerifier enforces the pinned cert for both directions.
     *
     * The debug keystore cert is included ONLY in debug builds: a debug keystore is not a secret, so
     * any app claiming a peer's package name and signed with it would pass in a release build.
     */
    val TRUSTED_PEERS = listOf(
        TrustedPeer(
            DEVTIME_PACKAGE,
            buildSet {
                if (BuildConfig.DEBUG) add(DEBUG_SIGNING_CERT_SHA256)
                if (DEVTIME_RELEASE_CERT_SHA256.isNotEmpty()) add(DEVTIME_RELEASE_CERT_SHA256)
            }
        ),
        TrustedPeer(
            CURIO_PACKAGE,
            buildSet {
                if (BuildConfig.DEBUG) add(CURIO_DEBUG_CERT_SHA256)
                if (CURIO_RELEASE_CERT_SHA256.isNotEmpty()) add(CURIO_RELEASE_CERT_SHA256)
            }
        ),
    )

    init {
        // Warn loudly in release builds when the DevTime cert is not yet pinned. We use Log
        // rather than check() so a release build signed before the DevTime cert is available
        // doesn't hard-crash at class-init time — PeerVerifier.requireTrusted() already rejects
        // callers when certSha256 is empty, so security is still enforced.
        if (!BuildConfig.DEBUG) {
            TRUSTED_PEERS.filter { it.certSha256.isEmpty() }.forEach { peer ->
                android.util.Log.e(
                    "InteropContract",
                    "SECURITY: Release build has no signing cert pinned for ${peer.packageName}. " +
                        "Run: apksigner verify --print-certs <peer-release.apk> " +
                        "and add the SHA-256 to the matching *_RELEASE_CERT_SHA256 before shipping."
                )
            }
        }
    }

    fun peerUri(path: String): Uri = Uri.parse("content://$PEER_AUTHORITY/$path")
}
