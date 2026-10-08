package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import android.graphics.Bitmap

/** App-boundary port for the QR bitmap displayed by the LAN share screen. */
fun interface LanShareQrBitmapFactory {
    fun create(value: String, foreground: Int, background: Int, size: Int): Bitmap
}
