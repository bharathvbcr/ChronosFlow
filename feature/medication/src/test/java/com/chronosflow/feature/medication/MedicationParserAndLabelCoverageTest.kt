package com.chronosflow.feature.medication

import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.chronosflow.core.ai.MedicationAssistSuggestion
import com.chronosflow.core.ai.RoutineAssistSource
import com.chronosflow.core.domain.model.MedicationPlan
import com.chronosflow.core.ui.components.ChronosModalActionLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationParserAndLabelCoverageTest {
    private val FORM_SHEET_KT = Class.forName("com.chronosflow.feature.medication.MedicationFormSheetKt")
    private val SCREEN_KT = Class.forName("com.chronosflow.feature.medication.MedicationScreenKt")
    private val HINTS_CLASS = Class.forName("com.chronosflow.feature.medication.MedicationModalAdaptiveHints")

    @Test
    fun `normalize transcript dose and unit handles known variants`() {
        assertEquals(
            "0.5",
            invokePrivate<String>(FORM_SHEET_KT, "normalizeTranscriptDose", String::class.java to "half")
        )
        assertEquals(
            "1",
            invokePrivate<String>(FORM_SHEET_KT, "normalizeTranscriptDose", String::class.java to "one")
        )
        assertEquals(
            "10",
            invokePrivate<String>(FORM_SHEET_KT, "normalizeTranscriptDose", String::class.java to "ten")
        )

        assertEquals(
            "IU",
            invokePrivate<String>(FORM_SHEET_KT, "normalizeTranscriptUnit", String::class.java to "iu")
        )
        assertEquals(
            "IU",
            invokePrivate<String>(FORM_SHEET_KT, "normalizeTranscriptUnit", String::class.java to "i.u.")
        )
        assertEquals(
            "tablet",
            invokePrivate<String>(FORM_SHEET_KT, "normalizeTranscriptUnit", String::class.java to "tablets")
        )
    }

    @Test
    fun `medication transcript parsing identifies name and schedule fields`() {
        assertEquals(
            "Vitamin D",
            invokePrivate<String>(
                FORM_SHEET_KT,
                "medicationTranscriptName",
                String::class.java to "take vitamin d 1000 iu"
            )
        )
        assertEquals(
            "Metformin",
            invokePrivate<String>(FORM_SHEET_KT, "medicationTranscriptName", String::class.java to "metformin 500mg now")
        )
        assertEquals(
            null,
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptName", String::class.java to "plain medication")
        )

        assertEquals(
            "Every 12 hours",
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptFrequency", String::class.java to "take every 12 hours")
        )
        assertEquals(
            "Twice daily",
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptFrequency", String::class.java to "take one pill twice daily")
        )
        assertEquals(
            "As needed",
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptFrequency", String::class.java to "use as needed")
        )

        assertEquals(
            "With food",
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptMealTiming", String::class.java to "take with food after lunch")
        )
        assertEquals(
            null,
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptMealTiming", String::class.java to "random timing note")
        )
    }

    @Test
    fun `medication transcript parsing infers form route and refill context`() {
        assertEquals(
            "injection",
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptForm", String::class.java to "insulin injection before bed")
        )
        assertEquals(
            "inhaler",
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptForm", String::class.java to "rescue inhaler today")
        )
        assertEquals(
            null,
            invokePrivate<String?>(FORM_SHEET_KT, "medicationTranscriptForm", String::class.java to "vitamin d supplement 1000 iu")
        )

        assertEquals(
            "injection",
            invokePrivate<String?>(
                FORM_SHEET_KT,
                "medicationTranscriptRoute",
                String::class.java to "insulin injection before bed",
                String::class.java to "injection"
            )
        )
        assertEquals(
            "inhaled",
            invokePrivate<String?>(
                FORM_SHEET_KT,
                "medicationTranscriptRoute",
                String::class.java to "rescue inhaler now",
                String::class.java to "inhaler"
            )
        )
        assertEquals(
            true,
            invokePrivate<Boolean>(FORM_SHEET_KT, "medicationTranscriptHasRefillContext", String::class.java to "refill requested at pharmacy")
        )
        assertEquals(
            false,
            invokePrivate<Boolean>(FORM_SHEET_KT, "medicationTranscriptHasRefillContext", String::class.java to "hydration reminders")
        )
    }

    @Test
    fun `modal action labels switch based on mode`() {
        val addLabels = invokePrivate<ChronosModalActionLabels>(
            FORM_SHEET_KT,
            "medicationModalActionLabels",
            Boolean::class.javaPrimitiveType!! to true
        )
        assertEquals("Add medication", addLabels.confirm)
        assertEquals("Duplicate medication", addLabels.duplicate)
        assertEquals("Archive medication", addLabels.archive)

        val editLabels = invokePrivate<ChronosModalActionLabels>(
            FORM_SHEET_KT,
            "medicationModalActionLabels",
            Boolean::class.javaPrimitiveType!! to false
        )
        assertEquals("Save medication", editLabels.confirm)
        assertEquals("Delete medication", editLabels.archive)
    }

    @Test
    fun `adaptive hints and dynamic title logic are deterministic`() {
        val suggestions = listOf<MedicationAssistSuggestion>(
            MedicationAssistSuggestion.Details(
                id = "details",
                label = "Name: Allergy",
                reason = "Capture",
                source = RoutineAssistSource.LOCAL,
                name = "Allergy medication",
                dosage = "10",
                unit = "mg"
            ),
            MedicationAssistSuggestion.Reminder(
                id = "reminder",
                label = "Night reminder",
                reason = "Capture",
                source = RoutineAssistSource.LOCAL,
                primaryMinute = 21 * 60,
                frequency = "Nightly"
            )
        )

        val hints = invokePrivate<Any>(
            FORM_SHEET_KT,
            "medicationModalAdaptiveHints",
            String::class.java to "rescue",
            String::class.java to "",
            String::class.java to "",
            String::class.java to "As needed",
            String::class.java to "",
            String::class.java to "",
            String::class.java to "inhaler",
            List::class.java to suggestions
        )

        val hintValues = extractMedicationHints(hints)
        assertEquals(true, hintValues.showDose)
        assertEquals(true, hintValues.showSafety)

        val title = invokePrivate<String>(
            FORM_SHEET_KT,
            "medicationAddModalDynamicTitle",
            String::class.java to "",
            String::class.java to "",
            String::class.java to "",
            HINTS_CLASS to hints,
            List::class.java to listOf(
                MedicationAssistSuggestion.Details(
                    id = "details",
                    label = "Name: Allergy",
                    reason = "Capture",
                    source = RoutineAssistSource.LOCAL,
                    name = "Allergy medication"
                )
            )
        )
        assertEquals("New Allergy medication", title)
    }

    @Test
    fun `screen helper maps known dosage forms to expected icon values`() {
        assertEquals(Icons.Filled.Air, invokePrivate<ImageVector>(SCREEN_KT, "dosageFormIcon", String::class.java to "inhaler"))
        assertEquals(Icons.Filled.WaterDrop, invokePrivate<ImageVector>(SCREEN_KT, "dosageFormIcon", String::class.java to "drop"))
        assertEquals(Icons.Filled.WaterDrop, invokePrivate<ImageVector>(SCREEN_KT, "dosageFormIcon", String::class.java to "liquid"))
        assertEquals(Icons.Filled.Medication, invokePrivate<ImageVector>(SCREEN_KT, "dosageFormIcon", String::class.java to "capsule"))
        assertEquals(Icons.Filled.Medication, invokePrivate<ImageVector>(SCREEN_KT, "dosageFormIcon", String::class.java to ""))
    }

    @Test
    fun `template and dosage helpers are deterministic`() {
        assertEquals(
            "½",
            invokePrivate<String>(FORM_SHEET_KT, "normalizeMedicationDosagePreset", String::class.java to "1/2")
        )
        assertEquals(
            "1000",
            invokePrivate<String>(FORM_SHEET_KT, "normalizeMedicationDosagePreset", String::class.java to "1000")
        )
    }

    @Test
    fun `form and route helpers infer values from unit and name`() {
        assertEquals(
            "liquid",
            invokePrivate<String>(FORM_SHEET_KT, "inferMedicationForm", String::class.java to "ml", String::class.java to "electrolyte")
        )
        assertEquals(
            "inhaler",
            invokePrivate<String>(FORM_SHEET_KT, "inferMedicationForm", String::class.java to "mg", String::class.java to "rescue inhaler")
        )
        assertEquals(
            "inhaled",
            invokePrivate<String>(FORM_SHEET_KT, "inferMedicationRoute", String::class.java to "inhaler")
        )
        assertEquals(
            "topical",
            invokePrivate<String>(FORM_SHEET_KT, "inferMedicationRoute", String::class.java to "cream")
        )
        assertEquals(
            "oral",
            invokePrivate<String>(FORM_SHEET_KT, "inferMedicationRoute", String::class.java to "tablet")
        )
    }

    @Test
    fun `template helpers build lowercase context strings and caution lists`() {
        val context = invokePrivate<String>(
            FORM_SHEET_KT,
            "medicationTemplateContextText",
            String::class.java to "Vitamin D",
            String::class.java to "1000",
            String::class.java to "iu",
            String::class.java to "Once daily",
            String::class.java to "Anytime",
            String::class.java to "Take with food",
            List::class.java to listOf(
                MedicationAssistSuggestion.Details(
                    id = "details",
                    label = "Vitamin D 1000 IU",
                    reason = "Capture",
                    source = RoutineAssistSource.LOCAL
                )
            )
        )

        assertEquals("vitamin d 1000 iu once daily anytime take with food vitamin d 1000 iu capture", context)

        assertEquals(
            listOf("Bedtime routine"),
            invokePrivate<List<String>>(
                FORM_SHEET_KT,
                "deriveMedicationCautions",
                String::class.java to "tablet",
                String::class.java to "Before bed",
                Boolean::class.javaPrimitiveType!! to false
            )
        )
    }

    @Test
    fun `template timing and reminder helpers handle core edge cases`() {
        assertEquals(
            5,
            invokePrivate<Int>(
                FORM_SHEET_KT,
                "medicationTemplateTimingScore",
                Int::class.javaPrimitiveType!! to 9 * 60,
                String::class.java to "morning routine with breakfast"
            )
        )
        assertEquals(
            "Custom",
            invokePrivate<String>(
                FORM_SHEET_KT,
                "resolveMedicationReminderPreset",
                Int::class.javaPrimitiveType!! to 999
            )
        )
        assertEquals(
            "Morning",
            invokePrivate<String>(
                FORM_SHEET_KT,
                "resolveMedicationReminderPreset",
                Int::class.javaPrimitiveType!! to 8 * 60
            )
        )
    }

    @Test
    fun `summary helpers prefer safe user-facing defaults`() {
        assertNull(
            invokePrivate<String?>(
                FORM_SHEET_KT,
                "buildMedicationNotes",
                String::class.java to "Anytime",
                String::class.java to "   "
            )
        )
        assertEquals(
            "Take with food",
            invokePrivate<String?>(
                FORM_SHEET_KT,
                "buildMedicationNotes",
                String::class.java to "Anytime",
                String::class.java to "  Take with food "
            )
        )
        assertEquals(
            "Once daily",
            invokePrivate<String>(FORM_SHEET_KT, "medicationFrequencyLabel", Int::class.javaPrimitiveType!! to 1)
        )
        assertEquals(
            "4 times daily",
            invokePrivate<String>(FORM_SHEET_KT, "medicationFrequencyLabel", Int::class.javaPrimitiveType!! to 99)
        )
        assertEquals(
            1,
            invokePrivate<Int>(FORM_SHEET_KT, "medicationReminderCount", String::class.java to "Once daily")
        )
        assertEquals(
            0,
            invokePrivate<Int>(FORM_SHEET_KT, "medicationReminderCount", String::class.java to "As needed")
        )
        assertEquals(
            "Flexible timing · 1 missed dose logged · refill at 5 doses",
            invokePrivate<String>(
                FORM_SHEET_KT,
                "buildMedicationSafetySummary",
                Int::class.javaPrimitiveType!! to 1,
                Int::class.javaObjectType to 5,
                String::class.java to "Anytime"
            )
        )
    }

    @Test
    fun `template timing and wording helpers exercise remaining branches`() {
        assertEquals(
            0,
            invokePrivate<Int>(
                FORM_SHEET_KT,
                "medicationTemplateTimingScore",
                Int::class.javaPrimitiveType!! to 16 * 60,
                String::class.java to "midday nap"
            )
        )
        assertEquals(
            5,
            invokePrivate<Int>(
                FORM_SHEET_KT,
                "medicationTemplateTimingScore",
                Int::class.javaPrimitiveType!! to 20 * 60,
                String::class.java to "before bed and evening routine"
            )
        )
        assertEquals(
            setOf("take", "1000mg", "vitamin", "once", "daily"),
            invokePrivate<Set<String>>(
                FORM_SHEET_KT,
                "contextTemplateWords",
                String::class.java to "Take 1000mg vitamin-d once daily!"
            )
        )
        assertEquals(
            listOf("Take with food", "Bedtime routine", "Track rescue usage"),
            invokePrivate<List<String>>(
                FORM_SHEET_KT,
                "deriveMedicationCautions",
                String::class.java to "inhaler",
                String::class.java to "Before bed",
                Boolean::class.javaPrimitiveType!! to true
            )
        )
    }

    @Test
    fun `screen archive title is safe for missing names`() {
        assertEquals(
            "Archive \"this medication\"?",
            invokePrivate<String>(
                SCREEN_KT,
                "medicationArchiveConfirmTitle",
                MedicationPlan::class.java to medicationPlan("  ")
            )
        )
        assertEquals(
            "Archive \"Vitamin D\"?",
            invokePrivate<String>(
                SCREEN_KT,
                "medicationArchiveConfirmTitle",
                MedicationPlan::class.java to medicationPlan("Vitamin D")
            )
        )
    }

    private inline fun <reified R> invokePrivate(
        targetClass: Class<*>,
        methodName: String,
        vararg args: Pair<Class<*>, Any?>
    ): R {
        val method = targetClass.getDeclaredMethod(
            methodName,
            *args.map { it.first }.toTypedArray()
        ).apply { isAccessible = true }

        return method.invoke(null, *args.map { it.second }.toTypedArray()) as R
    }

    private fun extractMedicationHints(hints: Any): MedicationModalHintsSnapshot {
        return MedicationModalHintsSnapshot(
            showDose = HINTS_CLASS.getDeclaredField("showDose").apply { isAccessible = true }.getBoolean(hints),
            showReminder = HINTS_CLASS.getDeclaredField("showReminder").apply { isAccessible = true }.getBoolean(hints),
            showSafety = HINTS_CLASS.getDeclaredField("showSafety").apply { isAccessible = true }.getBoolean(hints),
            showRefill = HINTS_CLASS.getDeclaredField("showRefill").apply { isAccessible = true }.getBoolean(hints)
        )
    }

    private data class MedicationModalHintsSnapshot(
        val showDose: Boolean,
        val showReminder: Boolean,
        val showSafety: Boolean,
        val showRefill: Boolean
    )

    private fun medicationPlan(name: String): MedicationPlan = MedicationPlan(
        id = "med-test",
        name = name,
        dosage = "1",
        unit = "mg",
        notes = null,
        startAt = null,
        endAt = null,
        reminderMinuteOfDay = 8 * 60,
        takeWithFood = true,
        missedCount = 0,
        refillNeededAfterDoses = null,
        isActive = true
    )
}
