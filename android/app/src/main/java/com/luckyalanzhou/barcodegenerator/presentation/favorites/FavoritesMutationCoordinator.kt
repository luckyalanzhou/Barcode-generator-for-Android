package com.luckyalanzhou.barcodegenerator.presentation.favorites

import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodePersistenceCoordinator
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryStateStore

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/** 收藏、文件夹和收藏条码关系的变更协调器。查询和分页由 FavoritesQueryCoordinator 负责。 */
internal class FavoritesMutationCoordinator(
    private val store: LibraryStateStore,
    private val persistence: BarcodePersistenceCoordinator,
    private val onReconciled: () -> Unit = {},
    private val onMutationStarted: () -> Unit = {},
) {
    fun renameFolder(path: String, renamedPath: String, savedAt: Long = System.currentTimeMillis()): Boolean {
        if (!canRenameFolder(path, renamedPath)) return false
        onMutationStarted()
        applyFolderRename(path, renamedPath, savedAt)
        return true
    }

    private fun canRenameFolder(path: String, renamedPath: String): Boolean {
        val knownPaths = store.foldersSnapshot() + store.favoriteIdentityFolderPathsSnapshot()
        return isSafeFavoriteFolderRename(path, renamedPath, knownPaths) &&
            !createsFavoriteIdentityCollision(path, renamedPath)
    }

    private fun applyFolderRename(path: String, renamedPath: String, savedAt: Long) {
        store.edit {
            groups.indices.filter { groups[it].folder == path || groups[it].folder.startsWith("$path/") }
                .forEach { index ->
                    val group = groups[index]
                    val newPath = if (group.folder == path) renamedPath else renamedPath + group.folder.removePrefix(path)
                    groups[index] = group.copy(folder = newPath, savedAt = savedAt)
                }
            sortGroupsByRecency()
            folders.filter { it == path || it.startsWith("$path/") }.toList().forEach { old ->
                folders.remove(old)
                folders.add(if (old == path) renamedPath else renamedPath + old.removePrefix(path))
            }
        }
        store.renameFavoriteIdentityFolders(path, renamedPath)
    }

    fun deleteFolder(path: String) {
        store.edit {
            groups.removeAll { it.folder == path || it.folder.startsWith("$path/") }
            folders.removeAll { it == path || it.startsWith("$path/") }
        }
        store.removeFavoriteIdentitiesInFolder(path)
    }

    fun deleteGroup(groupId: Long): Boolean {
        val identity = store.favoriteIdentity(groupId) ?: return false
        val removed = store.edit {
            groups.removeAll { it.id == groupId }
            if (identity.folder !in folders) folders.add(identity.folder)
            true
        }
        if (removed) store.removeFavoriteIdentity(groupId)
        return removed
    }

    suspend fun saveResultAsFavorite(resultItemIds: List<Long>, editingGroupId: Long?, targetGroupId: Long?, folder: String, name: String): Boolean {
        if (orderedFavoriteItems(store.itemsSnapshot(), resultItemIds).isNullOrEmpty() || folder.isBlank() || name.isBlank()) return false
        if (targetGroupId == null && store.hasFavoriteIdentity(folder, name, setOfNotNull(editingGroupId))) return false
        val allocatedGroupId = targetGroupId ?: run {
            val maxGroupId = store.maxFavoriteGroupId()
            require(maxGroupId >= 0L && maxGroupId < Long.MAX_VALUE) { "收藏文件 ID 已达到存储上限" }
            maxGroupId + 1L
        }
        val deleteGroupIds = buildSet {
            store.favoriteGroupIdsForIdentity(folder, name)
                .filter { it != targetGroupId }
                .forEach(::add)
            editingGroupId?.takeIf { it != targetGroupId }?.let(::add)
        }
        onMutationStarted()
        val currentItems = store.itemsSnapshot()
        val selectedItems = orderedFavoriteItems(currentItems, resultItemIds) ?: return false
        if (selectedItems.isEmpty()) return false
        val selectedItemIds = selectedItems.mapTo(mutableSetOf()) { it.id }
        val savedAt = System.currentTimeMillis()
        val updatedGroup = FavoriteGroup(
            allocatedGroupId,
            folder,
            name,
            savedAt,
            selectedItems.map { it.id }.toMutableList(),
        )
        val groupSnapshot = store.groupsSnapshot().filterNot { it.id in deleteGroupIds }.toMutableList()
        val targetIndex = groupSnapshot.indexOfFirst { it.id == allocatedGroupId }
        if (targetIndex >= 0) groupSnapshot[targetIndex] = updatedGroup else groupSnapshot.add(0, updatedGroup)
        groupSnapshot.sortWith(compareByDescending<FavoriteGroup> { it.savedAt }.thenByDescending { it.id })
        val folderSnapshot = store.foldersSnapshot().toMutableList().apply {
            if (folder !in this) add(folder)
        }
        val itemSnapshot = currentItems.map { item ->
            if (item.id in selectedItemIds) item.copy(favorite = true, folder = folder) else item
        }
        val replaceGroupLinkIds = (store.loadedGroupLinkIdsSnapshot() - deleteGroupIds) + allocatedGroupId

        // 先提交独立快照；失败时共享内存保持原样，避免恢复读取也失败后泄漏乐观状态。
        persistence.persistAllFavorites(
            itemSnapshot,
            groupSnapshot,
            folderSnapshot,
            replaceGroupLinkIds,
            deleteGroupIds,
        ).await().getOrThrow()

        store.edit {
            items.filter { it.id in selectedItemIds }.forEach { item ->
                item.favorite = true
                item.folder = folder
            }
            groups.removeAll { it.id in deleteGroupIds || it.id == allocatedGroupId }
            groups.add(0, updatedGroup)
            sortGroupsByRecency()
            if (folder !in folders) folders.add(folder)
        }
        store.removeFavoriteGroups(deleteGroupIds)
        store.setFavoriteIdentity(allocatedGroupId, folder, name)
        store.markGroupLinksChanged(allocatedGroupId)
        persistence.refreshFavoriteFlags(store, onReconciled)
        return true
    }

    fun updateGroup(groupId: Long, name: String, folder: String): Boolean {
        if (store.hasFavoriteIdentity(folder, name, setOf(groupId))) return false
        if (store.groupsSnapshot().none { it.id == groupId }) return false
        onMutationStarted()
        val updated = store.edit {
            val index = groups.indexOfFirst { it.id == groupId }
            if (index < 0) return@edit false
            val group = groups[index]
            groups[index] = group.copy(
                name = name,
                folder = folder,
                savedAt = System.currentTimeMillis(),
            )
            sortGroupsByRecency()
            if (folder !in folders) folders.add(folder)
            true
        }
        if (!updated) return false
        store.setFavoriteIdentity(groupId, folder, name)
        persistAllFavorites()
        return true
    }

    suspend fun renameFolderAndPersist(path: String, renamedPath: String): Boolean {
        val savedAt = System.currentTimeMillis()
        if (!canRenameFolder(path, renamedPath)) return false
        onMutationStarted()
        persistence.renameFavoriteFolder(path, renamedPath, savedAt).await().getOrThrow()
        applyFolderRename(path, renamedPath, savedAt)
        return true
    }
    suspend fun deleteFolderAndPersist(path: String) {
        onMutationStarted()
        persistence.deleteFavoriteFolder(path).await().getOrThrow()
        deleteFolder(path)
        persistence.refreshFavoriteFlags(store, onReconciled)
    }
    suspend fun renameGroupAndPersist(groupId: Long, name: String): Boolean {
        val identity = store.favoriteIdentity(groupId) ?: return false
        if (name.isBlank()) return false
        if (store.hasFavoriteIdentity(identity.folder, name, setOf(groupId))) return false
        val savedAt = System.currentTimeMillis()
        onMutationStarted()
        persistence.updateFavoriteGroupMetadata(groupId, name, identity.folder, savedAt).await().getOrThrow()
        updateLoadedGroupMetadata(groupId, name, identity.folder, savedAt)
        store.setFavoriteIdentity(groupId, identity.folder, name)
        return true
    }
    suspend fun moveGroupAndPersist(groupId: Long, folder: String): Boolean {
        val identity = store.favoriteIdentity(groupId) ?: return false
        if (store.hasFavoriteIdentity(folder, identity.name, setOf(groupId))) return false
        val savedAt = System.currentTimeMillis()
        onMutationStarted()
        persistence.updateFavoriteGroupMetadata(groupId, identity.name, folder, savedAt).await().getOrThrow()
        updateLoadedGroupMetadata(groupId, identity.name, folder, savedAt)
        store.setFavoriteIdentity(groupId, folder, identity.name)
        return true
    }

    private fun updateLoadedGroupMetadata(groupId: Long, name: String, folder: String, savedAt: Long) {
        store.edit {
            val index = groups.indexOfFirst { it.id == groupId }
            if (index >= 0) groups[index] = groups[index].copy(name = name, folder = folder, savedAt = savedAt)
            sortGroupsByRecency()
            if (folder.isNotBlank() && folder !in folders) folders.add(folder)
        }
    }
    suspend fun deleteGroupAndPersist(groupId: Long) {
        if (store.favoriteIdentity(groupId) != null) {
            onMutationStarted()
            persistence.deleteFavoriteGroups(listOf(groupId)).await().getOrThrow()
            deleteGroup(groupId)
            persistence.refreshFavoriteFlags(store, onReconciled)
        }
    }
    suspend fun clearFavoritesAndPersist() {
        onMutationStarted()
        persistence.clearAllFavorites().await().getOrThrow()
        store.clearFavorites()
    }
    fun persistAllFavorites(deleteGroupIds: Set<Long> = emptySet()): kotlinx.coroutines.Deferred<Result<Unit>> {
        val commit = persistence.persistAllFavorites(
            store.itemsSnapshot(), store.groupsSnapshot(), store.foldersSnapshot(), store.loadedGroupLinkIdsSnapshot(), deleteGroupIds,
        )
        persistence.refreshFavoriteFlags(store, onReconciled)
        return commit
    }

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

/** 按生成结果页提供的顺序解析所选 ID，不使用存储中的顺序。 */
internal fun orderedFavoriteItems(items: List<CodeItem>, resultItemIds: List<Long>): List<CodeItem>? {
    val itemsById = items.associateBy(CodeItem::id)
    val orderedIds = resultItemIds.distinct()
    if (orderedIds.isEmpty()) return emptyList()
    val orderedItems = orderedIds.mapNotNull(itemsById::get)
    return orderedItems.takeIf { it.size == orderedIds.size }
}
