package com.luckyalanzhou.barcodegenerator.ui.feature.results

import android.app.Application
import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ResultImageLayoutTest {
    private val item = CodeItem(1, "ABC-123", "Code 128-B")
    @Test fun exportAndScreenLabelsOnlyShowBarcodeContent() {
        assertEquals("ABC-123", resultImageLabel(item))
    }
    @Test fun spacingIsDensityScaledAndLimitedToTheSettingsRange() {
        assertEquals(14, resultImageSpacing(4, 3.5f))
        assertEquals(0, resultImageSpacing(-1, 3.5f))
        assertEquals(35, resultImageSpacing(99, 3.5f))
    }

    @Test fun nonCode128RowsCanBeSizedWithoutAllocatingBitmaps() {
        val qrSize = resultImageRowSize(
            CodeItem(2, "QR-CONTENT", "QR Code"),
            StyleSettings(),
            width = 900,
            density = 1f,
            fontScale = 1f,
        )
        val linearSize = resultImageRowSize(
            CodeItem(3, "EAN-CONTENT", "EAN-13"),
            StyleSettings(),
            width = 900,
            density = 1f,
            fontScale = 1f,
        )

        assertEquals(ResultImageRowSize(900, 900), qrSize)
        assertEquals(ResultImageRowSize(900, 360), linearSize)
    }

    @Test fun legacyFormatAliasesKeepTheirCanonicalResultLayout() {
        val style = StyleSettings()
        val qr = resultImageRowSize(
            CodeItem(4, "QR-CONTENT", "QR_CODE"),
            style,
            width = 900,
            density = 1f,
            fontScale = 1f,
        )
        val canonicalQr = resultImageRowSize(
            CodeItem(5, "QR-CONTENT", "QR Code"),
            style,
            width = 900,
            density = 1f,
            fontScale = 1f,
        )
        assertEquals("legacy QR aliases must retain QR aspect ratio", canonicalQr, qr)

        val bitmap = Bitmap.createBitmap(320, 80, Bitmap.Config.ARGB_8888)
        try {
            val layout = resultImageRowLayout(
                bitmap,
                CodeItem(6, "CODE128-CONTENT", "CODE_128"),
                style,
                dark = false,
                width = 900,
                density = 1f,
                fontScale = 1f,
            )
            val canonicalLayout = resultImageRowLayout(
                bitmap,
                CodeItem(7, "CODE128-CONTENT", "Code 128-B"),
                style,
                dark = false,
                width = 900,
                density = 1f,
                fontScale = 1f,
            )
            assertNotNull("legacy Code 128 aliases must keep the text label", layout.labelTop)
            assertEquals(canonicalLayout.barcodeWidth, layout.barcodeWidth)
            assertEquals(canonicalLayout.barcodeHeight, layout.barcodeHeight)
            assertEquals(canonicalLayout.labelHeight, layout.labelHeight)
        } finally {
            bitmap.recycle()
        }
    }
}
