package com.chronosflow.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarImportClassifierTest {

    @Test
    fun `meetings and reviews classify as high energy`() {
        assertEquals(EnergyIntensity.HIGH, classifyImportedEventEnergy("Team meeting"))
        assertEquals(EnergyIntensity.HIGH, classifyImportedEventEnergy("Quarterly Review"))
        assertEquals(EnergyIntensity.HIGH, classifyImportedEventEnergy("Interview with candidate"))
        assertEquals(EnergyIntensity.HIGH, classifyImportedEventEnergy("Sprint planning"))
    }

    @Test
    fun `meals social and travel classify as low energy`() {
        assertEquals(EnergyIntensity.LOW, classifyImportedEventEnergy("Lunch with Sam"))
        assertEquals(EnergyIntensity.LOW, classifyImportedEventEnergy("Coffee catch-up"))
        assertEquals(EnergyIntensity.LOW, classifyImportedEventEnergy("Flight to Berlin"))
        assertEquals(EnergyIntensity.LOW, classifyImportedEventEnergy("Maya's birthday"))
    }

    @Test
    fun `low energy keywords win over high energy keywords`() {
        assertEquals(EnergyIntensity.LOW, classifyImportedEventEnergy("Lunch meeting"))
    }

    @Test
    fun `description contributes to classification`() {
        assertEquals(
            EnergyIntensity.HIGH,
            classifyImportedEventEnergy("Catch-up", "standup with the platform team")
        )
    }

    @Test
    fun `unrecognized events stay moderate`() {
        assertEquals(EnergyIntensity.MODERATE, classifyImportedEventEnergy("Untitled Event"))
        assertEquals(EnergyIntensity.MODERATE, classifyImportedEventEnergy("Dentist"))
    }
}
