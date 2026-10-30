package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionRimColors
import com.luckyalanzhou.barcodegenerator.ui.theme.DarkAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LightAppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Test

/** 结果按钮与普通有框按钮共用同一条静态轮廓的低光 fallback。 */
class StaticButtonOutlineTest {
    @Test fun sharedRimFallbackAdaptsToLightAndDarkSurfaces() {
        assertEquals(
            androidx.compose.ui.graphics.Color.Black.copy(alpha = .22f),
            resultActionRimColors(LightAppColorScheme.surfaces.background).outline,
        )
        assertEquals(
            androidx.compose.ui.graphics.Color.White.copy(alpha = .30f),
            resultActionRimColors(DarkAppColorScheme.surfaces.background).outline,
        )
    }
}
