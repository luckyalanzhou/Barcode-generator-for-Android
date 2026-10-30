package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/** 调用方必须在 ID 分配和插入期间持有数据库写事务。 */
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
    // 使用 ABORT 而非 upsert，让意外的 ID 冲突直接失败且不修改旧数据。
    dao.insertNewItems(inserted.map(CodeItem::toEntity))
    return inserted
}
