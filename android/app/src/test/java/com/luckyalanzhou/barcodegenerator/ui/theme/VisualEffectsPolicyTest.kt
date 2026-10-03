package com.luckyalanzhou.barcodegenerator.ui.theme

import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import org.junit.Assert.*
import org.junit.Test

class VisualEffectsPolicyTest {
    @Test fun defaultKeepsEffectsWhenSystemAllowsThem() {
        assertEquals(VisualEffectsPolicy(), resolveVisualEffectsPolicy(StyleSettings(), true, false))
    }

    @Test fun systemAndUserRestrictionsCannotCancelEachOther() {
        for (userMotion in listOf(false, true)) for (systemAnimations in listOf(false, true)) {
            val policy = resolveVisualEffectsPolicy(StyleSettings(reduceMotion = userMotion), systemAnimations, false)
            assertEquals(userMotion || !systemAnimations, policy.reduceMotion)
        }
        for (userContrast in listOf(false, true)) for (systemContrast in listOf(false, true)) {
            val policy = resolveVisualEffectsPolicy(StyleSettings(enhanceContrast = userContrast), true, systemContrast)
            assertEquals(userContrast || systemContrast, policy.highContrast)
            assertEquals(userContrast || systemContrast, policy.opaqueGlass)
        }
    }

    @Test fun transparencyAndMotionAreIndependent() {
        val policy = resolveVisualEffectsPolicy(StyleSettings(reduceTransparency = true), true, false)
        assertTrue(policy.opaqueGlass)
        assertFalse(policy.reduceMotion)
        assertFalse(policy.highContrast)
    }
}
