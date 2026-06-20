package com.ChronosFlow.VBCR.interop

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Read-only provider exposing ChronosFlow's shareable data to DevTime. Authority is
 * `<applicationId>.share`. Every query is gated by [PeerVerifier]; only self-authored rows
 * (origin IS NULL) are served so imported copies are never re-shared.
 *
 * ChronosFlow owns tasks, events, habits, medications and goals. The zones/people paths return
 * empty cursors (DevTime owns those). The DB is SQLCipher-encrypted; [ChronosDatabase]'s open
 * helper decrypts transparently, so raw SQL here reads plaintext rows.
 */
class InteropProvider : ContentProvider() {

    private val matcher = UriMatcher(UriMatcher.NO_MATCH)

    /** ContentProvider can't use constructor injection — reach the Hilt graph on demand. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface InteropEntryPoint {
        fun chronosDatabase(): ChronosDatabase
        fun chronosPreferencesDataSource(): ChronosPreferencesDataSource
    }

    private fun database(ctx: Context): ChronosDatabase =
        EntryPointAccessors.fromApplication(ctx.applicationContext, InteropEntryPoint::class.java)
            .chronosDatabase()

    private fun preferencesDataSource(ctx: Context): ChronosPreferencesDataSource =
        EntryPointAccessors.fromApplication(ctx.applicationContext, InteropEntryPoint::class.java)
            .chronosPreferencesDataSource()

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        val authority = "${ctx.packageName}.share"
        matcher.addURI(authority, InteropContract.PATH_TASKS, CODE_TASKS)
        matcher.addURI(authority, InteropContract.PATH_EVENTS, CODE_EVENTS)
        matcher.addURI(authority, InteropContract.PATH_HABITS, CODE_HABITS)
        matcher.addURI(authority, InteropContract.PATH_MEDICATIONS, CODE_MEDS)
        matcher.addURI(authority, InteropContract.PATH_GOALS, CODE_GOALS)
        matcher.addURI(authority, InteropContract.PATH_ZONES, CODE_ZONES)
        matcher.addURI(authority, InteropContract.PATH_PEOPLE, CODE_PEOPLE)
        return true
    }

    // projection, selection, selectionArgs, and sortOrder are intentionally ignored — all queries
    // use hardcoded SQL. This is the correct design for a read-only interop provider: it prevents
    // any possibility of column/row injection even if a caller supplies crafted arguments.
    // Do NOT wire caller-supplied arguments into queries in future changes without also adding a
    // platform android:permission attribute to the <provider> declaration as a safety net.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        // PRIV-006: Refuse all queries until the user has explicitly consented to interop data
        // sharing. This is the server-side gate — it fires before PeerVerifier so even a trusted
        // peer cannot read any ChronosFlow data without the user's explicit opt-in.
        if (!preferencesDataSource(ctx).isInteropConsentGranted()) return null
        PeerVerifier.requireTrusted(ctx, callingPackage)
        val db = database(ctx).openHelper.readableDatabase
        return when (matcher.match(uri)) {
            CODE_TASKS -> tasksCursor(db)
            CODE_EVENTS -> eventsCursor(db)
            CODE_HABITS -> habitsCursor(db)
            CODE_MEDS -> medicationsCursor(db, ctx)
            CODE_GOALS -> goalsCursor(db)
            // Types ChronosFlow does not own — empty cursors keep peers' code uniform.
            CODE_ZONES -> MatrixCursor(InteropContract.ZONE_COLUMNS)
            CODE_PEOPLE -> MatrixCursor(InteropContract.PEOPLE_COLUMNS)
            else -> null
        }
    }

    private fun tasksCursor(db: SupportSQLiteDatabase): Cursor {
        val out = MatrixCursor(InteropContract.TASK_COLUMNS)
        db.query(
            "SELECT id, title, description, dueDate, isCompleted, priority, updatedAt " +
                "FROM tasks WHERE origin IS NULL ORDER BY updatedAt DESC",
        ).use { c ->
            while (c.moveToNext()) {
                out.addRow(
                    arrayOf<Any?>(
                        c.getString(0),                                   // external_id
                        c.getString(1),                                   // title
                        if (c.isNull(2)) null else c.getString(2),        // notes (description)
                        if (c.isNull(3)) null else c.getLong(3),          // due_at (epoch ms)
                        c.getInt(4),                                      // is_completed
                        if (c.isNull(5)) null else c.getInt(5),           // priority
                        null,                                             // timezone (tasks have none)
                        if (c.isNull(6)) null else c.getLong(6),          // updated_at
                    ),
                )
            }
        }
        return out
    }

    private fun eventsCursor(db: SupportSQLiteDatabase): Cursor {
        val out = MatrixCursor(InteropContract.EVENT_COLUMNS)
        // calendar_events is a disposable snapshot (regenerated on every calendar sync) with no
        // per-row modification timestamp. Use query-time as updated_at so DevTime's incremental
        // sync sees events as "fresh" after each ChronosFlow calendar re-import rather than
        // treating startAt (immutable) as "last modified" and silently dropping title/description edits.
        val queryTimeMs = System.currentTimeMillis()
        db.query(
            "SELECT id, startAt, title, description, endAt, timezone, location, isAllDay " +
                "FROM calendar_events ORDER BY startAt ASC",
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val startAt = c.getLong(1)
                out.addRow(
                    arrayOf<Any?>(
                        "$id:$startAt",                              // external_id (unique per instance)
                        c.getString(2),                             // title
                        if (c.isNull(3)) null else c.getString(3),  // description
                        startAt,                                    // start_at
                        c.getLong(4),                               // end_at
                        if (c.isNull(5)) null else c.getString(5),  // timezone
                        c.getInt(7),                                // is_all_day
                        if (c.isNull(6)) null else c.getString(6),  // location
                        queryTimeMs,                                // updated_at (proxy: time of this query)
                    ),
                )
            }
        }
        return out
    }

    private fun habitsCursor(db: SupportSQLiteDatabase): Cursor {
        val out = MatrixCursor(InteropContract.HABIT_COLUMNS)
        db.query("SELECT id, title, cadence, isActive, streakCount FROM habits").use { c ->
            while (c.moveToNext()) {
                out.addRow(
                    arrayOf<Any?>(
                        c.getString(0), c.getString(1), c.getString(2), c.getInt(3), c.getInt(4),
                    ),
                )
            }
        }
        return out
    }

    private fun medicationsCursor(db: SupportSQLiteDatabase, ctx: Context): Cursor {
        val sharingEnabled = preferencesDataSource(ctx).isMedicationSharingEnabled()
        if (!sharingEnabled) return MatrixCursor(InteropContract.MEDICATION_COLUMNS)

        val out = MatrixCursor(InteropContract.MEDICATION_COLUMNS)
        db.query("SELECT id, name, dosage, unit, isActive FROM medication_plans").use { c ->
            while (c.moveToNext()) {
                out.addRow(
                    arrayOf<Any?>(
                        c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4),
                    ),
                )
            }
        }
        return out
    }

    private fun goalsCursor(db: SupportSQLiteDatabase): Cursor {
        val out = MatrixCursor(InteropContract.GOAL_COLUMNS)
        db.query("SELECT id, title, description, category, isCompleted FROM goals").use { c ->
            while (c.moveToNext()) {
                out.addRow(
                    arrayOf<Any?>(
                        c.getString(0),
                        c.getString(1),
                        if (c.isNull(2)) null else c.getString(2),
                        c.getString(3),
                        c.getInt(4),
                    ),
                )
            }
        }
        return out
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        UriMatcher.NO_MATCH -> null
        else -> "vnd.android.cursor.dir/vnd.${InteropContract.SELF_PACKAGE}.interop"
    }

    // Read-only provider: mutations are not supported.
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private companion object {
        const val CODE_TASKS = 1
        const val CODE_EVENTS = 2
        const val CODE_HABITS = 3
        const val CODE_MEDS = 4
        const val CODE_GOALS = 5
        const val CODE_ZONES = 6
        const val CODE_PEOPLE = 7
    }
}
