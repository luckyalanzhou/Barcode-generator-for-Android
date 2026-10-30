package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.LanShareInputBar
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 输入框在空值和填写后都保持稳定、明确的辅助技术名称。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InputFieldAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun generatedBarcodeRowsExposeTheirRowNumberAsTheInputLabel() {
        compose.setContent {
            AppTheme("light") {
                ComposeGenerateInputPanel(
                    values = listOf(""),
                    dark = false,
                    focusedIndex = -1,
                    onValueChange = { _, _ -> },
                    onFocus = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onDelete = {},
                    onDeleteLongClick = {},
                )
            }
        }

        compose.onNodeWithContentDescription("第 1 行条码内容").performTextInput("TEST-123")
    }

    @Test
    fun batchPasteFieldExposesItsPurposeAsTheInputLabel() {
        compose.setContent {
            AppTheme("light") {
                GenerateContent(
                    inputDraft = listOf(""),
                    initialFormat = "Code 128-B",
                    dark = false,
                    isPreparingResult = false,
                    isDataReady = true,
                    onDraftChanged = {},
                    onFormatChanged = {},
                    onGenerate = { _, _ -> },
                    onCaptureText = {},
                    onNotice = {},
                )
            }
        }

        compose.onNodeWithText("批量粘贴").performClick()
        compose.onNodeWithContentDescription("多行条码内容").performTextInput("TEST-123")
    }

    @Test
    fun lanShareComposerExposesMessagePurposeAsTheInputLabel() {
        compose.setContent {
            AppTheme("light") {
                val colors = LocalAppColorScheme.current
                val message = remember { mutableStateOf("") }
                Box(Modifier.fillMaxSize()) {
                    LanShareInputBar(
                        dark = false,
                        panel = colors.surfaces.panel,
                        inputPanel = colors.surfaces.input,
                        primary = colors.text.primary,
                        secondary = colors.text.secondary,
                        accent = colors.controls.accent,
                        pendingUploadName = null,
                        message = message.value,
                        onMessageChange = { message.value = it },
                        onSend = {},
                        onOpenAttachmentMenu = {},
                        attachmentMenu = false,
                        onDismissAttachmentMenu = {},
                        onOpenCamera = {},
                        onOpenGallery = {},
                        onOpenFiles = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("消息内容").performTextInput("TEST-123")
    }
}
