package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN", sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsLiquidGlassStrengthRowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun strengthRowFollowsGlassSwitchAndExposesDiscreteDefaultAndDisabledState() {
        compose.setContent {
            AppTheme("light") {
                AppearanceThemeContent(
                    settings = SettingsContentState(
                        style = StyleSettings(),
                        ocrMask = 0,
                        liquidGlassEnabled = false,
                    ),
                    dark = false,
                    onBack = {},
                    onStyleChange = {},
                    onLiquidGlassEnabledChange = {},
                    onLiquidGlassStrengthChange = {},
                    onBottomTabLabelsVisibleChange = {},
                )
            }
        }

        val title = compose.onNodeWithText("应用主题")
        val glass = compose.onNodeWithText("液态玻璃效果")
        val strength = compose.onNodeWithText("强度")
        assertTrue(title.fetchSemanticsNode().boundsInRoot.top < glass.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(glass.fetchSemanticsNode().boundsInRoot.top < strength.fetchSemanticsNode().boundsInRoot.top)

        compose.onNodeWithText("50%").assertExists()
        val slider = compose.onNode(hasContentDescription("强度"))
        slider.assert(hasStateDescription("50%")).assertIsNotEnabled()
        val rangeInfo = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0f..100f, rangeInfo.range)
        assertEquals(9, rangeInfo.steps)
        assertEquals(50f, rangeInfo.current, 0f)
        assertEquals(11, rangeInfo.steps + 2)
    }

    @Test
    fun strengthDragUpdatesPercentAndPersistsOnlyTenPointValues() {
        var changed = -1
        compose.setContent {
            AppTheme("light") {
                val percent = remember { mutableIntStateOf(50) }
                AppearanceThemeContent(
                    settings = SettingsContentState(
                        style = StyleSettings(),
                        ocrMask = 0,
                        liquidGlassEnabled = true,
                        liquidGlassStrengthPercent = percent.intValue,
                    ),
                    dark = false,
                    onBack = {},
                    onStyleChange = {},
                    onLiquidGlassEnabledChange = {},
                    onLiquidGlassStrengthChange = { changed = it; percent.intValue = it },
                    onBottomTabLabelsVisibleChange = {},
                )
            }
        }

        val slider = compose.onNode(hasContentDescription("强度"))
        slider.assert(hasStateDescription("50%")).assertIsEnabled()
        slider.performTouchInput {
            val start = center
            down(start)
            moveTo(Offset(center.x * 1.8f, center.y))
            up()
        }
        compose.runOnIdle {
            assertTrue(changed in 50..100)
            assertEquals(0, changed % 10)
        }
        compose.onNodeWithText("${changed}%").assertExists()
    }
}
