package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
/** 将当前结果批次合成为一张图片交给系统分享面板。 */
internal fun MainActivity.shareResultPage() {
    val resultItems = viewModel.resultUiState.value.items
    val images = resultItems.mapNotNull { item ->
        viewModel.createBarcodeImage(
            item.text,
            barcodeFormats.firstOrNull { it.first == item.format }?.second ?: com.google.zxing.BarcodeFormat.CODE_128,
            settingsViewModel.style,
            isDark(),
            resources.displayMetrics.density,
        )
    }
    if (images.isEmpty()) {
        toast("没有可分享的条码")
        return
    }
    val width = images.maxOf { it.width }
    val spacing = if (resultItems.all { it.format == "Code 128-B" }) {
        (settingsViewModel.style.margin * resources.displayMetrics.density).toInt().coerceAtLeast(0)
    } else 0
    val height = images.sumOf { it.height } + spacing * (images.size - 1)
    val pageImage = createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(pageImage)
    canvas.drawColor(BarcodeImageColors.background(isDark()))
    var top = 0
    images.forEach { image ->
        canvas.drawBitmap(image, (width - image.width) / 2f, top.toFloat(), null)
        top += image.height + spacing
    }
    shareBitmap(pageImage, "本页生成的 ${images.size} 个条码")
}
