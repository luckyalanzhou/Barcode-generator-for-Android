package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

/**
 * 收藏与历史的唯一内存状态容器。
 *
 * Coordinator 只能通过这组集合读取和修改，ViewModel 负责把快照发布给 Compose；
 * 后续接入 Room Flow 时只需要替换这里的装载入口，不改变页面接口。
 */
internal class FavoritesStateStore {
    val items = mutableListOf<CodeItem>()
    val groups = mutableListOf<FavoriteGroup>()
    val folders = mutableListOf<String>()

    fun replace(
        newItems: List<CodeItem>,
        newGroups: List<FavoriteGroup>,
        newFolders: List<String>,
    ) {
        items.clear()
        items.addAll(newItems)
        groups.clear()
        groups.addAll(newGroups)
        folders.clear()
        folders.addAll(newFolders)
    }

    fun snapshot(isReady: Boolean): BarcodeDataState = BarcodeDataState(
        items = items.map { it.copy() },
        groups = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) },
        folders = folders.toList(),
        isReady = isReady,
    )
}
