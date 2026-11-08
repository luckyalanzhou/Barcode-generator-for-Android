package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerateRowActionsTest {
    @get:Rule val compose = createComposeRule()
    private val draft = mutableStateOf(listOf("A", "B", "C"))

    private fun render() {
        compose.setContent {
            AppTheme("light") {
                GenerateContent(inputDraft = draft.value, initialFormat = "Code 128-B", dark = false,
                    isPreparingResult = false, isDataReady = true, onDraftChanged = { draft.value = it },
                    onFormatChanged = {}, onGenerate = { _, _ -> }, onCaptureText = {}, onNotice = {})
            }
        }
    }

    @Test fun actionsOnlyAppearForSelectedRowAndFollowItThroughRepeatedMoves() {
        render()
        compose.onAllNodesWithContentDescription("删除").assertCountEquals(0)
        compose.onNodeWithContentDescription("第 2 行条码内容").performClick()
        compose.onAllNodesWithContentDescription("删除").assertCountEquals(1)
        val height = compose.onNodeWithContentDescription("第 2 行条码内容").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithContentDescription("上移").performClick()
        compose.runOnIdle { assertEquals(listOf("B", "A", "C"), draft.value) }
        compose.onNodeWithContentDescription("第 1 行条码内容").assertTextEquals("B").assertIsFocused()
        compose.onNodeWithContentDescription("上移").assertIsNotEnabled()
        compose.onNodeWithContentDescription("下移").performClick()
        compose.onNodeWithContentDescription("下移").performClick()
        compose.runOnIdle { assertEquals(listOf("A", "C", "B"), draft.value) }
        compose.onNodeWithContentDescription("第 3 行条码内容").assertTextEquals("B").assertIsFocused()
        compose.onNodeWithContentDescription("下移").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(height, compose.onNodeWithContentDescription("第 3 行条码内容").fetchSemanticsNode().boundsInRoot.height)
        }
    }

    @Test fun undoRestoresDeletedRowWithoutOverwritingSubsequentEdits() {
        render()
        compose.onNodeWithContentDescription("第 2 行条码内容").performClick()
        compose.onNodeWithContentDescription("删除").performClick()
        compose.onNodeWithContentDescription("第 2 行条码内容").assertIsFocused()
        compose.onNodeWithContentDescription("第 1 行条码内容").performTextReplacement("CHANGED")
        compose.onNodeWithText("撤销").performClick()
        compose.runOnIdle { assertEquals(listOf("CHANGED", "B", "C"), draft.value) }
    }

    @Test fun undoAfterDeletingOnlyRowPreservesNewPlaceholderContent() {
        draft.value = listOf("OLD")
        render()
        compose.onNodeWithContentDescription("第 1 行条码内容").performClick()
        compose.onNodeWithContentDescription("删除").performClick()
        compose.onNodeWithContentDescription("第 1 行条码内容").assertIsFocused()
        compose.onNodeWithContentDescription("第 1 行条码内容").performTextReplacement("NEW")
        compose.onNodeWithText("撤销").performClick()
        compose.runOnIdle { assertEquals(listOf("OLD", "NEW"), draft.value) }
    }
}
