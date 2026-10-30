package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.graphics.Bitmap

/** App 层创建局域网分享页面二维码图片的接口。 */
fun interface LanShareQrBitmapFactory {
    fun create(value: String, foreground: Int, background: Int, size: Int): Bitmap
}
