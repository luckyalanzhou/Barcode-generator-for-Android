package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.ui.theme.VisualEffectsPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupAppearanceTest {
    @Test fun allPopupKindsUseTheExistingGlassSetting() {
        for (kind in PopupKind.entries) {
            val enabled = popupAppearance(kind, VisualEffectsPolicy(), 35)
            val disabled = popupAppearance(kind,
                VisualEffectsPolicy(liquidGlassEnabled = false, opaqueGlass = true), 35)
            assertTrue(enabled.glass)
            assertFalse(enabled.classicMaterial)
            assertEquals(kind.glassCorner, enabled.corner)
            assertFalse(disabled.glass)
            assertTrue(disabled.classicMaterial)
            assertEquals(kind.classicCorner, disabled.corner)
        }
    }

    @Test fun unsupportedGraphicsAndHighContrastKeepTheClassicSurface() {
        assertFalse(popupAppearance(PopupKind.Card, VisualEffectsPolicy(), 32).glass)
        assertFalse(popupAppearance(PopupKind.Card, VisualEffectsPolicy(), 32).classicMaterial)
        assertFalse(popupAppearance(PopupKind.Card, VisualEffectsPolicy(highContrast = true), 35).glass)
        assertFalse(popupAppearance(PopupKind.Card, VisualEffectsPolicy(highContrast = true), 35).classicMaterial)
        val opaqueFallback = popupAppearance(PopupKind.Card, VisualEffectsPolicy(opaqueGlass = true), 35)
        assertFalse(opaqueFallback.glass)
        assertFalse(opaqueFallback.classicMaterial)
    }
}
