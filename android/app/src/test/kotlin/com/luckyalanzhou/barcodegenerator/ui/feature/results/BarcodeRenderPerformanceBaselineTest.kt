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
import androidx.compose.ui.graphics.asAndroidBitmap
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
import java.util.concurrent.ConcurrentHashMap

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
    fun preparedRowLayoutMatchesCompositeDimensionsAcrossDensityAndFontScale() {
        val cache = InMemoryImageCache()
        val renderer = ZxingBarcodeImageRenderer(cache, AppLogger { _, _, _ -> })
        val style = StyleSettings()
        try {
            listOf("Code 128-B", "QR Code").forEachIndexed { index, format ->
                listOf(1f, 3f).forEach { density ->
                    listOf(1f, 1.5f).forEach { fontScale ->
                    val item = CodeItem(index.toLong(), "LAYOUT-${format.take(2)}-0123456789", format)
                    val raw = checkNotNull(renderer.loadOrCreate(item, style, false, density))
                    val layout = resultImageRowLayout(
                        barcode = raw,
                        item = item,
                        style = style,
                        dark = false,
                        width = if (format == "Code 128-B") ROW_WIDTH_PX else minOf(ROW_WIDTH_PX, raw.width),
                        density = density,
                        fontScale = fontScale,
                    )
                    val composite = composeResultRowImage(
                        barcode = raw,
                        item = item,
                        style = style,
                        dark = false,
                        width = ROW_WIDTH_PX,
                        density = density,
                        fontScale = fontScale,
                        rasterWidthLimit = raw.width,
                    )
                    try {
                        assertEquals("${format} density=$density fontScale=$fontScale row width", composite.width, layout.width)
                        assertEquals("${format} density=$density fontScale=$fontScale row height", composite.height, layout.height)
                        assertTrue("barcode bounds must fit the row", layout.barcodeLeft >= 0 &&
                            layout.barcodeLeft + layout.barcodeWidth <= layout.width &&
                            layout.barcodeTop + layout.barcodeHeight <= layout.height)
                        if (format == "Code 128-B") {
                            assertTrue("Code 128 label starts below its barcode", layout.labelTop != null &&
                                layout.labelTop >= layout.barcodeTop + layout.barcodeHeight)
                            assertTrue("Code 128 label height matches its row", layout.labelHeight > 0 &&
                                layout.labelTop!! + layout.labelHeight <= layout.height)
                        } else {
                            assertEquals("QR rows have no text label", null, layout.labelTop)
                            assertEquals(0, layout.labelHeight)
                        }
                    } finally {
                        composite.recycle()
                    }
                }
            }
            }
        } finally {
            cache.values.values.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    @Test
    fun preparedResultRowKeepsBarcodeAndLabelAligned() {
        val cache = InMemoryImageCache()
        val renderer = ZxingBarcodeImageRenderer(cache, AppLogger { _, _, _ -> })
        val style = StyleSettings(barWidth = 300f, barHeight = 80)
        val cases = listOf("Code 128-B", "QR Code").flatMapIndexed { formatIndex, format ->
            listOf(false, true).mapIndexed { themeIndex, dark ->
                val text = if (format == "Code 128-B") "ALIGNED-123456" else "https://example.com/aligned-qr"
                val item = CodeItem(formatIndex * 2L + themeIndex, text, format)
                val raw = checkNotNull(renderer.loadOrCreate(item, style, dark, 1f))
                val layout = resultImageRowLayout(raw, item, style, dark, 320, 1f, 1f)
                PreparedResultRow(item, raw, layout) to dark
            }
        }
        val visible = mutableStateOf(cases.first())
        try {
            compose.setContent {
                val (row, dark) = visible.value
                AppTheme(if (dark) "dark" else "light") {
                    PreparedResultRowView(
                        row = row,
                        dark = dark,
                        modifier = Modifier.size(row.layout.width.dp, row.layout.height.dp).testTag("prepared-result-row"),
                    )
                }
            }
            cases.forEach { (row, dark) ->
                compose.runOnIdle { visible.value = row to dark }
                compose.waitForIdle()
                val captured = compose.onNodeWithTag("prepared-result-row").captureToImage().asAndroidBitmap()
                val composite = composeResultRowImage(
                    barcode = row.barcode,
                    item = row.item,
                    style = style,
                    dark = dark,
                    width = 320,
                    density = 1f,
                    fontScale = 1f,
                    rasterWidthLimit = row.barcode.width,
                )
                try {
                    assertEquals(row.layout.width, captured.width)
                    assertEquals(row.layout.height, captured.height)
                    assertEquals("预览条码与标签位置应和导出布局一致 (${row.item.format}, dark=$dark)",
                        composite.width * composite.height, captured.width * captured.height)
                    val expectedPixels = IntArray(composite.width * composite.height)
                    val actualPixels = IntArray(captured.width * captured.height)
                    composite.getPixels(expectedPixels, 0, composite.width, 0, 0, composite.width, composite.height)
                    captured.getPixels(actualPixels, 0, captured.width, 0, 0, captured.width, captured.height)
                    assertTrue("预览行像素位置应和共享布局一致 (${row.item.format}, dark=$dark)", expectedPixels.contentEquals(actualPixels))

                    val barcode = Bitmap.createBitmap(
                        captured,
                        row.layout.barcodeLeft,
                        row.layout.barcodeTop,
                        row.layout.barcodeWidth,
                        row.layout.barcodeHeight,
                    )
                    try {
                        val pixels = IntArray(barcode.width * barcode.height)
                        barcode.getPixels(pixels, 0, barcode.width, 0, 0, barcode.width, barcode.height)
                        val decoded = try {
                            MultiFormatReader().decode(
                                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(barcode.width, barcode.height, pixels))),
                            )
                        } catch (error: Exception) {
                            throw AssertionError("Canvas barcode decode failed (${row.item.format}, dark=$dark)", error)
                        }
                        assertEquals("结果页 Canvas 绘制的条码应可解码 (${row.item.format}, dark=$dark)", row.item.text, decoded.text)
                    } finally {
                        barcode.recycle()
                    }
                } finally {
                    captured.recycle()
                    composite.recycle()
                }
            }
        } finally {
            cache.values.values.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    @Test
    fun reportsColdGenerationAndRetainedBitmapMemoryForRepresentativeBatches() {
        val preparedMeasurements = listOf(10, 20, 100).map(::measurePreparedBatch)
        val exportMeasurements = listOf(10, 20, 100).flatMap { count ->
            listOf(Bitmap.Config.ARGB_8888, Bitmap.Config.RGB_565).map { config -> measureExportBatch(count, config) }
        }
        println("BARCODE_RESULT_PREPARED robolectric-sdk=35 density=3 rowWidthPx=1080")
        preparedMeasurements.forEach { sample ->
            println(
                "BARCODE_RESULT_PREPARED count=${sample.count} medianMs=${sample.medianMs} " +
                    "rawBitmapMiB=${sample.rawBytes / MIB} rowBitmapMiB=${sample.rowBytes / MIB} " +
                    "retainedTotalMiB=${(sample.rawBytes + sample.rowBytes) / MIB}",
            )
        }
        println("BARCODE_EXPORT_COMPOSITE robolectric-sdk=35 density=3 rowWidthPx=1080")
        exportMeasurements.forEach { sample ->
            println(
                "BARCODE_EXPORT_COMPOSITE count=${sample.count} config=${sample.config} medianMs=${sample.medianMs} " +
                    "rawBitmapMiB=${sample.rawBytes / MIB} rowBitmapMiB=${sample.rowBytes / MIB} " +
                    "retainedTotalMiB=${(sample.rawBytes + sample.rowBytes) / MIB}",
            )
        }
        assertEquals("结果预览不分配合成行位图", listOf(0L, 0L, 0L), preparedMeasurements.map { it.rowBytes })
        assertEquals("结果准备需要覆盖 10、20、100 条", listOf(10, 20, 100), preparedMeasurements.map { it.count })
        assertTrue("100 条预览位图驻留至少降低一半", preparedMeasurements.last().rawBytes + preparedMeasurements.last().rowBytes < 70.5 * MIB)
        assertTrue("100 条准备中位耗时较 HEAD 453ms 至少降低 20%", preparedMeasurements.last().medianMs <= 362)
        assertEquals("保留独立的全宽合成导出基线", listOf(10, 10, 20, 20, 100, 100), exportMeasurements.map { it.count })
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
        var export: Bitmap? = null
        try {
            assertTrue("结果页批量准备路径保留原始 QR", preparedRows.single().barcode === raw)
            assertTrue("QR 行布局维持原始像素宽度", preparedRows.single().layout.width == raw.width)
            val rawBitmapMiB = raw.allocationByteCount.toLong() * MAX_RESULT_ROWS / MIB
            println(
                "BARCODE_QR_PREPARED raw=${raw.width}x${raw.height} config=${raw.config} " +
                    "projectedRawMiB=$rawBitmapMiB previewRowMiB=0 retainedTotalMiB=$rawBitmapMiB",
            )
            assertTrue("100 条 QR 仅保留原图的估算内存低于 100 MiB", rawBitmapMiB < 100.0)
            export = composeResultRowImage(raw, item, StyleSettings(), false, ROW_WIDTH_PX, 3f, 1f)
            assertTrue("导出仍使用原有全宽画布", export.width == ROW_WIDTH_PX)
            assertEquals("导出背景色保持浅色页面颜色", resultPageBackground(false), export.getPixel(0, 0))
            val pixels = IntArray(export.width * export.height)
            export.getPixels(pixels, 0, export.width, 0, 0, export.width, export.height)
            val decoded = MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(export.width, export.height, pixels))),
            )
            assertEquals("全宽 PNG 导出路径仍可扫码", item.text, decoded.text)
        } finally {
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

    private fun measurePreparedBatch(count: Int): Measurement {
        val durations = mutableListOf<Long>()
        var rawBytes = 0L
        repeat(SAMPLES) { sampleIndex ->
            val cache = InMemoryImageCache()
            val renderer = ZxingBarcodeImageRenderer(cache, AppLogger { _, _, _ -> })
            val items = (0 until count).map { index ->
                CodeItem(
                    id = index.toLong(),
                    text = "BATCH-${count}-${sampleIndex}-${index.toString().padStart(4, '0')}",
                    format = "Code 128-B",
                )
            }
            try {
                val startedAt = System.nanoTime()
                val rows = kotlinx.coroutines.runBlocking {
                    prepareResultRows(
                        items = items,
                        style = StyleSettings(),
                        dark = false,
                        density = 3f,
                        fontScale = 1f,
                        rowWidth = ROW_WIDTH_PX,
                        loadBarcodeImage = { item, dark, density ->
                            renderer.loadOrCreate(item, StyleSettings(), dark, density)
                        },
                        onRowsPrepared = {},
                    )
                }
                durations += System.nanoTime() - startedAt
                assertEquals(count, rows.size)
                assertTrue("准备列表只持有缓存中的原始图", rows.all { it.barcode in cache.values.values })
                if (sampleIndex == SAMPLES - 1) {
                    rawBytes = cache.values.values.sumOf(Bitmap::getAllocationByteCount).toLong()
                }
            } finally {
                cache.values.values.forEach { if (!it.isRecycled) it.recycle() }
            }
        }
        return Measurement(count, Bitmap.Config.ARGB_8888, durations.sorted()[durations.size / 2] / 1_000_000, rawBytes, 0L)
    }

    private fun measureExportBatch(count: Int, bitmapConfig: Bitmap.Config): Measurement {
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
        // Result preparation deliberately renders in parallel, so the fixture must model a thread-safe cache.
        val values = ConcurrentHashMap<String, Bitmap>()
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
