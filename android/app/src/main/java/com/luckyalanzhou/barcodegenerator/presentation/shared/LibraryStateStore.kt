package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState

/** Shared in-memory library snapshot used by favorites, history, generation, and item editing. */
internal class LibraryStateStore {
    private val lock = Any()
    private val items = mutableListOf<CodeItem>()
    private val groups = mutableListOf<FavoriteGroup>()
    private val folders = mutableListOf<String>()
    private val loadedGroupLinkIds = mutableSetOf<Long>()

    internal class Editor internal constructor(
        val items: MutableList<CodeItem>,
        val groups: MutableList<FavoriteGroup>,
        val folders: MutableList<String>,
    )

    fun <T> edit(block: Editor.() -> T): T = synchronized(lock) {
        Editor(items, groups, folders).block()
    }

    fun itemsSnapshot(): List<CodeItem> = synchronized(lock) {
        items.map { it.copy() }
    }

    fun groupsSnapshot(): List<FavoriteGroup> = synchronized(lock) {
        groups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
    }

    fun foldersSnapshot(): List<String> = synchronized(lock) { folders.toList() }

    fun markGroupLinksLoaded(groupId: Long) = synchronized(lock) {
        if (groups.any { it.id == groupId }) loadedGroupLinkIds += groupId
    }

    fun markGroupLinksChanged(groupId: Long) = markGroupLinksLoaded(groupId)

    fun loadedGroupLinkIdsSnapshot(): Set<Long> = synchronized(lock) { loadedGroupLinkIds.toSet() }

    fun replace(
        newItems: List<CodeItem>,
        newGroups: List<FavoriteGroup>,
        newFolders: List<String>,
    ) {
        edit {
            items.clear()
            items.addAll(newItems)
            groups.clear()
            groups.addAll(newGroups)
            loadedGroupLinkIds.clear()
            loadedGroupLinkIds.addAll(newGroups.filter { it.itemIds.isNotEmpty() }.map { it.id })
            folders.clear()
            folders.addAll(newFolders)
        }
    }

    fun clearFavorites() = edit {
        groups.clear()
        folders.clear()
        loadedGroupLinkIds.clear()
        items.forEach { item ->
            item.favorite = false
            item.folder = "默认"
        }
    }

    fun snapshot(isReady: Boolean): BarcodeDataState = synchronized(lock) {
        BarcodeDataState(
            items = items.map { it.copy() },
            groups = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) },
            folders = folders.toList(),
            isReady = isReady,
        )
    }
}
