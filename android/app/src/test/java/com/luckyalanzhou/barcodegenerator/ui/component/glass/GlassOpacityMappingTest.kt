package com.luckyalanzhou.barcodegenerator.ui.component.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassOpacityMappingTest {
    @Test
    fun mapsEveryTenPercentStepMonotonicallyAndKeepsFiftyPercentBaseline() {
        val opacities = (0..100 step 10).map { glassOpacityForStrength(0.8f, it) }

        assertEquals(0.6f, opacities.first(), 0.0001f)
        assertEquals(0.8f, opacities[5], 0.0001f)
        assertEquals(0.87f, opacities.last(), 0.0001f)
        assertTrue(opacities.zipWithNext().all { (previous, next) -> next > previous })
    }

    @Test
    fun fiftyPercentPreservesEachExistingOpacityBaseline() {
        listOf(0f, 0.09f, 0.18f, 0.22f, 0.47f, 0.53f, 0.8f, 0.98f, 1f).forEach { baseline ->
            assertEquals(baseline, glassOpacityForStrength(baseline, 50), 0.0001f)
        }
    }

    @Test
    fun clampsStrengthAndOutputAndUsesFiniteFallback() {
        assertEquals(glassOpacityForStrength(0.8f, 0), glassOpacityForStrength(0.8f, -50), 0f)
        assertEquals(glassOpacityForStrength(0.8f, 100), glassOpacityForStrength(0.8f, 150), 0f)
        assertEquals(0.5f, glassOpacityForStrength(Float.NaN, 50), 0.0001f)
        assertEquals(0.5f, glassOpacityForStrength(Float.POSITIVE_INFINITY, 50), 0.0001f)
        assertEquals(1f, glassOpacityForStrength(1f, 100), 0f)
        assertEquals(1f, glassOpacityForStrength(2f, 100), 0f)
        assertEquals(0f, glassOpacityForStrength(-1f, 0), 0f)
    }

    @Test
    fun fullyOpaqueBaselineIsConstantAtEveryStep() {
        (0..100 step 10).forEach { percent ->
            assertEquals(1f, glassOpacityForStrength(1f, percent), 0f)
        }
    }
}
