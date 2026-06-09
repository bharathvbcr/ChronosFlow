package com.chronosflow.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyIntensityTest {

    @Test
    fun `fromLevel maps known levels`() {
        assertEquals(EnergyIntensity.LOW, EnergyIntensity.fromLevel(1))
        assertEquals(EnergyIntensity.MODERATE, EnergyIntensity.fromLevel(2))
        assertEquals(EnergyIntensity.HIGH, EnergyIntensity.fromLevel(3))
        assertEquals(EnergyIntensity.INTENSE, EnergyIntensity.fromLevel(4))
        assertEquals(EnergyIntensity.MAX, EnergyIntensity.fromLevel(5))
    }

    @Test
    fun `fromLevel returns moderate for null or unsupported values`() {
        assertEquals(EnergyIntensity.MODERATE, EnergyIntensity.fromLevel(null))
        assertEquals(EnergyIntensity.MODERATE, EnergyIntensity.fromLevel(0))
        assertEquals(EnergyIntensity.MODERATE, EnergyIntensity.fromLevel(-1))
        assertEquals(EnergyIntensity.MODERATE, EnergyIntensity.fromLevel(10))
    }
}

