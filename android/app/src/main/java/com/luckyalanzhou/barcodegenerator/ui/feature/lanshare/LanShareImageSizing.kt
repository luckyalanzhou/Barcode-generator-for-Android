package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import kotlin.math.roundToInt

/** Bounded preview dimensions in dp; both dimensions use one scale to preserve the source ratio. */
internal data class LanSharePreviewSize(val widthDp: Int, val heightDp: Int)

internal const val LAN_SHARE_PREVIEW_MAX_DECODE_EDGE = 4_096

/** Decode large camera photos at a bounded resolution so previewing does not require a full bitmap. */
internal fun lanSharePreviewSampleSize(imageWidth: Int, imageHeight: Int): Int {
    if (imageWidth <= 0 || imageHeight <= 0) return 1
    val longestEdge = maxOf(imageWidth, imageHeight).toLong()
    var sampleSize = 1
    while (longestEdge > LAN_SHARE_PREVIEW_MAX_DECODE_EDGE.toLong() * sampleSize) {
        if (sampleSize > Int.MAX_VALUE / 2) return Int.MAX_VALUE
        sampleSize *= 2
    }
    return sampleSize
}

internal fun fitLanSharePreviewSize(
    imageWidth: Int,
    imageHeight: Int,
    maxWidthDp: Int = 220,
    maxHeightDp: Int = 180,
): LanSharePreviewSize {
    require(maxWidthDp > 0 && maxHeightDp > 0)
    if (imageWidth <= 0 || imageHeight <= 0) return LanSharePreviewSize(1, 1)

    val scale = minOf(
        maxWidthDp.toFloat() / imageWidth,
        maxHeightDp.toFloat() / imageHeight,
        1f,
    )
    return LanSharePreviewSize(
        widthDp = (imageWidth * scale).roundToInt().coerceAtLeast(1),
        heightDp = (imageHeight * scale).roundToInt().coerceAtLeast(1),
    )
}
