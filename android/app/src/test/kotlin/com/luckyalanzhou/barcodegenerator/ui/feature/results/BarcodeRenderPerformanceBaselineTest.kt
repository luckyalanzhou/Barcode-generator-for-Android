package com.luckyalanzhou.barcodegenerator.ui.feature.results

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.infrastructure.barcode.ZxingBarcodeImageRenderer
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageCache
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.luckyalanzhou.barcodegenerator.ui.theme.AppTheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.RGBLuminanceSource
import org.junit.Assert.assertEquals

/** 本地诊断基准：使用真实 ZXing 与结果行位图，不作为设备 GPU/滚动帧率数据。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BarcodeRenderPerformanceBaselineTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun resultRowsPreserveExactThemeColorsAndExportArgb8888ByDefault() {
        assertTrue("结果行必须保留页面画布的精确颜色", resultPreviewBitmapConfig() == Bitmap.Config.ARGB_8888)
        val cache = InMemoryImageCache()
        val renderer = ZxingBarcodeImageRenderer(cache, AppLogger { _, _, _ -> })
        val style = StyleSettings()
        val item = CodeItem(1, "CONTRAST-0123", "Code 128-B")
        try {
            listOf(false, true).forEach { dark ->
                val barcode = checkNotNull(renderer.loadOrCreate(item, style, dark, 3f))
                val screen = composeResultRowImage(
                    barcode,
                    item,
                    style,
                    dark,
                    ROW_WIDTH_PX,
                    3f,
                    1f,
                    resultPreviewBitmapConfig(),
                )
                val export = composeResultRowImage(barcode, item, style, dark, ROW_WIDTH_PX, 3f, 1f)
                try {
                    assertTrue("默认导出维持 32 位色", export.config == Bitmap.Config.ARGB_8888)
                    assertTrue("屏幕预览保留主题色", screen.config == Bitmap.Config.ARGB_8888)
                    for (y in 0 until screen.height) for (x in 0 until screen.width) {
                        val before = export.getPixel(x, y)
                        val after = screen.getPixel(x, y)
                        assertTrue("像素色差过大 at ($x,$y)", channelDistance(before, after) <= MAX_CHANNEL_DELTA)
                    }
                } finally {
                    screen.recycle()
                    export.recycle()
                }
            }
        } finally {
            cache.values.values.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    @Test
    fun reportsColdGenerationAndRetainedBitmapMemoryForRepresentativeBatches() {
        val measurements = listOf(10, 20, 100).flatMap { count ->
            listOf(Bitmap.Config.ARGB_8888, Bitmap.Config.RGB_565).map { config -> measureBatch(count, config) }
        }
        println("BARCODE_RENDER_BASELINE robolectric-sdk=35 density=3 rowWidthPx=1080")
        measurements.forEach { sample ->
            println(
                "BARCODE_RENDER_BASELINE count=${sample.count} config=${sample.config} medianMs=${sample.medianMs} " +
                    "rawBitmapMiB=${sample.rawBytes / MIB} rowBitmapMiB=${sample.rowBytes / MIB} " +
                    "retainedTotalMiB=${(sample.rawBytes + sample.rowBytes) / MIB}",
            )
        }
        assertTrue("必须对比常见及上限批量规模的两种像素格式", measurements.map { it.count } == listOf(10, 10, 20, 20, 100, 100))
    }

    @Test
    fun reportsQrResultRowMemoryAtMaximumBatchSize() {
        val cache = InMemoryImageCache()
        val renderer = ZxingBarcodeImageRenderer(cache, AppLogger { _, _, _ -> })
        val item = CodeItem(1, "https://example.com/qr-memory-check", "QR Code")
        val raw = checkNotNull(renderer.loadOrCreate(item, StyleSettings(), false, 3f))
        val preparedRows = kotlinx.coroutines.runBlocking {
            prepareResultRows(
                items = listOf(item),
                style = StyleSettings(),
                dark = false,
                density = 3f,
                fontScale = 1f,
                rowWidth = ROW_WIDTH_PX,
                loadBarcodeImage = { _, _, _ -> raw },
                onRowsPrepared = {},
            )
        }
        var row: Bitmap? = null
        var export: Bitmap? = null
        try {
            assertTrue("结果页批量准备路径按原生像素合成 QR", preparedRows.single().width == raw.width)
            row = composeResultRowImage(
                barcode = raw,
                item = item,
                style = StyleSettings(),
                dark = false,
                width = ROW_WIDTH_PX,
                density = 3f,
                fontScale = 1f,
                bitmapConfig = resultPreviewBitmapConfig(),
                rasterWidthLimit = raw.width,
            )
            val oneRowBytes = row.allocationByteCount.toLong()
            val projectedRowsMiB = oneRowBytes * MAX_RESULT_ROWS / MIB
            println(
                "BARCODE_QR_MEMORY_BASELINE raw=${raw.width}x${raw.height} " +
                    "row=${row.width}x${row.height} config=${row.config} " +
                    "oneRowMiB=${oneRowBytes / MIB} projectedRows=${MAX_RESULT_ROWS} " +
                    "projectedRowsMiB=$projectedRowsMiB",
            )
            assertTrue("QR 预览保留原生像素宽度", row.width == raw.width && row.height == raw.height)
            assertTrue("结果预览保留精确画布颜色", row.config == Bitmap.Config.ARGB_8888)
            assertTrue("100 条 QR 结果行预计低于 100 MiB", projectedRowsMiB < 100.0)
            assertEquals("结果行底色必须与浅色页面画布精确一致", resultPageBackground(false), row.getPixel(0, 0))
            val pixels = IntArray(row.width * row.height)
            row.getPixels(pixels, 0, row.width, 0, 0, row.width, row.height)
            val decoded = MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(row.width, row.height, pixels))),
            )
            assertEquals("缩减结果行分辨率后仍可扫码", item.text, decoded.text)

            export = composeResultRowImage(raw, item, StyleSettings(), false, ROW_WIDTH_PX, 3f, 1f)
            assertTrue("导出仍使用原有全宽画布", export.width == ROW_WIDTH_PX)
        } finally {
            preparedRows.forEach { if (!it.isRecycled) it.recycle() }
            row?.let { if (!it.isRecycled) it.recycle() }
            export?.let { if (!it.isRecycled) it.recycle() }
            if (!raw.isRecycled) raw.recycle()
        }
    }

    @Test
    fun qrPreviewRemainsDecodableAfterComposeScalesItForLightAndDarkThemes() {
        val cache = InMemoryImageCache()
        val renderer = ZxingBarcodeImageRenderer(cache, AppLogger { _, _, _ -> })
        val item = CodeItem(7L, "https://example.com/visible-qr-check", "QR Code")
        val style = StyleSettings()
        val rowsByTheme = mutableListOf<Pair<Boolean, Bitmap>>()
        var contentStarted = false
        try {
            listOf(false, true).forEach { dark ->
                val raw = checkNotNull(renderer.loadOrCreate(item, style, dark, 3f))
                val row = composeResultRowImage(
                    barcode = raw,
                    item = item,
                    style = style,
                    dark = dark,
                    width = ROW_WIDTH_PX,
                    density = 3f,
                    fontScale = 1f,
                    bitmapConfig = resultPreviewBitmapConfig(),
                    rasterWidthLimit = raw.width,
                )
                rowsByTheme += dark to row
            }
            val visibleRow = mutableStateOf(rowsByTheme.first())
            compose.setContent {
                val (dark, row) = visibleRow.value
                AppTheme(if (dark) "dark" else "light") {
                    DisposableEffect(row) {
                        onDispose { if (!row.isRecycled) row.recycle() }
                    }
                    Image(
                        bitmap = row.asImageBitmap(),
                        contentDescription = "scaled-qr-preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(300.dp).testTag("scaled-qr-preview"),
                    )
                }
            }
            contentStarted = true
            rowsByTheme.forEach { (dark, row) ->
                compose.runOnIdle { visibleRow.value = dark to row }
                compose.waitForIdle()
                val displayed = compose.onNodeWithTag("scaled-qr-preview").captureToImage().toPixelMap()
                val pixels = IntArray(displayed.width * displayed.height) { index ->
                    displayed[index % displayed.width, index / displayed.width].toArgb()
                }
                val decoded = MultiFormatReader().decode(
                    BinaryBitmap(HybridBinarizer(RGBLuminanceSource(displayed.width, displayed.height, pixels))),
                )
                assertEquals("Compose 缩放后的 ${if (dark) "深色" else "浅色"}结果页 QR 仍可解码", item.text, decoded.text)
            }
        } finally {
            if (!contentStarted) rowsByTheme.forEach { (_, row) -> if (!row.isRecycled) row.recycle() }
            cache.values.values.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    @Test
    fun reportsResultListScrollToEndLatencyForOneHundredPreparedRows() {
        val items = (0 until 100).map { index ->
            CodeItem(index.toLong(), "SCROLL-${index.toString().padStart(3, '0')}", "Code 128-B")
        }
        compose.setContent {
            AppTheme("light") {
                Box(Modifier.fillMaxSize().background(LocalAppColorScheme.current.surfaces.background)) {
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
                        loadBarcodeImage = { _, _, _ ->
                            Bitmap.createBitmap(400, 50, Bitmap.Config.ARGB_8888)
                        },
                        onRenderFailure = { error, _, _ -> throw AssertionError(error) },
                    )
                }
            }
        }
        compose.waitUntil(30_000) {
            compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val startedAt = System.nanoTime()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(99)
        compose.waitForIdle()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        println("BARCODE_SCROLL_BASELINE robolectric-sdk=35 count=100 scrollToEndMs=$elapsedMs")
    }

    private fun measureBatch(count: Int, bitmapConfig: Bitmap.Config): Measurement {
        val durations = mutableListOf<Long>()
        var rawBytes = 0L
        var rowBytes = 0L
        repeat(SAMPLES) { sampleIndex ->
            val cache = InMemoryImageCache()
            val renderer = ZxingBarcodeImageRenderer(cache, AppLogger { _, _, _ -> })
            val rows = ArrayList<Bitmap>(count)
            val startedAt = System.nanoTime()
            try {
                repeat(count) { index ->
                    val item = CodeItem(
                        id = index.toLong(),
                        text = "BATCH-${count}-${sampleIndex}-${index.toString().padStart(4, '0')}",
                        format = "Code 128-B",
                    )
                    val barcode = checkNotNull(renderer.loadOrCreate(item, StyleSettings(), false, 3f))
                    rows += composeResultRowImage(
                        barcode = barcode,
                        item = item,
                        style = StyleSettings(),
                        dark = false,
                        width = ROW_WIDTH_PX,
                        density = 3f,
                        fontScale = 1f,
                        bitmapConfig = bitmapConfig,
                    )
                }
                durations += System.nanoTime() - startedAt
                if (sampleIndex == SAMPLES - 1) {
                    rawBytes = cache.values.values.sumOf(Bitmap::getAllocationByteCount).toLong()
                    rowBytes = rows.sumOf(Bitmap::getAllocationByteCount).toLong()
                }
            } finally {
                rows.forEach(Bitmap::recycle)
                cache.values.values.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
        return Measurement(count, bitmapConfig, durations.sorted()[durations.size / 2] / 1_000_000, rawBytes, rowBytes)
    }

    private class InMemoryImageCache : BarcodeImageCache {
        val values = linkedMapOf<String, Bitmap>()
        override fun readImage(key: String): Bitmap? = values[key]
        override fun writeImage(key: String, bitmap: Bitmap) { values[key] = bitmap }
    }

    private data class Measurement(
        val count: Int,
        val config: Bitmap.Config,
        val medianMs: Long,
        val rawBytes: Long,
        val rowBytes: Long,
    )

    private fun channelDistance(first: Int, second: Int): Int = maxOf(
        kotlin.math.abs(Color.red(first) - Color.red(second)),
        kotlin.math.abs(Color.green(first) - Color.green(second)),
        kotlin.math.abs(Color.blue(first) - Color.blue(second)),
    )

    private companion object {
        const val SAMPLES = 3
        const val ROW_WIDTH_PX = 1080
        const val MAX_RESULT_ROWS = 100
        const val MAX_CHANNEL_DELTA = 8
        const val MIB = 1024.0 * 1024.0
    }
}
