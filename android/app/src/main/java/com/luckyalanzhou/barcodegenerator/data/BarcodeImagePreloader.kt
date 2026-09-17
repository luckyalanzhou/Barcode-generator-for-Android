package com.luckyalanzhou.barcodegenerator

/** 启动后后台补齐缺失条码图片；不阻塞首屏，也不删除已有缓存。 */
internal fun MainActivity.prewarmBarcodeImages() {
    viewModel.prewarmBarcodeImages(settingsViewModel.style, barcodeFormats, isDark(), resources.displayMetrics.density)
}

