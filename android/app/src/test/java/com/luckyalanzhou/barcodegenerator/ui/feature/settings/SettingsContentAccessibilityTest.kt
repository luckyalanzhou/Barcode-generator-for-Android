package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import android.app.Application
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 设置页值选择入口需向无障碍服务说明字段语义和菜单状态。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsContentAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun ocrCorrectionPickerAnnouncesItsLabelAndExpandedState() {
        compose.setContent {
            AppTheme("light") {
                SettingsContent(
                    checkingForUpdates = false,
                    settings = SettingsContentState(StyleSettings(), ocrMask = 0),
                    dark = false,
                    onStyleChange = {},
                    onOcrMaskChange = {},
                    onEnterLanShare = {},
                    onShareDebugLog = {},
                    onCheckForUpdates = {},
                    onNotice = {},
                )
            }
        }

        compose.onNodeWithText("关闭")
            .assert(hasContentDescription("OCR 字符纠错"))
            .assert(hasStateDescription("关闭，已收起"))
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("关闭")
            .assert(hasContentDescription("OCR 字符纠错"))
            .assert(hasStateDescription("关闭，已展开"))
    }

    @Test
    fun updateActionKeepsItsFullLabelAtLargeSystemFontScale() {
        compose.setContent {
            AppTheme("light") {
                val density = LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f),
                ) {
                    SettingsButton(
                        text = "检查更新",
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = androidx.compose.ui.graphics.Color.Black,
                        onClick = {},
                        modifier = Modifier.widthIn(min = 112.dp),
                    )
                }
            }
        }

        compose.onNodeWithText("检查更新").assertWidthIsAtLeast(120.dp)
    }
}
