package com.luckyalanzhou.barcodegenerator.ui.app

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraOcrEventCollectorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun recognizedTextUpdatesDraftAndRequestsGenerateTabNavigation() {
        val events = MutableSharedFlow<CameraOcrEvent>(extraBufferCapacity = 2)
        val generateDraft = mutableStateOf(emptyList<String>())
        val notices = mutableListOf<String>()
        var selectedTab = 2
        var navigationCount = 0

        compose.setContent {
            // 此 collector 独立于生成页路由，模拟从其他 Tab 长按后执行拍照填充。
            CameraOcrEventCollector(
                events = events,
                onRecognizedText = { lines, _ -> generateDraft.value = lines; true },
                onNavigateToGenerate = { selectedTab = 0; navigationCount++ },
                onNotice = notices::add,
            )
        }

        val recognizedLines = listOf("first line", "second line")
        compose.runOnIdle {
            assertTrue(events.tryEmit(CameraOcrEvent.RecognizedText(recognizedLines)))
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            generateDraft.value == recognizedLines && notices.size == 1
        }

        compose.runOnIdle {
            assertEquals(recognizedLines, generateDraft.value)
            assertEquals(0, selectedTab)
            assertEquals(1, navigationCount)
            assertEquals(listOf("文字识别成功，已按行添加到输入框"), notices)
        }
    }

    @Test
    fun rejectedOcrResultPreservesDraftAndExplainsWhyItWasNotApplied() {
        val events = MutableSharedFlow<CameraOcrEvent>(extraBufferCapacity = 1)
        val generateDraft = mutableStateOf(listOf("MANUAL-EDIT"))
        val notices = mutableListOf<String>()
        var selectedTab = 2

        compose.setContent {
            CameraOcrEventCollector(
                events = events,
                onRecognizedText = { _, _ -> false },
                onNavigateToGenerate = { selectedTab = 0 },
                onNotice = notices::add,
            )
        }

        compose.runOnIdle {
            assertTrue(
                events.tryEmit(
                    CameraOcrEvent.RecognizedText(
                        lines = listOf("OCR-RESULT"),
                        expectedDraft = listOf("BEFORE-CAPTURE"),
                    ),
                ),
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) { notices.isNotEmpty() }

        compose.runOnIdle {
            assertEquals(listOf("MANUAL-EDIT"), generateDraft.value)
            assertEquals(0, selectedTab)
            assertEquals(listOf("识别完成，但输入内容已更改，未覆盖当前草稿"), notices)
        }
    }
}
