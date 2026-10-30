package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import android.app.Application
import com.luckyalanzhou.barcodegenerator.BarcodeFormatIds
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeImageCache
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ZxingBarcodeImageRendererLoggerFailureTest {
    @Test
    fun failedEncodingStillReturnsMissingImageWhenDiagnosticLoggerThrows() {
        var loggerWasCalled = false
        val renderer = ZxingBarcodeImageRenderer(
            imageCache = EmptyBarcodeImageCache,
            logger = AppLogger { _, _, _ ->
                loggerWasCalled = true
                throw IllegalStateException("日志暂时不可用")
            },
        )

        val result = renderer.create(
            text = "中文不能被 Code 128 编码",
            formatId = BarcodeFormatIds.CODE_128,
            style = StyleSettings(),
            dark = false,
            density = 1f,
            withBackground = true,
        )

        assertTrue("编码失败应尝试记录诊断", loggerWasCalled)
        assertNull("编码失败仍应按既有策略返回无图", result)
    }

    private object EmptyBarcodeImageCache : BarcodeImageCache {
        override fun readImage(key: String) = null
        override fun writeImage(key: String, bitmap: android.graphics.Bitmap) = Unit
    }
}
