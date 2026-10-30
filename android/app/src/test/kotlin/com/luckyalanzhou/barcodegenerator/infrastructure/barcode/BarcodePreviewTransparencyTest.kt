package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import android.app.Application
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.luckyalanzhou.barcodegenerator.BarcodeImageColors
import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BarcodePreviewTransparencyTest {
    @Test
    fun renderedPreviewDecodesWithStandardDarkBarsInLightAndDarkThemes() {
        listOf(false, true).forEach { dark ->
            val text = "SCAN-2400-TEST"
            val bitmap = requireNotNull(
                ZxingBarcodeImageRenderer(InMemoryBarcodeImageCache(), AppLogger { _, _, _ -> })
                    .loadOrCreate(CodeItem(1, text, "Code 128-B"), StyleSettings(), dark, 1f),
            )
            val pageBackground = if (dark) Color.BLACK else Color.rgb(243, 245, 248)
            val pixels = IntArray(bitmap.width * bitmap.height)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    pixels[y * bitmap.width + x] = if (Color.alpha(pixel) == 0) pageBackground else pixel
                }
            }
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val reader = MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.CODE_128)))
            }

            assertEquals("深浅主题的最终屏幕位图都应保持标准条码极性并可解码", text,
                reader.decode(BinaryBitmap(HybridBinarizer(source))).text)
        }
    }

    @Test
    fun screenPreviewKeepsDarkBarsAndAddsLightQuietZoneInDarkTheme() {
        listOf(false, true).forEach { dark ->
            val cache = InMemoryBarcodeImageCache()
            val renderer = ZxingBarcodeImageRenderer(cache, AppLogger { _, _, _ -> })

            val bitmap = requireNotNull(
                renderer.loadOrCreate(
                    item = CodeItem(1, "CI512TEST", "Code 128-B"),
                    style = StyleSettings(),
                    dark = dark,
                    density = 1f,
                ),
            )
            val colors = (0 until bitmap.height).flatMap { y ->
                (0 until bitmap.width).map { x -> bitmap.getPixel(x, y) }
            }.toSet()
            val alphaZeroPixelCount = (0 until bitmap.height).sumOf { y ->
                (0 until bitmap.width).count { x -> Color.alpha(bitmap.getPixel(x, y)) == 0 }
            }
            val expectedBarColor = BarcodeImageColors.foreground(dark)
            val expectedBackgroundColor = if (dark) {
                BarcodeImageColors.background(dark)
            } else {
                Color.TRANSPARENT
            }

            assertTrue("预览条码始终使用可扫描的深色条纹", colors.contains(expectedBarColor))
            assertTrue("深色模式提供浅色静区，浅色模式透出页面底色", colors.contains(expectedBackgroundColor))
            assertTrue("深色模式条码静区必须完全不透明", !dark || alphaZeroPixelCount == 0)
            assertTrue("预览位图只包含深色条纹和对应静区", colors.all {
                it == expectedBackgroundColor || it == expectedBarColor
            })
        }
    }

    @Test
    fun resultPageExportKeepsScannerSafePolarityInDarkTheme() {
        listOf(false, true).forEach { dark ->
            val renderer = ZxingBarcodeImageRenderer(InMemoryBarcodeImageCache(), AppLogger { _, _, _ -> })
            val withBackground = dark

            val bitmap = requireNotNull(
                renderer.create(
                    text = "CI514TEST",
                    formatId = BarcodeFormatIds.CODE_128,
                    style = StyleSettings(),
                    dark = dark,
                    density = 1f,
                    withBackground = withBackground,
                ),
            )
            val colors = (0 until bitmap.height).flatMap { y ->
                (0 until bitmap.width).map { x -> bitmap.getPixel(x, y) }
            }.toSet()
            val alphaZeroPixelCount = (0 until bitmap.height).sumOf { y ->
                (0 until bitmap.width).count { x -> Color.alpha(bitmap.getPixel(x, y)) == 0 }
            }
            val expectedBarColor = BarcodeImageColors.foreground(dark)
            val expectedBackgroundColor = if (withBackground) {
                BarcodeImageColors.background(dark)
            } else {
                Color.TRANSPARENT
            }

            assertTrue("结果页导出条码保持深色条纹", colors.contains(expectedBarColor))
            assertTrue("深色结果图使用浅色静区", colors.contains(expectedBackgroundColor))
            assertTrue("深色结果图条码静区必须完全不透明", !dark || alphaZeroPixelCount == 0)
            assertTrue("结果条码只包含深色条纹和对应静区", colors.all {
                it == expectedBackgroundColor || it == expectedBarColor
            })
        }
    }

    private class InMemoryBarcodeImageCache : BarcodeImageCache {
        private val images = mutableMapOf<String, android.graphics.Bitmap>()

        override fun readImage(key: String) = images[key]
        override fun writeImage(key: String, bitmap: android.graphics.Bitmap) {
            images[key] = bitmap
        }
    }
}
