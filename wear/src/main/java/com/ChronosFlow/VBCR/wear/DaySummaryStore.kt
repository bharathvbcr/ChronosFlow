package com.ChronosFlow.VBCR.wear

import android.content.Context
import com.ChronosFlow.VBCR.wear.model.WearBlock
import com.ChronosFlow.VBCR.wear.model.WearDaySummary
import com.ChronosFlow.VBCR.wear.model.WearHabit
import com.ChronosFlow.VBCR.wear.model.WearMed
import com.ChronosFlow.VBCR.wear.model.WearTask
import com.ChronosFlow.VBCR.wear.model.parseBlocks
import com.ChronosFlow.VBCR.wear.model.parseHabits
import com.ChronosFlow.VBCR.wear.model.parseMeds
import com.ChronosFlow.VBCR.wear.model.parseTasks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch-local cache of the day summary mirrored from the phone. [DaySummaryWearListenerService]
 * writes it; the tiles read the last-known snapshot on every render (so they keep working when
 * the phone is out of reach) and the Compose app collects [state] for live updates.
 *
 * Persistence is via SharedPreferences. The in-process [MutableStateFlow] is the live channel:
 * the listener service and the foreground app share one app process on Wear, so a write from the
 * service is observed by the app immediately. The flow is seeded from prefs on first access in
 * case the app starts cold before any mirror has arrived.
 */
object DaySummaryStore {

    // @Volatile + synchronized double-checked locking prevents two threads from each constructing
    // a separate MutableStateFlow and one silently losing writes to the other's discarded instance
    // (TS-001).
    @Volatile private var flow: MutableStateFlow<WearDaySummary>? = null

    fun state(context: Context): StateFlow<WearDaySummary> = ensureFlow(context).asStateFlow()

    fun read(context: Context): WearDaySummary = ensureFlow(context).value

    /**
     * Persists a summary just received from the phone, stamping [WearDaySummary.receivedAtMillis]
     * with [nowMillis] so the UI can later tell how stale it is. Distinct from [write], which the
     * app uses for optimistic local edits and which preserves the last sync stamp untouched.
     */
    fun writeSynced(
        context: Context,
        summary: WearDaySummary,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        write(context, summary.copy(receivedAtMillis = nowMillis))
    }

    fun write(context: Context, summary: WearDaySummary) {
        prefs(context).edit()
            .putString(KEY_NOW_TITLE, summary.nowTitle)
            .putInt(KEY_NOW_END_MINUTE, summary.nowEndMinute)
            .putString(KEY_NOW_BLOCK_ID, summary.nowBlockId)
            .putString(KEY_NOW_CATEGORY, summary.nowCategory)
            .putString(KEY_BLOCK_ENTRIES, summary.blocks.joinToString(LINE_SEP) { packBlock(it) })
            .putString(KEY_NEXT_TITLE, summary.nextTitle)
            .putInt(KEY_NEXT_START_MINUTE, summary.nextStartMinute)
            .putInt(KEY_NEXT_BREAK_START_MINUTE, summary.nextBreakStartMinute)
            .putString(KEY_NEXT_BREAK_TITLE, summary.nextBreakTitle.orEmpty())
            .putInt(KEY_OPEN_TASK_COUNT, summary.openTaskCount)
            .putString(KEY_TASK_ENTRIES, summary.tasks.joinToString(LINE_SEP) { packTask(it) })
            .putInt(KEY_HABITS_DONE, summary.habitsDone)
            .putInt(KEY_HABITS_TOTAL, summary.habitsTotal)
            .putString(KEY_HABIT_ENTRIES, summary.habits.joinToString(LINE_SEP) { packHabit(it) })
            .putInt(KEY_MEDS_DUE_COUNT, summary.medsDueCount)
            .putString(KEY_MED_ENTRIES, summary.meds.joinToString(LINE_SEP) { packMed(it) })
            .putString(KEY_DIGEST, summary.digest.orEmpty())
            .putLong(KEY_RECEIVED_AT, summary.receivedAtMillis)
            .apply()
        ensureFlow(context).value = summary
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        ensureFlow(context).value = WearDaySummary()
    }

    private fun ensureFlow(context: Context): MutableStateFlow<WearDaySummary> =
        flow ?: synchronized(this) {
            flow ?: MutableStateFlow(load(context)).also { flow = it }
        }

