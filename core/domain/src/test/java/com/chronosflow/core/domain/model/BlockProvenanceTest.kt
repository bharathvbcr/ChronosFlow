package com.chronosflow.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockProvenanceTest {

    @Test
    fun `fromSource maps primary provenance types`() {
        assertEquals(BlockProvenance.AI_SUGGESTED, BlockProvenance.fromSource("AI"))
        assertEquals(BlockProvenance.AI_SUGGESTED, BlockProvenance.fromSource("AI_SUGGESTED"))
        assertEquals(BlockProvenance.CALENDAR_IMPORTED, BlockProvenance.fromSource("calendar"))
        assertEquals(BlockProvenance.CALENDAR_IMPORTED, BlockProvenance.fromSource("calendar_imported"))
        assertEquals(BlockProvenance.TASK_CONVERTED, BlockProvenance.fromSource("TASK"))
        assertEquals(BlockProvenance.TASK_CONVERTED, BlockProvenance.fromSource("task_converted"))
        assertEquals(BlockProvenance.SYSTEM_GENERATED, BlockProvenance.fromSource("system_generated"))
    }

    @Test
    fun `fromSource defaults to user created when unknown`() {
        assertEquals(BlockProvenance.USER_CREATED, BlockProvenance.fromSource(null))
        assertEquals(BlockProvenance.USER_CREATED, BlockProvenance.fromSource(""))
        assertEquals(BlockProvenance.USER_CREATED, BlockProvenance.fromSource("manual"))
    }
}

