package com.luckyalanzhou.barcodegenerator.ui.theme

import org.junit.Assert.*
import org.junit.Test

class VisualEffectsPolicyTest {
    @Test fun defaultKeepsEffectsWhenSystemAllowsThem() {
        assertEquals(VisualEffectsPolicy(), resolveVisualEffectsPolicy(true, false))
    }

    @Test fun carriesGlassStrengthIntoPolicyAndKeepsDefaultBaseline() {
        assertEquals(50, resolveVisualEffectsPolicy(true, false).glassStrengthPercent)
        assertEquals(0, resolveVisualEffectsPolicy(true, false, glassStrengthPercent = 0).glassStrengthPercent)
        assertEquals(100, resolveVisualEffectsPolicy(true, false, glassStrengthPercent = 100).glassStrengthPercent)
        assertEquals(0, resolveVisualEffectsPolicy(true, false, glassStrengthPercent = -20).glassStrengthPercent)
        assertEquals(100, resolveVisualEffectsPolicy(true, false, glassStrengthPercent = 120).glassStrengthPercent)
    }

    @Test fun systemAnimationSettingControlsMotionPolicy() {
        for (systemAnimations in listOf(false, true)) {
            val policy = resolveVisualEffectsPolicy(systemAnimations, false)
            assertEquals(!systemAnimations, policy.reduceMotion)
        }
    }

    @Test fun systemHighContrastControlsGlassAndControlContrast() {
        for (systemContrast in listOf(false, true)) {
            val policy = resolveVisualEffectsPolicy(true, systemContrast)
            assertEquals(systemContrast, policy.highContrast)
            assertEquals(systemContrast, policy.opaqueGlass)
        }
    }

    @Test fun disabledGlassAndHighContrastKeepOpaqueGlassPriorityWithCustomStrength() {
        val disabled = resolveVisualEffectsPolicy(true, false, liquidGlassEnabled = false, glassStrengthPercent = 20)
        val highContrast = resolveVisualEffectsPolicy(true, true, liquidGlassEnabled = true, glassStrengthPercent = 80)

        assertTrue(disabled.opaqueGlass)
        assertEquals(20, disabled.glassStrengthPercent)
        assertTrue(highContrast.opaqueGlass)
        assertEquals(80, highContrast.glassStrengthPercent)
    }

    @Test fun contrastAndMotionAreIndependent() {
        val policy = resolveVisualEffectsPolicy(false, true)
        assertTrue(policy.opaqueGlass)
        assertTrue(policy.reduceMotion)
        assertTrue(policy.highContrast)
    }
}
