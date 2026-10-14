package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.ui.graphics.Color
import com.luckyalanzhou.barcodegenerator.ui.component.staticButtonOutlineColor
import org.junit.Assert.assertEquals
import org.junit.Test

/** 按钮完整轮廓的主题配色；尺寸、点击业务与描边解耦。 */
class StaticButtonOutlineTest {
    @Test fun lightUsesDarkOutlineAndDarkUsesLightOutline() {
        assertEquals(Color.Black.copy(alpha = .16f), staticButtonOutlineColor(false))
        assertEquals(Color.White.copy(alpha = .22f), staticButtonOutlineColor(true))
    }
}
