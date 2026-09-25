package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

/** 收藏、文件夹和收藏条码关系的变更协调器。查询和分页由 FavoritesQueryCoordinator 负责。 */
internal class FavoritesMutationCoordinator(
    private val store: LibraryStateStore,
    private val persistence: BarcodePersistenceCoordinator,
) {
    fun renameFolder(path: String, renamedPath: String): Boolean {
        val knownPaths = store.foldersSnapshot() + store.favoriteIdentityFolderPathsSnapshot()
        if (!isSafeFavoriteFolderRename(path, renamedPath, knownPaths)) return false
        if (createsFavoriteIdentityCollision(path, renamedPath)) return false
        store.edit {
            val modifiedAt = System.currentTimeMillis()
            groups.indices.filter { groups[it].folder == path || groups[it].folder.startsWith("$path/") }
                .forEach { index ->
                    val group = groups[index]
                    val newPath = if (group.folder == path) renamedPath else renamedPath + group.folder.removePrefix(path)
                    groups[index] = group.copy(folder = newPath, savedAt = modifiedAt)
                }
            folders.filter { it == path || it.startsWith("$path/") }.toList().forEach { old ->
                folders.remove(old)
                folders.add(if (old == path) renamedPath else renamedPath + old.removePrefix(path))
            }
        }
        store.renameFavoriteIdentityFolders(path, renamedPath)
        return true
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
        store.removeFavoriteIdentitiesInFolder(path)
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
        if (removed) store.removeFavoriteIdentity(groupId)
        return removed
    }

    fun saveResultAsFavorite(resultItemIds: List<Long>, editingGroupId: Long?, targetGroupId: Long?, folder: String, name: String): Boolean {
        val selectedItems = store.itemsSnapshot().filter { it.id in resultItemIds }
        if (selectedItems.isEmpty() || folder.isBlank() || name.isBlank()) return false
        val excludedIds = setOfNotNull(editingGroupId, targetGroupId).toSet()
        if (store.hasFavoriteIdentity(folder, name, excludedIds)) return false
        val allocatedGroupId = targetGroupId ?: (store.maxFavoriteGroupId() + 1L)
        val savedGroupId = store.edit {
            val selectedItems = items.filter { it.id in resultItemIds }
            if (selectedItems.isEmpty()) return@edit null
            if (editingGroupId != null && editingGroupId != targetGroupId) groups.removeAll { it.id == editingGroupId }
            selectedItems.forEach { it.favorite = true; it.folder = folder }
            if (folder !in folders) folders.add(folder)
            val groupId = allocatedGroupId
            val updatedGroup = FavoriteGroup(groupId, folder, name, System.currentTimeMillis(), selectedItems.map { it.id }.toMutableList())
            val targetIndex = groups.indexOfFirst { it.id == groupId }
            if (targetIndex >= 0) groups[targetIndex] = updatedGroup else groups.add(0, updatedGroup)
            items.filter { it.favorite && groups.none { group -> it.id in group.itemIds } }.forEach { it.favorite = false }
            groupId
        }
        if (savedGroupId != null) {
            if (editingGroupId != null && editingGroupId != savedGroupId) store.removeFavoriteIdentity(editingGroupId)
            store.setFavoriteIdentity(savedGroupId, folder, name)
        }
        savedGroupId?.let(store::markGroupLinksChanged)
        persistAllFavorites()
        return savedGroupId != null
    }

    fun updateGroup(groupId: Long, name: String, folder: String): Boolean {
        if (store.hasFavoriteIdentity(folder, name, setOf(groupId))) return false
        val updated = store.edit {
            val index = groups.indexOfFirst { it.id == groupId }
            if (index < 0) return@edit false
            val group = groups[index]
            groups[index] = group.copy(
                name = name,
                folder = folder,
                savedAt = System.currentTimeMillis(),
            )
            if (folder !in folders) folders.add(folder)
            true
        }
        if (!updated) return false
        store.setFavoriteIdentity(groupId, folder, name)
        persistAllFavorites()
        return true
    }

    fun renameFolderAndPersist(path: String, renamedPath: String): Boolean {
        if (!renameFolder(path, renamedPath)) return false
        persistence.renameFavoriteFolder(path, renamedPath)
        return true
    }
    fun deleteFolderAndPersist(path: String) { deleteFolder(path); persistence.deleteFavoriteFolder(path) }
    fun renameGroupAndPersist(groupId: Long, name: String): Boolean {
        val identity = store.favoriteIdentity(groupId) ?: return false
        if (store.hasFavoriteIdentity(identity.folder, name, setOf(groupId))) return false
        store.edit {
            val index = groups.indexOfFirst { it.id == groupId }
            if (index < 0) return@edit
            groups[index] = groups[index].copy(name = name, savedAt = System.currentTimeMillis())
        }
        store.setFavoriteIdentity(groupId, identity.folder, name)
        persistAllFavorites()
        return true
    }
    fun moveGroupAndPersist(groupId: Long, folder: String): Boolean {
        val identity = store.favoriteIdentity(groupId) ?: return false
        if (store.hasFavoriteIdentity(folder, identity.name, setOf(groupId))) return false
        store.edit {
            val index = groups.indexOfFirst { it.id == groupId }
            if (index < 0) return@edit
            groups[index] = groups[index].copy(folder = folder, savedAt = System.currentTimeMillis())
            if (folder.isNotBlank() && folder !in folders) folders.add(folder)
        }
        store.setFavoriteIdentity(groupId, folder, identity.name)
        persistAllFavorites()
        return true
    }
    fun deleteGroupAndPersist(groupId: Long) {
        if (deleteGroup(groupId)) persistence.deleteFavoriteGroups(listOf(groupId))
    }
    fun clearFavoritesAndPersist() {
        store.clearFavorites()
        persistence.clearAllFavoriteGroups()
        persistAllFavorites()
    }
    fun persistAllFavorites() = persistence.persistAllFavorites(
        store.itemsSnapshot(),
        store.groupsSnapshot(),
        store.foldersSnapshot(),
        store.loadedGroupLinkIdsSnapshot(),
    )

    private fun createsFavoriteIdentityCollision(path: String, renamedPath: String): Boolean {
        val before = store.favoriteIdentitiesSnapshot()
        val after = before.mapValues { (_, identity) ->
            if (identity.folder == path || identity.folder.startsWith("$path/")) {
                identity.copy(folder = if (identity.folder == path) renamedPath else renamedPath + identity.folder.removePrefix(path))
            } else identity
        }
        return after.entries.groupBy({ it.value }, { it.key }).values.any { ids ->
            ids.size > 1 && ids.mapNotNull(before::get).distinct().size > 1
        }
    }
}
