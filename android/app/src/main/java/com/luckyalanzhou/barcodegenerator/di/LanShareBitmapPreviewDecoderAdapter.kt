package com.luckyalanzhou.barcodegenerator.di

import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.data.preview.LanShareImagePreviewDecoder
import com.luckyalanzhou.barcodegenerator.data.preview.LanShareTiffPreviewDecoder
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareBitmapPreviewDecoder
import java.io.File

/** 将图片格式库隔离在应用依赖组装边界之后。 */
internal class LanShareBitmapPreviewDecoderAdapter : LanShareBitmapPreviewDecoder {
    override val maxDecodeEdge: Int = LanShareImagePreviewDecoder.MAX_DECODE_EDGE

    override suspend fun decode(file: File, maxEdge: Int, isTiff: Boolean): Bitmap? =
        if (isTiff) LanShareTiffPreviewDecoder.decode(file, maxEdge)
        else LanShareImagePreviewDecoder.decode(file, maxEdge)
}
