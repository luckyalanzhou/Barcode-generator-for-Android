package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class MenuBackdropTest {
    @Test fun frostAppliesDifferentVeilsInLightAndDarkAppearance() {
        assertEquals(Color.White.copy(alpha = .10f), menuBackdropVeil(1f, dark = false, frosted = true))
        assertEquals(Color.Black.copy(alpha = .12f), menuBackdropVeil(1f, dark = true, frosted = true))
        assertEquals(Color.Black.copy(alpha = .045f), menuBackdropVeil(1f, dark = false, frosted = false))
        assertEquals(0f, menuBackdropVeil(0f, dark = false, frosted = true).alpha, 0f)
    }

    @Test fun menuLetsColorThroughOnlyAfterBackdropIsBlurred() {
        assertEquals(1f, frostedMenuPanelAlpha(.75f, dark = false), 0f)
        assertEquals(.93f, frostedMenuPanelAlpha(.875f, dark = false), .001f)
        assertEquals(.86f, frostedMenuPanelAlpha(1f, dark = false), .001f)
        assertEquals(.90f, frostedMenuPanelAlpha(1f, dark = true), .001f)
    }
    @Test fun dimmingIsSubtleAndFollowsMenuProgress() {
        assertEquals(.045f, menuBackdropDimAlpha(1f, false), 0f)
        assertEquals(.025f, menuBackdropDimAlpha(1f, true), 0f)
        assertEquals(0f, menuBackdropDimAlpha(0f, true), 0f)
        assertEquals(0f, menuBackdropDimAlpha(Float.NaN, false), 0f)
        assertEquals(.025f, menuBackdropDimAlpha(2f, true), 0f)
    }
    @Test fun closingContinuouslyRemovesBlurUsingOnePresentationCoordinate() {
        var previous = menuBackdropBlurPx(1f, 3f, false)
        assertEquals(60f, previous, 0f)
        for (step in 99 downTo 0) {
            val value = menuBackdropBlurPx(step / 100f, 3f, false)
            assertTrue(value <= previous)
            previous = value
        }
        assertEquals(0f, previous, 0f)
    }

    @Test fun blurIsBoundedAndReducedMotionDisablesIt() {
        assertEquals(40f, menuBackdropBlurPx(10f, 2f, false), 0f)
        assertEquals(0f, menuBackdropBlurPx(-1f, 2f, false), 0f)
        assertEquals(0f, menuBackdropBlurPx(1f, 2f, true), 0f)
        assertEquals(0f, menuBackdropBlurPx(Float.NaN, 2f, false), 0f)
        assertEquals(0f, menuBackdropBlurPx(1f, Float.NaN, false), 0f)
    }
}
