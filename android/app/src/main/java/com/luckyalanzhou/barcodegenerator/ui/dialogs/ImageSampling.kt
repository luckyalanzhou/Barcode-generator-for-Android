package com.luckyalanzhou.barcodegenerator.ui.dialogs

internal const val OCR_RECOGNITION_MAX_EDGE = 2_400
internal const val BARCODE_RECOGNITION_MAX_EDGE = 3_072

internal data class ExifBitmapTransform(
    val rotationDegrees: Int = 0,
    val flipHorizontally: Boolean = false,
)

/** Selects a power-of-two decode sample so the decoded longest edge stays within the target. */
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

/** Maps EXIF orientations to a clockwise rotation followed by a horizontal flip. */
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
