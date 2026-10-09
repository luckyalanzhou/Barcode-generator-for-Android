package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ResultActionShadowTest {
    @Test fun shadowsAreLowAndContinuousAcrossPressStates() {
        for (background in listOf(Color.White, Color.Black)) {
            var previous = 0f
            for (step in 0..100) {
                val shadow = resultActionShadow(background, step / 100f, false)
                assertTrue(shadow.elevationDp in .6f..1.2f)
                assertTrue(shadow.elevationDp >= previous)
                assertTrue(shadow.ambientAlpha <= .04f)
                assertTrue(shadow.spotAlpha <= .07f)
                previous = shadow.elevationDp
            }
        }
    }

    @Test fun darkThemeDoesNotAddHeavyBlackShadow() {
        val light = resultActionShadow(Color.White, 0f, false)
        val dark = resultActionShadow(Color.Black, 0f, false)
        assertTrue(dark.ambientAlpha < light.ambientAlpha)
        assertTrue(dark.spotAlpha < light.spotAlpha)
    }

    @Test fun accessibilityAndInvalidActivityHaveSafeBounds() {
        assertEquals(ResultActionShadow(0f, 0f, 0f), resultActionShadow(Color.White, 1f, true))
        for (activity in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            assertEquals(.6f, resultActionShadow(Color.White, activity, false).elevationDp, 0f)
        }
        assertEquals(1.2f, resultActionShadow(Color.White, 100f, false).elevationDp, 0f)
    }
}
