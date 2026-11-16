package com.luckyalanzhou.barcodegenerator.ui.dialogs

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN", sdk = [35], application = Application::class)
class UpdateAvailableDialogContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun ignoreLaterAndUpdateActionsCallOnlyTheirOwnHandlers() {
        var ignored = 0
        var later = 0
        var updated = 0
        compose.setContent {
            AppTheme("light") {
                UpdateAvailableDialogContent(
                    latest = "1.2.3",
                    dark = false,
                    onIgnore = { ignored++ },
                    onLater = { later++ },
                    onUpdate = { updated++ },
                )
            }
        }

        compose.onNodeWithText("忽略更新").performClick()
        assertEquals(1, ignored)
        assertEquals(0, later)
        assertEquals(0, updated)

        compose.onNodeWithText("稍后更新").performClick()
        assertEquals(1, later)
        assertEquals(0, updated)

        compose.onNodeWithText("立即更新").performClick()
        assertEquals(1, updated)
    }

    @Test
    fun allActionsRemainVisibleOnANarrowDialogAtLargeFontScale() {
        compose.setContent {
            AppTheme("light") {
                val density = LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalDensity provides Density(density.density, 2f),
                ) {
                    Box(Modifier.width(320.dp).testTag("update-dialog-bounds")) {
                        UpdateAvailableDialogContent(
                            latest = "1.2.3",
                            dark = false,
                            onIgnore = {},
                            onLater = {},
                            onUpdate = {},
                        )
                    }
                }
            }
        }

        val dialogBounds = compose.onNodeWithTag("update-dialog-bounds").fetchSemanticsNode().boundsInRoot
        val actionNodes = listOf("忽略更新", "稍后更新", "立即更新").map(compose::onNodeWithText)
        actionNodes.forEach { it.assertIsDisplayed() }
        actionNodes.forEach { action ->
            val bounds = action.fetchSemanticsNode().boundsInRoot
            assertEquals("action must stay within the narrow dialog", true,
                bounds.left >= dialogBounds.left && bounds.right <= dialogBounds.right)
        }
    }
}
