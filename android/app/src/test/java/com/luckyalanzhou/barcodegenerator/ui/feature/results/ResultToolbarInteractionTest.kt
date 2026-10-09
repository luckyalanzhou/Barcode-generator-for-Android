package com.luckyalanzhou.barcodegenerator.ui.feature.results

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 在真实 Compose 语义树中验证四个入口，不用源码字符串代替点击行为。不是真机 GPU 验收。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultToolbarInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightToolbarKeepsFourCallbacksIndependent() = verifyToolbar("light")
    @Test fun darkToolbarKeepsFourCallbacksIndependent() = verifyToolbar("dark")

    private fun verifyToolbar(scheme: String) {
        val actions = mutableListOf<String>()
        val exporting = mutableStateOf<ResultExportAction?>(null)
        compose.setContent {
            AppTheme(scheme) {
                Box(Modifier.fillMaxSize().background(LocalAppColorScheme.current.surfaces.background)) {
                    ResultsContent(exporting.value,
                        ResultsContentState(listOf(CodeItem(1, "123", "CODE_128", 1)), false, false, false),
                        StyleSettings(), scheme == "dark",
                        onEdit = { actions.add("edit") }, onSaveFavorite = { actions.add("favorite") },
                        onShare = { actions.add("share") }, onSave = { actions.add("save") },
                        onImageWidthChanged = {},
                        loadBarcodeImage = { _, _, _ -> Bitmap.createBitmap(100, 30, Bitmap.Config.ARGB_8888) },
                        onRenderFailure = { error, _, _ -> throw AssertionError(error) })
                }
            }
        }
        // 图片准备运行在真实后台线程，Compose 空闲并不代表整批图片已经就绪。
        compose.waitUntil(5_000) {
            compose.onAllNodesWithContentDescription("编辑").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        listOf("编辑", "收藏", "分享", "保存").forEach {
            compose.onNodeWithContentDescription(it).assertIsEnabled().performClick()
        }
        compose.runOnIdle { assertEquals(listOf("edit", "favorite", "share", "save"), actions) }
        compose.runOnIdle { exporting.value = ResultExportAction.Share }
        compose.onNodeWithContentDescription("编辑").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("收藏").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("准备中…").assertIsNotEnabled()
        compose.onNodeWithContentDescription("保存").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf("edit", "favorite", "share", "save", "edit", "favorite"), actions) }
    }
}
