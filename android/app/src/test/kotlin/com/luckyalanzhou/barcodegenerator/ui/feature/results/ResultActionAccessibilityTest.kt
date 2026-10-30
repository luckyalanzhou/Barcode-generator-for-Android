package com.luckyalanzhou.barcodegenerator.ui.feature.results

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ResultActionAccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun exportActionsKeepTheirNamesAndExposePreparingState() {
        var exportAction by mutableStateOf(ResultExportAction.Share)
        val item = CodeItem(1L, "ACCESSIBILITY-123", "Code 128-B")

        compose.setContent {
            AppTheme("light") {
                ResultsContent(
                    exportAction = exportAction,
                    state = ResultsContentState(listOf(item), false, false, false),
                    style = StyleSettings(),
                    dark = false,
                    onEdit = {},
                    onSaveFavorite = {},
                    onShare = {},
                    onSave = {},
                    onImageWidthChanged = {},
                    loadBarcodeImage = { _, _, _ -> Bitmap.createBitmap(100, 30, Bitmap.Config.ARGB_8888) },
                    onRenderFailure = { error, _, _ -> throw AssertionError(error) },
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("编辑")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }

        compose.onNodeWithContentDescription("分享")
            .assertExists()
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在准备"))
        compose.onNodeWithContentDescription("保存").assertExists().assertIsNotEnabled()

        compose.runOnIdle { exportAction = ResultExportAction.Save }

        compose.onNodeWithContentDescription("保存")
            .assertExists()
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在准备"))
        compose.onNodeWithContentDescription("分享").assertExists().assertIsNotEnabled()
    }

    @Test
    fun actionLabelsAppearOnLongPressAndActionsRemainClickable() {
        var editClicks = 0
        val item = CodeItem(2L, "TOOLTIP-123", "Code 128-B")

        compose.setContent {
            AppTheme("light") {
                ResultsContent(
                    exportAction = null,
                    state = ResultsContentState(listOf(item), false, false, false),
                    style = StyleSettings(),
                    dark = false,
                    onEdit = { editClicks++ },
                    onSaveFavorite = {},
                    onShare = {},
                    onSave = {},
                    onImageWidthChanged = {},
                    loadBarcodeImage = { _, _, _ -> Bitmap.createBitmap(100, 30, Bitmap.Config.ARGB_8888) },
                    onRenderFailure = { error, _, _ -> throw AssertionError(error) },
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("编辑")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        assertTrue(
            compose.onAllNodesWithText("编辑")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty(),
        )
        compose.onNodeWithContentDescription("编辑").performTouchInput { longClick() }
        compose.onNodeWithText("编辑").assertExists()
        compose.onNodeWithContentDescription("编辑").performClick()
        compose.runOnIdle { assert(editClicks == 1) }
    }
}
