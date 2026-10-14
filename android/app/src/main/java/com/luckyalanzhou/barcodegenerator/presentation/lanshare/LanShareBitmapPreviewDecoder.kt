package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.graphics.Bitmap
import java.io.File

/** App-side port so presentation state does not depend on the LAN module's decoder implementation. */
interface LanShareBitmapPreviewDecoder {
    val maxDecodeEdge: Int

    suspend fun decode(file: File, maxEdge: Int, isTiff: Boolean): Bitmap?
}
