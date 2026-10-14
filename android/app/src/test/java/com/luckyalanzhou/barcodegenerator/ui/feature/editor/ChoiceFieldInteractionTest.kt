package com.luckyalanzhou.barcodegenerator.ui.feature.editor

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 验证格式选择真实 Popup 的选中回传与关闭；不把材料/动画契约测试当作交互测试。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChoiceFieldInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun choosingFormatUpdatesValueAndClosesMenu() {
        var value by mutableStateOf("原格式")
        compose.setContent {
            AppTheme("light") { Box(Modifier.fillMaxSize()) {
                ComposeChoiceField(value, listOf("原格式", "新格式"), false, Modifier.width(180.dp)) { value = it }
            } }
        }
        compose.onNodeWithText("原格式").performClick()
        compose.onNodeWithText("新格式").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("新格式", value) }
        compose.onNodeWithText("原格式").assertDoesNotExist()
    }

    @Test fun unframedCompactFormatUpdatesValueAndClosesMenu() {
        var value by mutableStateOf("原格式")
        compose.setContent {
            AppTheme("dark") { Box(Modifier.fillMaxSize()) {
                ComposeChoiceField(value, listOf("原格式", "新格式"), true,
                    Modifier.width(180.dp), compact = true) { value = it }
            } }
        }
        compose.onNodeWithText("原格式").performClick()
        compose.onNodeWithText("新格式").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals("新格式", value) }
        compose.onNodeWithText("原格式").assertDoesNotExist()
    }
}
