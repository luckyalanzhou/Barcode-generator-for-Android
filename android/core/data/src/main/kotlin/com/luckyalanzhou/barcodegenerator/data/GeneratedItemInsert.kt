package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/** Caller must hold a database write transaction across allocation and insert. */
internal suspend fun insertGeneratedItemsWithAllocatedIds(
    dao: BarcodeDao,
    drafts: List<CodeItem>,
): List<CodeItem> {
    if (drafts.isEmpty()) return emptyList()
    val maximum = dao.maxItemId()
    require(maximum >= 0 && maximum <= Long.MAX_VALUE - drafts.size) { "条码 ID 已达到存储上限" }
    val inserted = drafts.mapIndexed { index, item ->
        item.copy(id = maximum + index + 1L, favorite = false, inHistory = true)
    }
    // ABORT rather than upsert makes any unexpected collision fail without changing old data.
    dao.insertNewItems(inserted.map(CodeItem::toEntity))
    return inserted
}
