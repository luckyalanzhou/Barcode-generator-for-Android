package com.luckyalanzhou.barcodegenerator.ui.feature.results

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ResultsContentPreparationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun failedPreparationCanRetryInPlaceAndShowTheResults() {
        val attempts = AtomicInteger()
        val failures = AtomicInteger()
        val item = CodeItem(1L, "RETRY-123", "Code 128-B")

        compose.setContent {
            AppTheme("light") {
                ResultsContent(
                    exportAction = null,
                    state = ResultsContentState(listOf(item), false, false, false),
                    style = StyleSettings(),
                    dark = false,
                    onEdit = {},
                    onSaveFavorite = {},
                    onShare = {},
                    onSave = {},
                    onImageWidthChanged = {},
                    loadBarcodeImage = { _, _, _ ->
                        if (attempts.incrementAndGet() == 1) {
                            error("temporary barcode image failure")
                        }
                        Bitmap.createBitmap(100, 30, Bitmap.Config.ARGB_8888)
                    },
                    onRenderFailure = { _, _, _ -> failures.incrementAndGet() },
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("重试", substring = false)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        assertEquals(1, failures.get())

        compose.onNodeWithText("重试", substring = false).performClick()

        compose.waitUntil(10_000) {
            attempts.get() == 2 && compose.onAllNodesWithContentDescription("编辑")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("编辑").assertExists()
        assertEquals(2, attempts.get())
        assertEquals(1, failures.get())
    }

    @Test
    fun preparationShowsCompletedRowsWhileTheRemainingBatchIsLoading() {
        val finalImage = CompletableDeferred<Bitmap?>()
        val items = (1L..9L).map { CodeItem(it, "ROW-$it", "Code 128-B") }

        compose.setContent {
            AppTheme("light") {
                ResultsContent(
                    exportAction = null,
                    state = ResultsContentState(items, false, false, false),
                    style = StyleSettings(),
                    dark = false,
                    onEdit = {},
                    onSaveFavorite = {},
                    onShare = {},
                    onSave = {},
                    onImageWidthChanged = {},
                    loadBarcodeImage = { item, _, _ ->
                        if (item.id == 9L) finalImage.await()
                        else Bitmap.createBitmap(100, 30, Bitmap.Config.ARGB_8888)
                    },
                    onRenderFailure = { error, _, _ -> throw AssertionError(error) },
                )
            }
        }

        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("正在准备条码 8/9…", substring = false)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("条码准备进度").assertExists()
        compose.onNodeWithText("全部准备完成后显示结果", substring = false).assertExists()
        assertEquals("整批完成前不得显示任何已准备条码", 0,
            compose.onAllNodesWithContentDescription("ROW-1")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).size)
        assertEquals("整批完成前不得显示结果工具栏", 0,
            compose.onAllNodesWithContentDescription("编辑")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).size)

        finalImage.complete(Bitmap.createBitmap(100, 30, Bitmap.Config.ARGB_8888))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription("编辑")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("ROW-1").assertExists()
    }

    @Test
    fun preparationReturnsTheSharedRawBitmapAndRowLayout() = runBlocking {
        val item = CodeItem(1L, "RAW-123", "Code 128-B")
        val raw = Bitmap.createBitmap(100, 30, Bitmap.Config.ARGB_8888)
        try {
            val rows = prepareResultRows(
                items = listOf(item),
                style = StyleSettings(),
                dark = false,
                density = 1f,
                fontScale = 1f,
                rowWidth = 320,
                loadBarcodeImage = { _, _, _ -> raw },
                onRowsPrepared = {},
            )
            assertEquals(1, rows.size)
            assertTrue("prepared rows must retain the shared renderer bitmap", rows.single().barcode === raw)
            assertEquals(320, rows.single().layout.width)
            assertEquals(StyleSettings().barWidth.toInt(), rows.single().layout.barcodeWidth)
            assertTrue("preparation must not recycle cache-owned raw bitmaps", !raw.isRecycled)
        } finally {
            if (!raw.isRecycled) raw.recycle()
        }
    }
}
