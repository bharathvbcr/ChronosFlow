package com.ChronosFlow.VBCR.feature.daydial.model

/**
 * Splits a block's planned duration into alternating focus/break phases.
 *
 * Picks a work-interval length and a break length, then divides [totalMinutes]
 * into full `work + break` cycles. A 60-minute block at 25/5 becomes
 * `25 focus · 5 break · 25 focus · 5 break`. Any remainder shorter than a full
 * cycle is added as a final focus interval (no trailing break).
 *
 * A non-positive [breakMinutes] (or a block too short for even one work
 * interval plus a break) yields a single flat focus phase, which the rest of
 * the system treats as the legacy "no splits" behavior.
 */
object FocusPhasePlanner {

    /** Common work-interval choices offered in the UI (minutes). */
    val WORK_PRESETS = listOf(15, 20, 25, 30, 45, 50)

    /** Common break-length choices offered in the UI (minutes). */
    val BREAK_PRESETS = listOf(5, 10, 15)

    fun plan(
        totalMinutes: Int,
        workMinutes: Int,
        breakMinutes: Int
    ): List<FocusPhase> {
        val total = totalMinutes.coerceAtLeast(1)
        val work = workMinutes.coerceAtLeast(1)
        val brk = breakMinutes.coerceAtLeast(0)

        // No breaks requested, or the block can't fit a work interval plus a
        // break: keep it a single flat block.
        if (brk == 0 || total < work + brk) {
            return listOf(FocusPhase(FocusPhaseKind.FOCUS, total))
        }

        val cycle = work + brk
        val fullCycles = total / cycle
        val remainder = total % cycle

        val phases = buildList {
            repeat(fullCycles) {
                add(FocusPhase(FocusPhaseKind.FOCUS, work))
                add(FocusPhase(FocusPhaseKind.BREAK, brk))
            }
            if (remainder > 0) {
                // Tail too small for another cycle: spend it all on focus.
                add(FocusPhase(FocusPhaseKind.FOCUS, remainder))
            }
        }
        return phases
    }

    /** Total focus minutes across a plan (excludes breaks). */
    fun focusMinutes(phases: List<FocusPhase>): Int =
        phases.filter { it.kind == FocusPhaseKind.FOCUS }.sumOf { it.durationMinutes }
}
