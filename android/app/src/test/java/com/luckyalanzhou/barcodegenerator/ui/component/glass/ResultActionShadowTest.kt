package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ResultActionShadowTest {
    @Test fun shadowsRemainLowAndFixed() {
        for (background in listOf(Color.White, Color.Black)) {
            repeat(100) {
                val shadow = resultActionShadow(background, false)
                assertEquals(.6f, shadow.elevationDp, 0f)
                assertTrue(shadow.ambientAlpha <= .04f)
                assertTrue(shadow.spotAlpha <= .07f)
            }
        }
    }

    @Test fun darkThemeDoesNotAddHeavyBlackShadow() {
        val light = resultActionShadow(Color.White, false)
        val dark = resultActionShadow(Color.Black, false)
        assertTrue(dark.ambientAlpha < light.ambientAlpha)
        assertTrue(dark.spotAlpha < light.spotAlpha)
    }

    @Test fun accessibilityRemovesShadow() {
        assertEquals(ResultActionShadow(0f, 0f, 0f), resultActionShadow(Color.White, true))
    }
}
