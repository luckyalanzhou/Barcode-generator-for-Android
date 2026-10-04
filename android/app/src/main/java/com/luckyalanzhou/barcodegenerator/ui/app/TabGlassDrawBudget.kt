package com.luckyalanzhou.barcodegenerator.ui.app

import android.os.Trace
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor

internal data class TabForegroundRegion(val left: Int, val size: IntSize) {
    val right: Int get() = left + size.width
    fun localFrame(frame: TabGlassFrame): TabGlassFrame = frame.copy(
        width = size.width.toFloat(), height = size.height.toFloat(),
        centerX = frame.centerX - left, touchX = frame.touchX - left,
    )
}

/** Integer-aligned crop leaves room for the 1.2dp lens and 3dp background taps. */
internal fun tabForegroundRegion(frame: TabGlassFrame, size: IntSize): TabForegroundRegion? {
    if (size.width <= 0 || size.height <= 0 || !frame.centerX.isFinite() ||
        !frame.halfWidth.isFinite() || !frame.density.isFinite() || frame.halfWidth <= 0f || frame.density <= 0f) return null
    val padding = frame.density * 4f
    val left = floor(frame.centerX - frame.halfWidth - padding).toInt().coerceIn(0, size.width)
    val right = ceil(frame.centerX + frame.halfWidth + padding).toInt().coerceIn(0, size.width)
    if (right <= left) return null
    return TabForegroundRegion(left, IntSize(right - left, size.height))
}

/** Bound the temporary two-input surface, including overflow and extreme window dimensions. */
internal fun tabForegroundAtlasSize(size: IntSize): IntSize? {
    if (size.width <= 0 || size.height <= 0) return null
    val width = size.width.toLong() * 2L
    val height = size.height.toLong()
    if (width > 8192L || height > 8192L || width * height > 4_194_304L) return null
    return IntSize(width.toInt(), size.height)
}

/** System Trace only, never per-frame logging or GPU readback. */
internal inline fun <T> traceGlassDraw(section: String, block: () -> T): T {
    Trace.beginSection(section)
    return try { block() } finally { Trace.endSection() }
}
