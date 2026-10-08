package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class GlassBackdropMaterialTest {
    @Test fun materialIsBoundedAcrossSizesAndThemes() {
        for (color in listOf(Color.Black, Color.White, Color(.12f, .13f, .15f))) {
            for (height in listOf(-100f, 0f, 80f, 150f, 240f, 2000f)) {
                val material = menuGlassMaterial(color, height)
                assertTrue(material.opacity in .56f.. .70f)
                assertTrue(material.blurDp in 7f..10f)
                assertTrue(material.refractionDp in .5f.. .86f)
            }
        }
    }

    @Test fun largerMenuHasMoreThicknessAndDarkMenuMoreProtection() {
        val small = menuGlassMaterial(Color.White, 80f)
        val large = menuGlassMaterial(Color.White, 240f)
        assertTrue(large.opacity > small.opacity)
        assertTrue(large.blurDp > small.blurDp)
        assertTrue(menuGlassMaterial(Color.Black, 80f).opacity > small.opacity)
        assertEquals(large, menuGlassMaterial(Color.White, 900f))
    }
}
