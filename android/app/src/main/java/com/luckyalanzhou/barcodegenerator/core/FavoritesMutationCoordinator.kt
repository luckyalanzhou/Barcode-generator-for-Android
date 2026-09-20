package com.luckyalanzhou.barcodegenerator

import kotlinx.coroutines.CoroutineScope
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

/** 收藏、文件夹和收藏条码关系的变更协调器。查询和分页由 FavoritesQueryCoordinator 负责。 */
internal class FavoritesMutationCoordinator(
    private val store: FavoritesStateStore,
    private val persistence: BarcodePersistenceCoordinator,
    private val scope: CoroutineScope,
) {
    fun renameFolder(path: String, renamedPath: String) {
        store.edit {
            groups.filter { it.folder == path || it.folder.startsWith("$path/") }.forEach { group ->
                group.folder = if (group.folder == path) renamedPath else renamedPath + group.folder.removePrefix(path)
            }
            folders.filter { it == path || it.startsWith("$path/") }.toList().forEach { old ->
                folders.remove(old)
                folders.add(if (old == path) renamedPath else renamedPath + old.removePrefix(path))
            }
        }
    }

    fun deleteFolder(path: String) {
        store.edit {
            val removed = groups.filter { it.folder == path || it.folder.startsWith("$path/") }
            val remaining = groups.filterNot { it.folder == path || it.folder.startsWith("$path/") }
            val orphaned = removed.flatMap { it.itemIds }.distinct().filter { itemId -> remaining.none { itemId in it.itemIds } }
            items.filter { it.id in orphaned }.forEach { it.favorite = false; it.folder = "默认" }
            groups.removeAll { it.folder == path || it.folder.startsWith("$path/") }
            folders.removeAll { it == path || it.startsWith("$path/") }
        }
    }

    fun deleteGroup(groupId: Long): Boolean {
        val removed = store.edit {
            val group = groups.firstOrNull { it.id == groupId } ?: return@edit false
            groups.removeAll { it.id == groupId }
            val orphaned = group.itemIds.filter { itemId -> groups.none { remaining -> itemId in remaining.itemIds } }
            items.filter { it.id in orphaned }.forEach { it.favorite = false }
            if (group.folder !in folders) folders.add(group.folder)
            true
        }
        return removed
    }

    fun deleteItem(itemId: Long) {
        store.edit {
            items.removeAll { it.id == itemId }
            groups.forEach { group -> group.itemIds.removeAll { it == itemId } }
        }
        persistAllFavorites()
    }

    fun updateItem(itemId: Long, text: String, format: String) {
        val favorite = store.edit {
            val item = items.firstOrNull { it.id == itemId } ?: return@edit null
            item.text = text
            item.format = format
            item.favorite
        } ?: return
        if (favorite) persistAllFavorites() else persistItems()
    }

    fun saveResultAsFavorite(resultItemIds: List<Long>, editingGroupId: Long?, targetGroupId: Long?, folder: String, name: String): Boolean {
        val saved = store.edit {
            val selectedItems = items.filter { it.id in resultItemIds }
            if (selectedItems.isEmpty() || folder.isBlank() || name.isBlank()) return@edit false
            if (editingGroupId != null && editingGroupId != targetGroupId) groups.removeAll { it.id == editingGroupId }
            selectedItems.forEach { it.favorite = true; it.folder = folder }
            if (folder !in folders) folders.add(folder)
            val groupId = targetGroupId ?: ((groups.maxOfOrNull { it.id } ?: 0L) + 1L)
            val updatedGroup = FavoriteGroup(groupId, folder, name, System.currentTimeMillis(), selectedItems.map { it.id }.toMutableList())
            val targetIndex = groups.indexOfFirst { it.id == groupId }
            if (targetIndex >= 0) groups[targetIndex] = updatedGroup else groups.add(0, updatedGroup)
            items.filter { it.favorite && groups.none { group -> it.id in group.itemIds } }.forEach { it.favorite = false }
            true
        }
        persistAllFavorites()
        return saved
    }

    fun updateGroup(groupId: Long, name: String, folder: String): Boolean {
        val updated = store.edit {
            val group = groups.firstOrNull { it.id == groupId } ?: return@edit false
            group.name = name
            group.folder = folder
            if (folder !in folders) folders.add(folder)
            true
        }
        if (!updated) return false
        persistAllFavorites()
        return true
    }

    fun renameFolderAndPersist(path: String, renamedPath: String) { renameFolder(path, renamedPath); persistence.renameFavoriteFolder(scope, path, renamedPath) }
    fun deleteFolderAndPersist(path: String) { deleteFolder(path); persistence.deleteFavoriteFolder(scope, path) }
    fun renameGroupAndPersist(groupId: Long, name: String) { store.edit { groups.firstOrNull { it.id == groupId }?.name = name }; persistAllFavorites() }
    fun moveGroupAndPersist(groupId: Long, folder: String) { store.edit { groups.firstOrNull { it.id == groupId }?.let { it.folder = folder; if (folder.isNotBlank() && folder !in folders) folders.add(folder) } }; persistAllFavorites() }
    fun deleteGroupAndPersist(groupId: Long) {
        if (deleteGroup(groupId)) persistence.deleteFavoriteGroups(scope, listOf(groupId))
    }
    fun clearFavoritesAndPersist() { store.edit { groups.clear(); items.forEach { it.favorite = false; it.folder = "默认" } }; persistence.clearAllFavoriteGroups(scope); persistAllFavorites() }
    fun clearHistoryAndPersist() { store.edit { items.forEach { it.inHistory = false } }; persistItems() }
    fun persistAllFavorites() = persistence.persistAllFavorites(scope, store.itemsSnapshot(), store.groupsSnapshot(), store.foldersSnapshot())
    fun persistItems() = persistence.persistItems(scope, store.itemsSnapshot())
    fun persistGroups() = persistence.persistFavoriteGroups(scope, store.groupsSnapshot())
    fun persistFolders() = persistence.persistFavoriteFolders(scope, store.foldersSnapshot())
}
