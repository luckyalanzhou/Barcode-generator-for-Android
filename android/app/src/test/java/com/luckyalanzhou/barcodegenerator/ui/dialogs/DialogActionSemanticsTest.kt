package com.luckyalanzhou.barcodegenerator.ui.dialogs

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 共享弹窗操作应向辅助技术暴露按钮语义。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DialogActionSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dialogActionExposesButtonRole() {
        compose.setContent {
            AppTheme("light") {
                DialogAction(text = "保存", dark = false, onClick = {})
            }
        }

        compose.onNodeWithText("保存").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
        )
    }

    @Test
    fun dialogActionHasAtLeastFortyEightDpTouchHeight() {
        compose.setContent {
            AppTheme("light") {
                DialogAction(text = "保存", dark = false, onClick = {})
            }
        }

        compose.onNodeWithText("保存").assertHeightIsAtLeast(48.dp)
    }
}
