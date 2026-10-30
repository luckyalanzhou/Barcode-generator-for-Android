package com.luckyalanzhou.barcodegenerator.ui.feature.results

/** 导出时保持条目顺序，并在每项渲染完成后立即释放临时资源。 */
internal fun <T, R : Any> consumeCompleteExportBatch(
    items: List<T>,
    render: (T) -> R?,
    consume: (R) -> Unit,
    release: (R) -> Unit,
): Boolean {
    if (items.isEmpty()) return false
    for (item in items) {
        val resource = render(item) ?: return false
        try {
            consume(resource)
        } finally {
            release(resource)
        }
    }
    return true
}

internal data class ResultExportDimensions(val width: Int, val height: Int)

/** 分配位图前拒绝不可能的尺寸，避免整数溢出或内存耗尽。 */
internal fun resultExportDimensions(
    rowWidths: List<Int>,
    rowHeights: List<Int>,
    spacing: Int,
    outerPadding: Int,
    maxPixels: Long = MAX_RESULT_EXPORT_PIXELS,
): ResultExportDimensions? {
    if (rowWidths.isEmpty() || rowWidths.size != rowHeights.size || maxPixels <= 0) return null
    if (rowWidths.any { it <= 0 } || rowHeights.any { it <= 0 } || spacing < 0 || outerPadding < 0) return null

    val width = rowWidths.maxOrNull()?.toLong() ?: return null
    val height = rowHeights.sumOf(Int::toLong) + spacing.toLong() * (rowHeights.size - 1) + outerPadding.toLong() * 2
    if (width > MAX_RESULT_EXPORT_DIMENSION || height > MAX_RESULT_EXPORT_DIMENSION) return null
    if (width * height > maxPixels) return null
    return ResultExportDimensions(width.toInt(), height.toInt())
}

private const val MAX_RESULT_EXPORT_DIMENSION = 32_767L
private const val MAX_RESULT_EXPORT_PIXELS = 24_000_000L
