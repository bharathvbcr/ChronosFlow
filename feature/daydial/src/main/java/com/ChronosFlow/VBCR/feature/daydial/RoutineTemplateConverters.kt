package com.ChronosFlow.VBCR.feature.daydial

import com.ChronosFlow.VBCR.core.domain.model.Routine
import com.ChronosFlow.VBCR.core.domain.model.RoutineStep
import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlockBlueprint
import com.ChronosFlow.VBCR.feature.daydial.model.TemplateBlueprint
import com.ChronosFlow.VBCR.feature.daydial.model.TimeBlockUiModel
import java.util.UUID

/**
 * Bridges the [TemplateBlueprint] view model and the DB-backed [Routine] domain model.
 *
 * Anchor semantics: routines saved from templates use a fixed anchor of minute 0, i.e.
 * [RoutineStep.offsetMinute] stores the block's absolute minute-of-day
 * ([TemplateBlockBlueprint.startMinute]) directly. Applying such a routine via
 * [com.ChronosFlow.VBCR.core.domain.usecase.ApplyRoutineToDateUseCase] must therefore pass
 * [ROUTINE_TEMPLATE_ANCHOR_MINUTE] (0) as `startMinuteOfDay`, which makes the
 * template -> routine -> template round trip lossless.
 */

/** Start anchor for template-backed routines: offsets are absolute minutes-of-day. */
internal const val ROUTINE_TEMPLATE_ANCHOR_MINUTE: Int = 0

internal fun TemplateBlueprint.toRoutine(): Routine = Routine(
    id = id,
    title = name,
    isActive = true,
    lastCompletedDate = null,
    steps = blocks
        .sortedBy { it.startMinute }
        .map { block ->
            RoutineStep(
                id = UUID.randomUUID().toString(),
                title = block.title,
                category = block.category.ifBlank { RoutineStep.DEFAULT_CATEGORY },
                offsetMinute = block.startMinute,
                durationMinutes = block.durationMinutes,
                energyLevel = RoutineStep.DEFAULT_ENERGY_LEVEL
            )
        }
)

internal fun Routine.toTemplateBlueprint(): TemplateBlueprint =
    TemplateBlueprint(
        id = id,
        name = title,
        blocks = steps
            .sortedBy { it.offsetMinute }
            .map { step ->
                TemplateBlockBlueprint(
                    title = step.title,
                    startMinute = normalizeRoutineMinute(ROUTINE_TEMPLATE_ANCHOR_MINUTE + step.offsetMinute),
                    durationMinutes = step.durationMinutes,
                    category = step.category
                )
            }
    )

/**
 * Derives per-routine completion from the selected day's blocks: every block carrying a
 * [TimeBlockUiModel.routineId] counts toward that routine's total, and blocks with a recorded
 * [TimeBlockUiModel.actualEndMinuteOfDay] count as done.
 */
internal fun deriveRoutineCompletions(blocks: List<TimeBlockUiModel>): Map<String, RoutineCompletionSummary> =
    blocks
        .filter { it.routineId != null }
        .groupBy { it.routineId!! }
        .mapValues { (_, routineBlocks) ->
            RoutineCompletionSummary(
                doneCount = routineBlocks.count { it.actualEndMinuteOfDay != null },
                totalCount = routineBlocks.size
            )
        }

private fun normalizeRoutineMinute(minute: Int): Int = ((minute % 1440) + 1440) % 1440
