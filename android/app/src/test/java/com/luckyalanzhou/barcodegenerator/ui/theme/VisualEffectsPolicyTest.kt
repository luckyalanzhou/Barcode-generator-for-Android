package com.luckyalanzhou.barcodegenerator.ui.theme

import org.junit.Assert.*
import org.junit.Test

class VisualEffectsPolicyTest {
    @Test fun defaultKeepsEffectsWhenSystemAllowsThem() {
        assertEquals(VisualEffectsPolicy(), resolveVisualEffectsPolicy(true, false))
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

    @Test fun contrastAndMotionAreIndependent() {
        val policy = resolveVisualEffectsPolicy(false, true)
        assertTrue(policy.opaqueGlass)
        assertTrue(policy.reduceMotion)
        assertTrue(policy.highContrast)
    }
}
