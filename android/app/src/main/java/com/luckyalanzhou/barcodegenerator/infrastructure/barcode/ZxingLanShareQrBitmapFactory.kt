package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareQrBitmapFactory

/** Preserves the LAN QR's existing one-module quiet margin and pixel colors. */
class ZxingLanShareQrBitmapFactory : LanShareQrBitmapFactory {
    override fun create(value: String, foreground: Int, background: Int, size: Int): Bitmap {
        val matrix = MultiFormatWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        return createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { image ->
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                image[x, y] = if (matrix[x, y]) foreground else background
            }
        }
    }
}