    private fun load(context: Context): WearDaySummary {
        val prefs = prefs(context)
        return WearDaySummary(
            nowTitle = prefs.getString(KEY_NOW_TITLE, null)?.takeIf { it.isNotBlank() },
            nowEndMinute = prefs.getInt(KEY_NOW_END_MINUTE, 0),
            nowBlockId = prefs.getString(KEY_NOW_BLOCK_ID, null)?.takeIf { it.isNotBlank() },
            nowCategory = prefs.getString(KEY_NOW_CATEGORY, null).orEmpty(),
            blocks = parseBlocks(prefs.getString(KEY_BLOCK_ENTRIES, null).toLines()),
            nextTitle = prefs.getString(KEY_NEXT_TITLE, null)?.takeIf { it.isNotBlank() },
            nextStartMinute = prefs.getInt(KEY_NEXT_START_MINUTE, 0),
            nextBreakStartMinute = prefs.getInt(KEY_NEXT_BREAK_START_MINUTE, 0),
            nextBreakTitle = prefs.getString(KEY_NEXT_BREAK_TITLE, null)?.takeIf { it.isNotBlank() },
            openTaskCount = prefs.getInt(KEY_OPEN_TASK_COUNT, 0),
            tasks = parseTasks(prefs.getString(KEY_TASK_ENTRIES, null).toLines()),
            habitsDone = prefs.getInt(KEY_HABITS_DONE, 0),
            habitsTotal = prefs.getInt(KEY_HABITS_TOTAL, 0),
            habits = parseHabits(prefs.getString(KEY_HABIT_ENTRIES, null).toLines()),
            medsDueCount = prefs.getInt(KEY_MEDS_DUE_COUNT, 0),
            meds = parseMeds(prefs.getString(KEY_MED_ENTRIES, null).toLines()),
            digest = prefs.getString(KEY_DIGEST, null)?.takeIf { it.isNotBlank() },
            receivedAtMillis = prefs.getLong(KEY_RECEIVED_AT, 0L)
        )
    }

    // Persistence re-uses the on-wire packing so read() can lean on the shared parse helpers.
    private fun packBlock(b: WearBlock) = "${b.startMinute}$FIELD_SEP${b.endMinute}"
    private fun packTask(t: WearTask) = "${t.id}$FIELD_SEP${t.title}"
    private fun packHabit(h: WearHabit) =
        "${h.id}$FIELD_SEP${if (h.done) 1 else 0}$FIELD_SEP${h.streak}$FIELD_SEP${h.title}"
    private fun packMed(m: WearMed) =
        "${m.id}$FIELD_SEP${if (m.taken) 1 else 0}$FIELD_SEP${m.reminderMinute}$FIELD_SEP${m.doseLabel}$FIELD_SEP${m.name}"

    private fun String?.toLines(): List<String> =
        this?.split(LINE_SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val FIELD_SEP = com.ChronosFlow.VBCR.core.domain.wear.WearDaySummaryContract.FIELD_SEP
    private const val PREFS_NAME = "chronos_day_summary"
    private const val LINE_SEP = "\n"
    private const val KEY_NOW_TITLE = "now_title"
    private const val KEY_NOW_END_MINUTE = "now_end_minute"
    private const val KEY_NOW_BLOCK_ID = "now_block_id"
    private const val KEY_NOW_CATEGORY = "now_category"
    private const val KEY_BLOCK_ENTRIES = "block_entries"
    private const val KEY_NEXT_TITLE = "next_title"
    private const val KEY_NEXT_START_MINUTE = "next_start_minute"
    private const val KEY_NEXT_BREAK_START_MINUTE = "next_break_start_minute"
    private const val KEY_NEXT_BREAK_TITLE = "next_break_title"
    private const val KEY_OPEN_TASK_COUNT = "open_task_count"
    private const val KEY_TASK_ENTRIES = "task_entries"
    private const val KEY_HABITS_DONE = "habits_done"
    private const val KEY_HABITS_TOTAL = "habits_total"
    private const val KEY_HABIT_ENTRIES = "habit_entries"
    private const val KEY_MEDS_DUE_COUNT = "meds_due_count"
    private const val KEY_MED_ENTRIES = "med_entries"
    private const val KEY_DIGEST = "digest"
    private const val KEY_RECEIVED_AT = "received_at"
}
