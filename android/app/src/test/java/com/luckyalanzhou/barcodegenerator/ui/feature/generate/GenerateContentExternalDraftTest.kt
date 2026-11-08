package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerateContentExternalDraftTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun externallyReplacedDraftUpdatesVisibleRowsWhileGeneratePageRemainsMounted() {
        val externalDraft = mutableStateOf(listOf("before"))

        compose.setContent {
            AppTheme("light") {
                GenerateContent(
                    inputDraft = externalDraft.value,
                    initialFormat = "Code 128-B",
                    dark = false,
                    isPreparingResult = false,
                    isDataReady = true,
                    onDraftChanged = { externalDraft.value = it },
                    onFormatChanged = {},
                    onGenerate = { _, _ -> },
                    onCaptureText = {},
                    onNotice = {},
                )
            }
        }

        compose.onNodeWithContentDescription("第 1 行条码内容").assertTextEquals("before")
        compose.runOnIdle { externalDraft.value = listOf("OCR-101", "OCR-102") }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("第 1 行条码内容").assertTextEquals("OCR-101")
        compose.onNodeWithContentDescription("第 2 行条码内容").assertTextEquals("OCR-102")
    }
}
