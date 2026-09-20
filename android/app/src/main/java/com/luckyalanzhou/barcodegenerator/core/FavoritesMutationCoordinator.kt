package com.luckyalanzhou.barcodegenerator

import kotlinx.coroutines.CoroutineScope
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

/** 收藏、文件夹和收藏条码关系的变更协调器。查询和分页由 FavoritesQueryCoordinator 负责。 */
internal class FavoritesMutationCoordinator(
    private val store: FavoritesStateStore,
    private val persistence: BarcodePersistenceCoordinator,
    private val scope: CoroutineScope,
) {
    private val items get() = store.items
    private val groups get() = store.groups
    private val folders get() = store.folders
    fun renameFolder(path: String, renamedPath: String) {
        groups.filter { it.folder == path || it.folder.startsWith("$path/") }.forEach { group ->
            group.folder = if (group.folder == path) renamedPath else renamedPath + group.folder.removePrefix(path)
        }
        folders.filter { it == path || it.startsWith("$path/") }.toList().forEach { old ->
            folders.remove(old)
            folders.add(if (old == path) renamedPath else renamedPath + old.removePrefix(path))
        }
    }

    fun deleteFolder(path: String) {
        val removed = groups.filter { it.folder == path || it.folder.startsWith("$path/") }
        val remaining = groups.filterNot { it.folder == path || it.folder.startsWith("$path/") }
        val orphanedItemIds = removed.flatMap { it.itemIds }.distinct().filter { itemId -> remaining.none { itemId in it.itemIds } }
        persistence.clearFavoriteFlags(scope, orphanedItemIds)
        persistence.clearFavoriteFlagsForGroups(scope, removed.map { it.id })
        persistence.deleteFavoriteGroups(scope, removed.map { it.id })
        groups.removeAll { it.folder == path || it.folder.startsWith("$path/") }
        folders.removeAll { it == path || it.startsWith("$path/") }
    }

    fun deleteGroup(groupId: Long) {
        val group = groups.firstOrNull { it.id == groupId } ?: return
        groups.removeAll { it.id == groupId }
        val orphanedItemIds = group.itemIds.filter { itemId -> groups.none { remaining -> itemId in remaining.itemIds } }
        items.filter { it.id in orphanedItemIds }.forEach { it.favorite = false }
        persistence.clearFavoriteFlags(scope, orphanedItemIds)
        persistence.clearFavoriteFlagsForGroups(scope, listOf(groupId))
        persistence.deleteFavoriteGroups(scope, listOf(groupId))
        if (group.folder !in folders) folders.add(group.folder)
    }

    fun deleteItem(itemId: Long) {
        items.removeAll { it.id == itemId }
        groups.forEach { group -> group.itemIds.removeAll { it == itemId } }
        persistAllFavorites()
    }

    fun updateItem(itemId: Long, text: String, format: String) {
        val item = items.firstOrNull { it.id == itemId } ?: return
        item.text = text
        item.format = format
        if (item.favorite) persistAllFavorites() else persistItems()
    }

    fun saveResultAsFavorite(resultItemIds: List<Long>, editingGroupId: Long?, targetGroupId: Long?, folder: String, name: String): Boolean {
        val selectedItems = items.filter { it.id in resultItemIds }
        if (selectedItems.isEmpty() || folder.isBlank() || name.isBlank()) return false
        if (editingGroupId != null && editingGroupId != targetGroupId) groups.removeAll { it.id == editingGroupId }
        selectedItems.forEach { it.favorite = true; it.folder = folder }
        if (folder !in folders) folders.add(folder)
        val groupId = targetGroupId ?: ((groups.maxOfOrNull { it.id } ?: 0L) + 1L)
        val updatedGroup = FavoriteGroup(groupId, folder, name, System.currentTimeMillis(), selectedItems.map { it.id }.toMutableList())
        val targetIndex = groups.indexOfFirst { it.id == groupId }
        if (targetIndex >= 0) groups[targetIndex] = updatedGroup else groups.add(0, updatedGroup)
        items.filter { it.favorite && groups.none { group -> it.id in group.itemIds } }.forEach { it.favorite = false }
        persistAllFavorites()
        return true
    }

    fun updateGroup(groupId: Long, name: String, folder: String): Boolean {
        val group = groups.firstOrNull { it.id == groupId } ?: return false
        group.name = name
        group.folder = folder
        if (folder !in folders) folders.add(folder)
        persistAllFavorites()
        return true
    }

    fun renameFolderAndPersist(path: String, renamedPath: String) { renameFolder(path, renamedPath); persistence.renameFavoriteFolder(scope, path, renamedPath); persistFolders() }
    fun deleteFolderAndPersist(path: String) { deleteFolder(path); persistence.deleteFavoriteFolder(scope, path); persistFolders() }
    fun renameGroupAndPersist(groupId: Long, name: String) { groups.firstOrNull { it.id == groupId }?.name = name; persistAllFavorites() }
    fun moveGroupAndPersist(groupId: Long, folder: String) { groups.firstOrNull { it.id == groupId }?.let { it.folder = folder; if (folder.isNotBlank() && folder !in folders) folders.add(folder); persistAllFavorites() } }
    fun deleteGroupAndPersist(groupId: Long) { deleteGroup(groupId); persistAllFavorites() }
    fun clearFavoritesAndPersist() { groups.clear(); items.forEach { it.favorite = false; it.folder = "默认" }; persistence.clearAllFavoriteFlags(scope); persistence.clearAllFavoriteGroups(scope); persistAllFavorites() }
    fun clearHistoryAndPersist() { items.forEach { it.inHistory = false }; persistItems() }
    fun persistAllFavorites() = persistence.persistAllFavorites(scope, items, groups, folders)
    fun persistItems() = persistence.persistItems(scope, items)
    fun persistGroups() = persistence.persistFavoriteGroups(scope, groups)
    fun persistFolders() = persistence.persistFavoriteFolders(scope, folders)
}
