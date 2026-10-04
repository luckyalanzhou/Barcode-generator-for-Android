package com.luckyalanzhou.barcodegenerator.ui.feature.results

/** Export must keep the original ordering and fail rather than omit an item. */
internal fun <T, R : Any> completeExportBatch(items: List<T>, render: (T) -> R?): List<R>? {
    if (items.isEmpty()) return null
    return items.map { render(it) ?: return null }
}
