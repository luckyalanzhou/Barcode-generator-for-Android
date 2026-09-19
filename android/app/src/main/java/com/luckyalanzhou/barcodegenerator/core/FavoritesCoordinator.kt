package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import kotlinx.coroutines.CoroutineScope

/** 收藏、文件夹和收藏条码关系的业务协调器。页面路由与 Compose 状态由 ViewModel 负责。 */
class FavoritesCoordinator(
    private val items: MutableList<CodeItem>,
    private val groups: MutableList<FavoriteGroup>,
    private val folders: MutableList<String>,
    private val persistence: BarcodePersistenceCoordinator,
    private val scope: CoroutineScope,
    private val publish: () -> Unit,
) {
    fun renameFolder(path: String, renamedPath: String) {
        groups
            .filter { it.folder == path || it.folder.startsWith("$path/") }
            .forEach { group ->
                group.folder = if (group.folder == path) renamedPath
                else renamedPath + group.folder.removePrefix(path)
            }
        folders
            .filter { it == path || it.startsWith("$path/") }
            .toList()
            .forEach { old ->
                folders.remove(old)
                folders.add(if (old == path) renamedPath else renamedPath + old.removePrefix(path))
            }
        publish()
    }

    fun deleteFolder(path: String) {
        groups.removeAll { it.folder == path || it.folder.startsWith("$path/") }
        folders.removeAll { it == path || it.startsWith("$path/") }
        publish()
    }

    fun deleteGroup(groupId: Long) {
        val group = groups.firstOrNull { it.id == groupId } ?: return
        groups.removeAll { it.id == groupId }
        items.filter { it.id in group.itemIds }
            .filter { item -> groups.none { remaining -> item.id in remaining.itemIds } }
            .forEach { it.favorite = false }
        if (group.folder !in folders) folders.add(group.folder)
        publish()
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

    fun saveResultAsFavorite(
        resultItemIds: List<Long>,
        editingGroupId: Long?,
        targetGroupId: Long?,
        folder: String,
        name: String,
    ): Boolean {
        val selectedItems = items.filter { it.id in resultItemIds }
        if (selectedItems.isEmpty() || folder.isBlank() || name.isBlank()) return false

        if (editingGroupId != null && editingGroupId != targetGroupId) {
            groups.removeAll { it.id == editingGroupId }
        }
        selectedItems.forEach {
            it.favorite = true
            it.folder = folder
        }
        if (folder !in folders) folders.add(folder)

        val groupId = targetGroupId ?: ((groups.maxOfOrNull { it.id } ?: 0L) + 1L)
        val updatedGroup = FavoriteGroup(
            groupId,
            folder,
            name,
            System.currentTimeMillis(),
            selectedItems.map { it.id }.toMutableList(),
        )
        val targetIndex = groups.indexOfFirst { it.id == groupId }
        if (targetIndex >= 0) groups[targetIndex] = updatedGroup else groups.add(0, updatedGroup)

        items.filter { it.favorite && groups.none { group -> it.id in group.itemIds } }
            .forEach { it.favorite = false }
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

    fun renameFolderAndPersist(path: String, renamedPath: String) {
        renameFolder(path, renamedPath)
        persistAllFavorites()
    }

    fun deleteFolderAndPersist(path: String) {
        deleteFolder(path)
        persistAllFavorites()
    }

    fun renameGroupAndPersist(groupId: Long, name: String) {
        groups.firstOrNull { it.id == groupId }?.name = name
        persistAllFavorites()
    }

    fun moveGroupAndPersist(groupId: Long, folder: String) {
        val group = groups.firstOrNull { it.id == groupId } ?: return
        group.folder = folder
        if (folder.isNotBlank() && folder !in folders) folders.add(folder)
        persistAllFavorites()
    }

    fun deleteGroupAndPersist(groupId: Long) {
        deleteGroup(groupId)
        persistAllFavorites()
    }

    fun clearFavoritesAndPersist() {
        groups.clear()
        items.forEach { it.favorite = false; it.folder = "默认" }
        persistAllFavorites()
    }

    fun clearHistoryAndPersist() {
        items.forEach { it.inHistory = false }
        persistItems()
    }

    fun persistAllFavorites() = persistence.persistAllFavorites(scope, items, groups, folders, publish)
    fun persistItems() = persistence.persistItems(scope, items, publish)
    fun persistGroups() = persistence.persistFavoriteGroups(scope, groups, publish)
    fun persistFolders() = persistence.persistFavoriteFolders(scope, folders, publish)
}
