package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

internal data class LanSharePreviewTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero)

/** 围绕触点缩放；图片不足一屏的方向不允许拖出空白。 */
internal fun transformLanSharePreview(
    current: LanSharePreviewTransform,
    image: IntSize,
    viewport: IntSize,
    centroid: Offset,
    pan: Offset = Offset.Zero,
    zoom: Float,
): LanSharePreviewTransform {
    if (image.width <= 0 || image.height <= 0 || viewport.width <= 0 || viewport.height <= 0 ||
        !zoom.isFinite() || zoom <= 0f) return current
    val nextScale = (current.scale * zoom).coerceIn(1f, 5f)
    if (nextScale == 1f) return LanSharePreviewTransform()
    val factor = nextScale / current.scale
    val center = Offset(viewport.width / 2f, viewport.height / 2f)
    val proposed = (current.offset - (centroid - center)) * factor + (centroid - center) + pan
    val fit = minOf(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    val maxX = (image.width * fit * nextScale - viewport.width).coerceAtLeast(0f) / 2f
    val maxY = (image.height * fit * nextScale - viewport.height).coerceAtLeast(0f) / 2f
    return LanSharePreviewTransform(nextScale, Offset(proposed.x.coerceIn(-maxX, maxX), proposed.y.coerceIn(-maxY, maxY)))
}
