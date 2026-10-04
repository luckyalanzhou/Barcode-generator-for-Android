package com.luckyalanzhou.barcodegenerator.ui.app

import org.junit.Assert.*
import org.junit.Test

class MenuBackdropTest {
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

    @Test fun blurIsBoundedAndOpaquePolicyDisablesIt() {
        assertEquals(40f, menuBackdropBlurPx(10f, 2f, false), 0f)
        assertEquals(0f, menuBackdropBlurPx(-1f, 2f, false), 0f)
        assertEquals(0f, menuBackdropBlurPx(1f, 2f, true), 0f)
        assertEquals(0f, menuBackdropBlurPx(Float.NaN, 2f, false), 0f)
        assertEquals(0f, menuBackdropBlurPx(1f, Float.NaN, false), 0f)
    }
}
