package com.chronosflow.wear

import android.content.Context
import com.chronosflow.wear.model.WearBlock
import com.chronosflow.wear.model.WearDaySummary
import com.chronosflow.wear.model.WearHabit
import com.chronosflow.wear.model.WearMed
import com.chronosflow.wear.model.WearTask
import com.chronosflow.wear.model.parseBlocks
import com.chronosflow.wear.model.parseHabits
import com.chronosflow.wear.model.parseMeds
import com.chronosflow.wear.model.parseTasks
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

    private var flow: MutableStateFlow<WearDaySummary>? = null

    fun state(context: Context): StateFlow<WearDaySummary> = ensureFlow(context).asStateFlow()

    fun read(context: Context): WearDaySummary = ensureFlow(context).value

    fun write(context: Context, summary: WearDaySummary) {
        prefs(context).edit()
            .putString(KEY_NOW_TITLE, summary.nowTitle)
            .putInt(KEY_NOW_END_MINUTE, summary.nowEndMinute)
            .putString(KEY_BLOCK_ENTRIES, summary.blocks.joinToString(LINE_SEP) { packBlock(it) })
            .putString(KEY_NEXT_TITLE, summary.nextTitle)
            .putInt(KEY_NEXT_START_MINUTE, summary.nextStartMinute)
            .putInt(KEY_OPEN_TASK_COUNT, summary.openTaskCount)
            .putString(KEY_TASK_ENTRIES, summary.tasks.joinToString(LINE_SEP) { packTask(it) })
            .putInt(KEY_HABITS_DONE, summary.habitsDone)
            .putInt(KEY_HABITS_TOTAL, summary.habitsTotal)
            .putString(KEY_HABIT_ENTRIES, summary.habits.joinToString(LINE_SEP) { packHabit(it) })
            .putInt(KEY_MEDS_DUE_COUNT, summary.medsDueCount)
            .putString(KEY_MED_ENTRIES, summary.meds.joinToString(LINE_SEP) { packMed(it) })
            .putString(KEY_DIGEST, summary.digest.orEmpty())
            .apply()
        ensureFlow(context).value = summary
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        ensureFlow(context).value = WearDaySummary()
    }

    private fun ensureFlow(context: Context): MutableStateFlow<WearDaySummary> =
        flow ?: MutableStateFlow(load(context)).also { flow = it }

    private fun load(context: Context): WearDaySummary {
        val prefs = prefs(context)
        return WearDaySummary(
            nowTitle = prefs.getString(KEY_NOW_TITLE, null)?.takeIf { it.isNotBlank() },
            nowEndMinute = prefs.getInt(KEY_NOW_END_MINUTE, 0),
            blocks = parseBlocks(prefs.getString(KEY_BLOCK_ENTRIES, null).toLines()),
            nextTitle = prefs.getString(KEY_NEXT_TITLE, null)?.takeIf { it.isNotBlank() },
            nextStartMinute = prefs.getInt(KEY_NEXT_START_MINUTE, 0),
            openTaskCount = prefs.getInt(KEY_OPEN_TASK_COUNT, 0),
            tasks = parseTasks(prefs.getString(KEY_TASK_ENTRIES, null).toLines()),
            habitsDone = prefs.getInt(KEY_HABITS_DONE, 0),
            habitsTotal = prefs.getInt(KEY_HABITS_TOTAL, 0),
            habits = parseHabits(prefs.getString(KEY_HABIT_ENTRIES, null).toLines()),
            medsDueCount = prefs.getInt(KEY_MEDS_DUE_COUNT, 0),
            meds = parseMeds(prefs.getString(KEY_MED_ENTRIES, null).toLines()),
            digest = prefs.getString(KEY_DIGEST, null)?.takeIf { it.isNotBlank() }
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

    private val FIELD_SEP = com.chronosflow.core.domain.wear.WearDaySummaryContract.FIELD_SEP
    private const val PREFS_NAME = "chronos_day_summary"
    private const val LINE_SEP = "\n"
    private const val KEY_NOW_TITLE = "now_title"
    private const val KEY_NOW_END_MINUTE = "now_end_minute"
    private const val KEY_BLOCK_ENTRIES = "block_entries"
    private const val KEY_NEXT_TITLE = "next_title"
    private const val KEY_NEXT_START_MINUTE = "next_start_minute"
    private const val KEY_OPEN_TASK_COUNT = "open_task_count"
    private const val KEY_TASK_ENTRIES = "task_entries"
    private const val KEY_HABITS_DONE = "habits_done"
    private const val KEY_HABITS_TOTAL = "habits_total"
    private const val KEY_HABIT_ENTRIES = "habit_entries"
    private const val KEY_MEDS_DUE_COUNT = "meds_due_count"
    private const val KEY_MED_ENTRIES = "med_entries"
    private const val KEY_DIGEST = "digest"
}
