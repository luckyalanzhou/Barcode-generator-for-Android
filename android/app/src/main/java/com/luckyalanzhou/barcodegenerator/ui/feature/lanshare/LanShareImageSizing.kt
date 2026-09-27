package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import kotlin.math.roundToInt

/** Bounded preview dimensions in dp; both dimensions use one scale to preserve the source ratio. */
internal data class LanSharePreviewSize(val widthDp: Int, val heightDp: Int)

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
