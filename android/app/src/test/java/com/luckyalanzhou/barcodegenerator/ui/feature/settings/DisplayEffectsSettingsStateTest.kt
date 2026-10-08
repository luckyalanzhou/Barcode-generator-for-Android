package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.resolveVisualEffectsPolicy
import org.junit.Assert.*
import org.junit.Test

class DisplayEffectsSettingsStateTest {
    @Test fun defaultsAreEditableAndKeepTheOriginalEffects() {
        val style = StyleSettings()
        val state = displayEffectsSettingsState(style, resolveVisualEffectsPolicy(style, true, false))
        assertEquals("默认", state.summary)
        for (option in listOf(state.motion, state.contrast)) {
            assertFalse(option.checked)
            assertTrue(option.enabled)
        }
    }

    @Test fun everyUserAndSystemCombinationShowsEffectiveValuesWithoutMutatingPreferences() {
        for (bits in 0 until 4) for (animationsEnabled in listOf(false, true)) for (systemContrast in listOf(false, true)) {
            val style = StyleSettings(reduceMotion = bits and 1 != 0,
                enhanceContrast = bits and 2 != 0)
            val saved = style.copy()
            val policy = resolveVisualEffectsPolicy(style, animationsEnabled, systemContrast)
            val state = displayEffectsSettingsState(style, policy)
            assertEquals(policy.reduceMotion, state.motion.checked)
            assertEquals(policy.highContrast, state.contrast.checked)
            assertEquals(animationsEnabled, state.motion.enabled)
            assertEquals(!systemContrast, state.contrast.enabled)
            assertEquals(when {
                bits != 0 -> "已调整"
                !animationsEnabled || systemContrast -> "跟随系统"
                else -> "默认"
            }, state.summary)
            assertEquals(saved, style)
        }
    }

    @Test fun disablingContrastRestoresTransparentMaterial() {
        val style = StyleSettings(enhanceContrast = true)
        assertTrue(resolveVisualEffectsPolicy(style, true, false).opaqueGlass)
        val restoredStyle = style.copy(enhanceContrast = false)
        val policy = resolveVisualEffectsPolicy(restoredStyle, true, false)
        val restored = displayEffectsSettingsState(restoredStyle, policy)
        assertFalse(policy.opaqueGlass)
        assertFalse(restored.contrast.checked)
        assertEquals("默认", restored.summary)
    }

    @Test fun systemOverrideIsExplainedAndDoesNotBecomeAnAppPreference() {
        val style = StyleSettings()
        val constrained = displayEffectsSettingsState(style, resolveVisualEffectsPolicy(style, false, true))
        assertEquals("跟随系统", constrained.summary)
        assertTrue(constrained.motion.explanation.contains("系统"))
        assertTrue(constrained.contrast.explanation.contains("系统"))
        val restored = displayEffectsSettingsState(style, resolveVisualEffectsPolicy(style, true, false))
        assertEquals("默认", restored.summary)
        assertFalse(restored.motion.checked)
        assertFalse(restored.contrast.checked)
    }
}
