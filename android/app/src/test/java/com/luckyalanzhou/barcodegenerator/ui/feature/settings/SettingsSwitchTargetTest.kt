package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsSwitchTargetTest {
    @Test fun labelIncludesSystemExplanationWithoutBlankSuffix() {
        assertEquals("显示条码格式", switchAccessibilityLabel("显示条码格式", null))
        assertEquals("显示条码格式", switchAccessibilityLabel("显示条码格式", " "))
        assertEquals("减少动态效果。系统已关闭动画", switchAccessibilityLabel("减少动态效果", "系统已关闭动画"))
    }

    @Test fun enabledSwitchRetainsThemeColor() {
        assertEquals(Color.Blue, settingsSwitchColor(Color.Blue, Color.White, true, false))
    }

    @Test fun disabledSwitchBlendsInBothThemesWithoutChangingOpacity() {
        for (surface in listOf(Color.White, Color.Black)) {
            val color = settingsSwitchColor(Color.Blue, surface, false, false)
            assertNotEquals(Color.Blue, color)
            assertNotEquals(surface, color)
            assertEquals(1f, color.alpha, .001f)
        }
    }

    @Test fun highContrastDoesNotFadeDisabledSwitch() {
        assertEquals(Color.Blue, settingsSwitchColor(Color.Blue, Color.Black, false, true))
    }
}
