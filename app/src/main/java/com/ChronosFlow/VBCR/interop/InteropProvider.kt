package com.ChronosFlow.VBCR.interop

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.ChronosFlow.VBCR.core.data.ChronosDatabase
import com.ChronosFlow.VBCR.core.data.datastore.ChronosPreferencesDataSource
import com.ChronosFlow.VBCR.core.data.reading.ReadingMetadataFetchWorker
import com.ChronosFlow.VBCR.core.domain.model.AlarmDeliveryState
import com.ChronosFlow.VBCR.core.domain.model.AlarmReliability
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequest
import com.ChronosFlow.VBCR.core.domain.model.AlarmRequestType
import com.ChronosFlow.VBCR.core.domain.model.CaptureSource
import com.ChronosFlow.VBCR.core.domain.model.InboxItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingItem
import com.ChronosFlow.VBCR.core.domain.model.ReadingMetadataState
import com.ChronosFlow.VBCR.core.domain.model.ReadingUrls
import com.ChronosFlow.VBCR.core.domain.model.Task
import com.ChronosFlow.VBCR.core.domain.repository.InboxRepository
import com.ChronosFlow.VBCR.core.domain.repository.ReadingListRepository
import com.ChronosFlow.VBCR.core.domain.repository.TaskRepository
import com.ChronosFlow.VBCR.core.notifications.AlarmScheduler
import com.ChronosFlow.VBCR.core.notifications.ReadingReminderActionReceiver
import com.ChronosFlow.VBCR.core.ui.settings.ChronosUiSettingsKeys
import com.ChronosFlow.VBCR.core.ui.settings.readChronosUiBooleanSetting
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

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
        // Inbound handoff (write path) dependencies — all @Singleton in SingletonComponent.
        fun readingListRepository(): ReadingListRepository
        fun inboxRepository(): InboxRepository
        fun taskRepository(): TaskRepository
        fun alarmScheduler(): AlarmScheduler
    }

    private fun entryPoint(ctx: Context): InteropEntryPoint =
        EntryPointAccessors.fromApplication(ctx.applicationContext, InteropEntryPoint::class.java)

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
        matcher.addURI(authority, InteropContract.PATH_HANDOFF, CODE_HANDOFF)
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

    /**
     * Inbound handoff write path ([InteropContract.PATH_HANDOFF]) — the ONLY writable path. A
     * trusted peer (Curio) inserts a single row to create a reading-list item (with an optional
     * "remind me to read later" alarm), an inbox capture, or a task inside ChronosFlow.
     *
     * Authorization, in order: only the handoff path is writable; the user's inbound kill-switch
     * must be on; and the caller must be a pinned peer (signature-verified). Caller-supplied values
     * are read by hardcoded column name and passed only as Room-parameterized arguments — never
     * concatenated into SQL — so the read-path sanitization discipline holds here too.
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val ctx = context ?: return null
        if (matcher.match(uri) != CODE_HANDOFF) return null
        // Dedicated inbound gate — NOT isInteropConsentGranted, which governs sharing data OUT.
        if (!preferencesDataSource(ctx).isInboundHandoffAccepted()) {
            throw SecurityException("Interop: inbound handoffs are disabled")
        }
        PeerVerifier.requireTrusted(ctx, callingPackage)
        val v = values ?: return null
        val caller = callingPackage ?: return null
        val ep = entryPoint(ctx)
        return when (v.getAsString(InteropContract.HANDOFF_KIND)?.trim()?.lowercase()) {
            InteropContract.KIND_READING -> insertReading(ctx, ep, v, uri)
            InteropContract.KIND_INBOX -> insertInbox(ep, v, uri)
            InteropContract.KIND_TASK -> insertTask(ep, v, uri, caller)
            else -> null
        }
    }

    /** Saves a reading-list item (de-duping on URL) and schedules its reminder when one is given. */
    private fun insertReading(ctx: Context, ep: InteropEntryPoint, v: ContentValues, uri: Uri): Uri? {
        val rawUrl = v.getAsString(InteropContract.HANDOFF_URL) ?: v.getAsString(InteropContract.HANDOFF_TEXT)
        val url = ReadingUrls.firstUrlIn(rawUrl) ?: return null // reading items require a link
        val domain = ReadingUrls.domainOf(url)
        val title = v.getAsString(InteropContract.HANDOFF_TITLE)?.trim()?.takeIf { it.isNotEmpty() } ?: domain
        val notes = v.getAsString(InteropContract.HANDOFF_NOTES)?.trim()?.takeIf { it.isNotEmpty() }
        val remindAt = v.getAsLong(InteropContract.HANDOFF_REMINDER_AT)?.let { Instant.ofEpochMilli(it) }
        val autoFetch = ctx.readChronosUiBooleanSetting(
            ChronosUiSettingsKeys.KEY_READING_METADATA_AUTOFETCH, true
        )
        val now = Instant.now()
        val repo = ep.readingListRepository()
        val scheduler = ep.alarmScheduler()
        val id = runBlocking {
            val existing = repo.findByUrl(url)
            val item = existing?.copy(
                // Only upgrade a placeholder (domain) title; don't clobber a fetched one.
                title = if (existing.title == existing.domain && title != domain) title else existing.title,
                notes = notes ?: existing.notes,
                reminderAt = remindAt ?: existing.reminderAt,
                updatedAt = now
            ) ?: ReadingItem(
                id = UUID.randomUUID().toString(),
                url = url,
                title = title,
                domain = domain,
                notes = notes,
                metadataState = if (autoFetch) ReadingMetadataState.PENDING else ReadingMetadataState.SKIPPED,
                reminderAt = remindAt,
                addedAt = now,
                updatedAt = now
            )
            repo.save(item)
            if (existing == null && autoFetch) {
                WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(
                    ReadingMetadataFetchWorker.uniqueName(item.id),
                    ExistingWorkPolicy.REPLACE,
                    ReadingMetadataFetchWorker.request(item.id)
                )
            }
            if (remindAt != null) {
                // Same path as the in-app reading reminder: schedule the OS alarm (id prefixed
                // "reading:" so AlarmDeliveryCoordinator posts the reading notification) then
                // persist the column. scheduleAlarmRequest degrades to an inexact window when
                // exact-alarm permission is absent and no-ops if POST_NOTIFICATIONS is denied.
                scheduler.scheduleAlarmRequest(
                    AlarmRequest(
                        id = ReadingReminderActionReceiver.readingReminderRequestId(item.id),
                        type = AlarmRequestType.READING_REMINDER,
                        scheduledFor = remindAt,
                        title = item.title,
                        message = "Time to read this",
                        medicationPlanId = null,
                        blockId = null,
                        reliability = AlarmReliability.INEXACT,
                        deliveryState = AlarmDeliveryState.PENDING,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                repo.setReminder(item.id, remindAt)
            }
            item.id
        }
        return Uri.withAppendedPath(uri, id)
    }

    /** Drops a quick-capture note (or link) into the triage inbox. */
    private fun insertInbox(ep: InteropEntryPoint, v: ContentValues, uri: Uri): Uri? {
        val text = (v.getAsString(InteropContract.HANDOFF_TEXT) ?: v.getAsString(InteropContract.HANDOFF_URL))
            ?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val now = Instant.now()
        val id = UUID.randomUUID().toString()
        runBlocking {
            ep.inboxRepository().save(
                InboxItem(
                    id = id,
                    text = text,
                    url = ReadingUrls.firstUrlIn(text),
                    source = CaptureSource.SHARE,
                    createdAt = now
                )
            )
        }
        return Uri.withAppendedPath(uri, id)
    }

    /** Creates a follow-up task on today's plan. Tagged with the caller's package as [Task.origin]
     * so it is never re-shared OUT through the read cursors (which serve only origin IS NULL). */
    private fun insertTask(ep: InteropEntryPoint, v: ContentValues, uri: Uri, caller: String): Uri? {
        val title = v.getAsString(InteropContract.HANDOFF_TITLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: v.getAsString(InteropContract.HANDOFF_TEXT)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val body = v.getAsString(InteropContract.HANDOFF_TEXT)?.trim()?.takeIf { it.isNotEmpty() && it != title }
        val url = v.getAsString(InteropContract.HANDOFF_URL)?.trim()?.takeIf { it.isNotEmpty() }
        // Tasks have no url field; fold the link into the description so it isn't lost.
        val description = listOfNotNull(body, url).joinToString("\n").takeIf { it.isNotEmpty() }
        val now = Instant.now()
        val id = UUID.randomUUID().toString()
        runBlocking {
            ep.taskRepository().saveTask(
                Task(
                    id = id,
                    title = title,
                    description = description,
                    isCompleted = false,
                    priority = 1,
                    dueDate = null,
                    createdAt = now,
                    updatedAt = now,
                    targetDate = LocalDate.now(),
                    origin = caller,
                    externalId = id
                )
            )
        }
        return Uri.withAppendedPath(uri, id)
    }

    // Update/delete remain unsupported — handoffs are insert-only.
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
        const val CODE_HANDOFF = 8
    }
}
