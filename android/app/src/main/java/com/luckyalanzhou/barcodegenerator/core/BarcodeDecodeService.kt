package com.luckyalanzhou.barcodegenerator

import android.graphics.Bitmap
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 条码图片解码服务；负责 ZXing 与 Bitmap 之间的平台转换。 */
class BarcodeDecodeService {
    suspend fun decode(bitmap: Bitmap): String? = withContext(Dispatchers.Default) {
        runCatching {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            MultiFormatReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))),
            ).text
        }.getOrNull()
    }
}
