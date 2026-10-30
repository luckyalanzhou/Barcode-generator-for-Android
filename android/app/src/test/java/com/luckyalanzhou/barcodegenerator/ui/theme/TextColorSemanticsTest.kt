package com.luckyalanzhou.barcodegenerator.ui.theme

import androidx.compose.ui.graphics.Color
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.lanSharePeerBubbleColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextColorSemanticsTest {
    @Test fun settingsSecondaryTextUsesTheSharedSecondaryColorInBothThemes() {
        listOf(LightAppColorScheme, DarkAppColorScheme).forEach { colors ->
            assertEquals(colors.text.secondary, colors.settingsText.secondary)
        }
    }

    @Test
    fun secondaryTextMeetsBodyTextContrastOnLightSurfaces() {
        val colors = LightAppColorScheme
        listOf(
            "页面背景" to colors.surfaces.background,
            "卡片背景" to colors.surfaces.surface,
            "输入框背景" to colors.surfaces.input,
            "输入面板背景" to colors.surfaces.inputPanel,
        ).forEach { (name, background) ->
            assertBodyTextContrast("浅色$name", colors.text.secondary, background)
        }
    }

    @Test
    fun linkTextMeetsBodyTextContrastOnEveryPeerBubbleInBothThemes() {
        listOf(false, true).forEach { dark ->
            val colors = appColorScheme(dark)
            listOf(
                "页面背景" to colors.surfaces.background,
                "卡片背景" to colors.surfaces.surface,
                "输入框背景" to colors.surfaces.input,
                "输入面板背景" to colors.surfaces.inputPanel,
            ).forEach { (name, background) ->
                assertBodyTextContrast("${if (dark) "深色" else "浅色"}$name 链接", colors.text.link, background)
            }
            repeat(8) { peerIndex ->
                assertBodyTextContrast(
                    "${if (dark) "深色" else "浅色"}设备气泡$peerIndex",
                    colors.text.link,
                    lanSharePeerBubbleColor(peerIndex, dark),
                )
            }
        }
    }

    @Test
    fun primaryActionTextMeetsBodyTextContrastInBothThemes() {
        listOf(LightAppColorScheme, DarkAppColorScheme).forEachIndexed { index, colors ->
            assertBodyTextContrast("主题主操作$index", colors.text.onAccent, colors.controls.primaryAction)
        }
    }

    private fun assertBodyTextContrast(label: String, foreground: Color, background: Color) {
        val renderedForeground = foreground.compositeOver(background)
        val foregroundLuminance = renderedForeground.relativeLuminance()
        val backgroundLuminance = background.relativeLuminance()
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        val ratio = (lighter + 0.05) / (darker + 0.05)

        assertTrue("$label 普通文字对比度 ${"%.2f".format(ratio)}:1 必须至少为 4.5:1", ratio >= 4.5)
    }

    private fun Color.compositeOver(background: Color): Color = Color(
        red = red * alpha + background.red * (1f - alpha),
        green = green * alpha + background.green * (1f - alpha),
        blue = blue * alpha + background.blue * (1f - alpha),
    )

    private fun Color.relativeLuminance(): Double {
        fun linear(channel: Float): Double =
            if (channel <= 0.04045f) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)

        return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    }
}
