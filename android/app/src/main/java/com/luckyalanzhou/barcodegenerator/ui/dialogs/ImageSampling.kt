package com.luckyalanzhou.barcodegenerator.ui.dialogs

internal const val OCR_RECOGNITION_MAX_EDGE = 2_400
internal const val BARCODE_RECOGNITION_MAX_EDGE = 3_072

internal data class ExifBitmapTransform(
    val rotationDegrees: Int = 0,
    val flipHorizontally: Boolean = false,
)

/** 选择 2 的幂次解码采样率，使解码后最长边不超过目标尺寸。 */
internal fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    val longestEdge = maxOf(width, height).toLong()
    var sampleSize = 1
    while (longestEdge > maxEdge.toLong() * sampleSize) {
        if (sampleSize > Int.MAX_VALUE / 2) return Int.MAX_VALUE
        sampleSize *= 2
    }
    return sampleSize
}

/** 将 EXIF 方向转换为顺时针旋转及随后执行的水平翻转。 */
internal fun exifBitmapTransform(orientation: Int): ExifBitmapTransform = when (orientation) {
    2 -> ExifBitmapTransform(flipHorizontally = true)
    3 -> ExifBitmapTransform(rotationDegrees = 180)
    4 -> ExifBitmapTransform(rotationDegrees = 180, flipHorizontally = true)
    5 -> ExifBitmapTransform(rotationDegrees = 90, flipHorizontally = true)
    6 -> ExifBitmapTransform(rotationDegrees = 90)
    7 -> ExifBitmapTransform(rotationDegrees = 270, flipHorizontally = true)
    8 -> ExifBitmapTransform(rotationDegrees = 270)
    else -> ExifBitmapTransform()
}
