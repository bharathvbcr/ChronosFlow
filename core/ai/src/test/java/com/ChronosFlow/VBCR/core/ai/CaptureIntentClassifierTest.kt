package com.ChronosFlow.VBCR.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureIntentClassifierTest {
    @Test
    fun `classifies one off call capture as task first`() {
        val suggestions = CaptureIntentClassifier.classify("call mom tomorrow")

        assertEquals(CaptureIntentType.TASK, suggestions.first().type)
    }

    @Test
    fun `classifies vitamin dose capture as medication first`() {
        val suggestions = CaptureIntentClassifier.classify("vitamin d 1000 iu morning")

        assertEquals(CaptureIntentType.MEDICATION, suggestions.first().type)
        assertTrue(suggestions.first().score > suggestions.last().score)
    }

    @Test
    fun `classifies medication captures with cadence as medication first`() {
        assertEquals(
            CaptureIntentType.MEDICATION,
            CaptureIntentClassifier.classify("vitamin d daily").first().type
        )
        assertEquals(
            CaptureIntentType.MEDICATION,
            CaptureIntentClassifier.classify("daily aspirin").first().type
        )
        assertEquals(
            CaptureIntentType.MEDICATION,
            CaptureIntentClassifier.classify("melatonin nightly").first().type
        )
    }

    @Test
    fun `classifies gym cadence capture as habit first`() {
        val suggestions = CaptureIntentClassifier.classify("gym 3x week evening")

        assertEquals(CaptureIntentType.HABIT, suggestions.first().type)
    }

    @Test
    fun `classifies protected focus captures as focus first`() {
        assertEquals(
            CaptureIntentType.FOCUS,
            CaptureIntentClassifier.classify("focus 45 minutes on launch brief").first().type
        )
        assertEquals(
            CaptureIntentType.FOCUS,
            CaptureIntentClassifier.classify("deep work 90m design review").first().type
        )
    }

    @Test
    fun `classifies natural recurrence captures as habits first`() {
        assertEquals(
            CaptureIntentType.HABIT,
            CaptureIntentClassifier.classify("stretch every weekday morning").first().type
        )
        assertEquals(
            CaptureIntentType.HABIT,
            CaptureIntentClassifier.classify("strength training three times per week").first().type
        )
    }

    @Test
    fun `classifies one off chore captures as tasks not habits`() {
        assertEquals(
            CaptureIntentType.TASK,
            CaptureIntentClassifier.classify("water plants tomorrow").first().type
        )
        assertEquals(
            CaptureIntentType.TASK,
            CaptureIntentClassifier.classify("walk dog tonight").first().type
        )
    }

    @Test
    fun `classifies speech style communication captures as tasks first`() {
        assertEquals(
            CaptureIntentType.TASK,
            CaptureIntentClassifier.classify("message Sarah tomorrow").first().type
        )
        assertEquals(
            CaptureIntentType.TASK,
            CaptureIntentClassifier.classify("ask Jordan about the invoice").first().type
        )
        assertEquals(
            CaptureIntentType.TASK,
            CaptureIntentClassifier.classify("follow up with doctor Friday").first().type
        )
    }

    @Test
    fun `classifies common medication and habit captures without substring false positives`() {
        assertEquals(
            CaptureIntentType.MEDICATION,
            CaptureIntentClassifier.classify("metformin 500 mg with dinner").first().type
        )
        assertEquals(
            CaptureIntentType.MEDICATION,
            CaptureIntentClassifier.classify("aspirin morning").first().type
        )
        assertEquals(
            CaptureIntentType.MEDICATION,
            CaptureIntentClassifier.classify("melatonin before bed").first().type
        )
        assertEquals(
            CaptureIntentType.HABIT,
            CaptureIntentClassifier.classify("floss nightly").first().type
        )
    }

    @Test
    fun `does not treat weak take wording as medication without dose context`() {
        val suggestions = CaptureIntentClassifier.classify("take package to post office tomorrow")

        assertEquals(CaptureIntentType.TASK, suggestions.first().type)
    }

    @Test
    fun `classifies unknown input as task with minimum score`() {
        val suggestions = CaptureIntentClassifier.classify("xyzabc123")
        // All scores should be 0, but task should be adjusted to 1
        val taskSuggestion = suggestions.find { it.type == CaptureIntentType.TASK }
        assertEquals(1, taskSuggestion?.score)
    }
}
