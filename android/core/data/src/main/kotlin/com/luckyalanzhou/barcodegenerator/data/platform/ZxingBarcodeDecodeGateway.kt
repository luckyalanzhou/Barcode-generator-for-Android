package com.luckyalanzhou.barcodegenerator.data.platform

import android.graphics.BitmapFactory
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDecodeGateway
import com.luckyalanzhou.barcodegenerator.domain.ImagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android/ZXing adapter; the presentation layer sees only BarcodeDecodeGateway. */
class ZxingBarcodeDecodeGateway : BarcodeDecodeGateway {
    override suspend fun decode(image: ImagePayload): String? = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size) ?: return@runCatching null
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                MultiFormatReader().decode(
                    BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
                ).text
            } finally {
                bitmap.recycle()
            }
        }.getOrNull()
    }
}
