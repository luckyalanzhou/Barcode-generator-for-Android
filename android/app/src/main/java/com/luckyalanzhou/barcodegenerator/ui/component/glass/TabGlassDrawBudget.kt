package com.luckyalanzhou.barcodegenerator.ui.component.glass

import android.os.Trace
import androidx.compose.ui.unit.IntSize

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
