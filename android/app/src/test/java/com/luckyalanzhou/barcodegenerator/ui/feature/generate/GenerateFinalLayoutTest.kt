package com.luckyalanzhou.barcodegenerator.ui.feature.generate

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN", sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GenerateFinalLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val draft = mutableStateOf(listOf("1234567890"))
    private var cameraCalls = 0

    private fun render(dark: Boolean = false, fontScale: Float = 1f, width: Int = 360) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            AppTheme(if (dark) "dark" else "light") {
                Box(Modifier.requiredSize(width.dp, 840.dp)) {
                    GenerateContent(draft.value, "Code 128-B", dark, false, true,
                        onDraftChanged = { draft.value = it }, onFormatChanged = {},
                        onGenerate = { _, _ -> }, onCaptureText = { cameraCalls++ }, onNotice = {})
                }
            }
            }
        }
    }

    @Test fun redundantCountAndExampleAreRemoved() {
        render()
        compose.onNodeWithText("1 条内容").assertDoesNotExist()
        compose.onNodeWithText("示例：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("字母、数字和符号").assertIsDisplayed()
    }

    @Test fun selectedAndUnselectedRowsHaveTheSameOuterBounds() {
        draft.value = listOf("1234567890", "9876543210")
        render()
        compose.onNodeWithContentDescription("第 2 行条码内容").performClick()
        val first = compose.onNodeWithTag("generate-row-0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("generate-row-1").fetchSemanticsNode().boundsInRoot
        val delete = compose.onNodeWithContentDescription("删除").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, .1f)
        assertEquals(first.right, second.right, .1f)
        assertTrue(delete.left >= second.left && delete.right <= second.right)
    }

    @Test fun inputsGrowNaturallyThenStopGrowing() {
        render()
        val initialY = compose.onNodeWithText("生成 1 个条码").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { draft.value = List(3) { "item-$it" } }
        val thirdY = compose.onNodeWithText("生成 3 个条码").fetchSemanticsNode().boundsInRoot.top
        assertTrue(thirdY > initialY)
        compose.runOnIdle { draft.value = List(5) { "item-$it" } }
        val fifthHeight = compose.onNodeWithTag("generate-inputs").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { draft.value = List(15) { "item-$it" } }
        val fifteenthHeight = compose.onNodeWithTag("generate-inputs").fetchSemanticsNode().boundsInRoot.height
        assertTrue("输入区应继续增长到设备可用高度", fifteenthHeight > fifthHeight)
        compose.runOnIdle { draft.value = List(30) { "item-$it" } }
        assertEquals(
            "输入区达到可用高度后应停止增长",
            fifteenthHeight,
            compose.onNodeWithTag("generate-inputs").fetchSemanticsNode().boundsInRoot.height,
            .1f,
        )
        compose.onNodeWithText("生成 30 个条码").assertExists()
    }

    @Test fun draggingAcrossIndependentActionsDoesNotExecuteThem() {
        render(dark = true)
        val start = compose.onNodeWithText("添加一行").fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithText("拍照填充").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); moveTo(Offset((start.x + end.x) / 2, start.y)); moveTo(end) }
        compose.runOnIdle { assertEquals(0, cameraCalls); assertEquals(1, draft.value.size) }
        compose.onRoot().performTouchInput { up() }
        compose.runOnIdle { assertEquals(0, cameraCalls); assertEquals(1, draft.value.size) }
        compose.onRoot().performTouchInput { down(start); moveTo(end); cancel() }
        compose.runOnIdle { assertEquals(0, cameraCalls); assertEquals(1, draft.value.size) }
        compose.onNodeWithText("添加一行").performClick()
        compose.runOnIdle { assertEquals(2, draft.value.size) }
        compose.onNodeWithText("拍照填充").performClick()
        compose.runOnIdle { assertEquals(1, cameraCalls) }
    }

    @Test fun outsideVerticalAndMultiplePointersDoNotExecuteAnAction() {
        render()
        val start = compose.onNodeWithText("添加一行").fetchSemanticsNode().boundsInRoot.center
        val end = compose.onNodeWithText("拍照填充").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(start); moveTo(end); moveTo(end + Offset(0f, 180f)); up() }
        compose.onRoot().performTouchInput { down(start); moveTo(start + Offset(0f, 100f)); up() }
        compose.onRoot().performTouchInput {
            down(0, start); down(1, end); moveTo(0, end); up(1); up(0)
        }
        compose.runOnIdle { assertEquals(0, cameraCalls); assertEquals(1, draft.value.size) }
        compose.onNodeWithText("批量粘贴").performClick()
        compose.onNodeWithText("取消").assertExists()
    }

    @Test fun largeTextKeepsAllActionLabelsVisible() {
        render(fontScale = 2f, width = 320)
        listOf("添加一行", "批量粘贴", "拍照填充").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val result = layouts.single()
            assertFalse("$label clipped: ${result.size}, ${result.layoutInput.constraints}", result.hasVisualOverflow)
        }
    }
}
