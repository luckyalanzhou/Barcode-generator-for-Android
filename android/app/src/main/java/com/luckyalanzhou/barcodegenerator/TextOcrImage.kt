package com.luckyalanzhou.barcodegenerator

/**
 * 屏幕像素网格会制造高频摩尔纹。先适度降采样，再做一次高斯低通，
 * 让网格纹理变弱而不把表格文字锐化成更明显的伪影。
 */
internal fun prepareScreenOcrBitmap(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
    var source = bitmap
    val longest = maxOf(source.width, source.height)
    if (longest > 1400) {
        val scale = 1400f / longest.toFloat()
        source = android.graphics.Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
    }
    if (source.width < 700 || source.height < 500) return source
    val width = source.width
    val height = source.height
    val input = IntArray(width * height)
    val output = IntArray(width * height)
    source.getPixels(input, 0, width, 0, 0, width, height)
    val kernel = intArrayOf(1, 4, 6, 4, 1)
    for (y in 2 until height - 2) {
        for (x in 2 until width - 2) {
            var red = 0
            var green = 0
            var blue = 0
            for (dy in -2..2) for (dx in -2..2) {
                val weight = kernel[dx + 2] * kernel[dy + 2]
                val color = input[(y + dy) * width + x + dx]
                red += android.graphics.Color.red(color) * weight
                green += android.graphics.Color.green(color) * weight
                blue += android.graphics.Color.blue(color) * weight
            }
            output[y * width + x] = android.graphics.Color.rgb(red / 256, green / 256, blue / 256)
        }
    }
    for (y in 0 until height) for (x in 0 until width) if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) output[y * width + x] = input[y * width + x]
    return android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also { it.setPixels(output, 0, width, 0, 0, width, height) }
}
