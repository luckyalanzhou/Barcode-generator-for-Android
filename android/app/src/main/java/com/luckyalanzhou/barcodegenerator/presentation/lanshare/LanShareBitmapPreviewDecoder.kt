package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.graphics.Bitmap
import java.io.File

/** App 层解码接口，使页面状态不依赖 LAN 模块的具体解码实现。 */
interface LanShareBitmapPreviewDecoder {
    val maxDecodeEdge: Int

    suspend fun decode(file: File, maxEdge: Int, isTiff: Boolean): Bitmap?
}
