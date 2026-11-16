package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.LanShareInputBar
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 输入框在空值和填写后都保持稳定、明确的辅助技术名称。 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN", sdk = [35], application = Application::class)
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
                )
            }
        }

        compose.onNodeWithContentDescription("第 1 行条码内容").performTextInput("TEST-123")
    }

    @Test
    fun hundredthInputRowNumberStaysOnOneLineAtPhoneWidth() {
        compose.setContent {
            AppTheme("light") {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    Box(Modifier.width(360.dp)) {
                        ComposeGenerateInputPanel(
                            values = List(100) { "VALUE" },
                            dark = false,
                            focusedIndex = -1,
                            onValueChange = { _, _ -> },
                            onFocus = {},
                            onMoveUp = {},
                            onMoveDown = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }

        val rowNumber = compose.onNodeWithText("100", useUnmergedTree = true)
        rowNumber.performScrollTo()
        rowNumber.assertIsDisplayed()
        val numberBounds = rowNumber.fetchSemanticsNode().boundsInRoot
        val numberHeight = with(compose.density) { numberBounds.height.toDp() }
        assertTrue("The 100th row number should fit on one line, got $numberHeight", numberHeight < 40.dp)
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
    fun tappingBlankSpaceBelowBatchPastePlaceholderFocusesTheField() {
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
        val field = compose.onNodeWithContentDescription("多行条码内容")
        field.performTouchInput {
            down(Offset(center.x, center.y + 60.dp.toPx()))
            up()
        }
        field.assertIsFocused()
    }

    @Test
    fun batchPastePreviewExplainsCountsAndWaitsForConfirmation() {
        var draft by mutableStateOf(listOf("EXISTING"))
        compose.setContent {
            AppTheme("light") {
                GenerateContent(
                    inputDraft = draft,
                    initialFormat = "Code 128-B",
                    dark = false,
                    isPreparingResult = false,
                    isDataReady = true,
                    onDraftChanged = { draft = it },
                    onFormatChanged = {},
                    onGenerate = { _, _ -> },
                    onCaptureText = {},
                    onNotice = {},
                )
            }
        }

        compose.onNodeWithText("批量粘贴").performClick()
        compose.onNodeWithContentDescription("多行条码内容").performTextInput("ONE\n\nTWO")

        compose.onNodeWithContentDescription("多行条码内容").assertTextEquals("ONE\n\nTWO")
        compose.onNodeWithText("将添加 2 条；添加后共 3 条。", substring = false).assertExists()
        assertEquals(listOf("EXISTING"), draft)
    }

    @Test
    fun batchPasteShowsMalformedLineInlineAndDisablesAdd() {
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
        compose.onNodeWithContentDescription("多行条码内容").performTextInput("ONE\nTWO,THREE")

        compose.onNodeWithText(
            "第 2 行：每行只能有一个条码值；含逗号或双引号的值请用双引号括起",
            substring = false,
        ).assertIsDisplayed()
        compose.onNodeWithText("添加", substring = false).assertIsNotEnabled()
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
