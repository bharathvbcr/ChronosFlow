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
     * Apps allowed to read this provider, pinned to their signing certificate. Add DevTime's
     * RELEASE signing-cert SHA-256 to the set below before shipping release builds (release APKs
     * are signed with a different key than the debug cert).
     *
     * The debug keystore cert is included ONLY in debug builds: the shared Android debug keystore
     * (~/.android/debug.keystore) is the same across all developer workstations and emulators, so
     * any app claiming to be DEVTIME_PACKAGE and signed with it would pass in a release build.
     */
    val TRUSTED_PEERS = listOf(
        TrustedPeer(
            DEVTIME_PACKAGE,
            buildSet {
                if (BuildConfig.DEBUG) add(DEBUG_SIGNING_CERT_SHA256)
                // TODO(release): add("<DevTime release cert SHA-256 from apksigner verify --print-certs>")
            }
        ),
    )

    fun peerUri(path: String): Uri = Uri.parse("content://$PEER_AUTHORITY/$path")
}
