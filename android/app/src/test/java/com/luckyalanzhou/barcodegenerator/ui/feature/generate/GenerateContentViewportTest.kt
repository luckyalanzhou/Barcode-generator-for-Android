package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
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
class GenerateContentViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun generateActionFitsShortViewportWithOneInputRow() {
        compose.setContent { contentInShortViewport(listOf("one")) }

        compose.onNodeWithText("生成 1 个条码").assertIsDisplayed()
    }

    @Test
    fun generateActionRemainsReachableWithMultipleRowsWhenViewportShrinks() {
        compose.setContent { contentInShortViewport(listOf("one", "two", "three")) }

        val generateAction = compose.onNodeWithText("生成 3 个条码")
        generateAction.assertIsNotDisplayed()

        compose.onRoot().performTouchInput { swipeUp() }

        generateAction.assertIsDisplayed()
    }

    @Test
    fun generateActionRemainsReachableAfterSeveralRowsAreAddedAndPageIsScrolled() {
        compose.setContent { contentInShortViewport(List(5) { "row-$it" }) }

        val action = compose.onNodeWithText("生成 5 个条码")
        action.assertIsNotDisplayed()
        repeat(2) { compose.onRoot().performTouchInput { swipeUp() } }
        action.assertIsDisplayed()
    }

    @Test
    fun failedLibraryLoadShowsReachableRetryAction() {
        var retryCount = 0
        compose.setContent {
            contentInShortViewport(
                inputDraft = listOf("one"),
                isDataReady = false,
                isDataLoadFailed = true,
                viewportHeight = 520.dp,
                onRetryDataLoad = { retryCount++ },
            )
        }

        compose.onNodeWithText("数据读取失败，暂时无法生成条码").assertIsDisplayed()
        compose.onNodeWithText("重试读取").performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun selectedBarcodeFormatShowsItsUseGuidance() {
        compose.setContent { contentInShortViewport(listOf("one")) }

        compose.onNodeWithText("字母、数字和符号").assertIsDisplayed()
        compose.onNodeWithText("Code 128-B").performClick()
        compose.onNodeWithText("QR Code").performClick()

        compose.onNodeWithText("网址或较长文本").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun contentInShortViewport(
        inputDraft: List<String>,
        isDataReady: Boolean = true,
        isDataLoadFailed: Boolean = false,
        viewportHeight: androidx.compose.ui.unit.Dp = 320.dp,
        onRetryDataLoad: () -> Unit = {},
    ) {
        AppTheme("light") {
            Box(Modifier.requiredSize(width = 360.dp, height = viewportHeight).clipToBounds()) {
                GenerateContent(
                    inputDraft = inputDraft,
                    initialFormat = "Code 128-B",
                    dark = false,
                    isPreparingResult = false,
                    isDataReady = isDataReady,
                    isDataLoadFailed = isDataLoadFailed,
                    onDraftChanged = {},
                    onFormatChanged = {},
                    onGenerate = { _, _ -> },
                    onCaptureText = {},
                    onNotice = {},
                    onRetryDataLoad = onRetryDataLoad,
                )
            }
        }
    }
}
