package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteFileIdentity
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeDataState

/** Shared in-memory library snapshot used by favorites, history, generation, and item editing. */
internal class LibraryStateStore {
    private val lock = Any()
    private val items = mutableListOf<CodeItem>()
    private val groups = mutableListOf<FavoriteGroup>()
    private val folders = mutableListOf<String>()
    private val loadedGroupLinkIds = mutableSetOf<Long>()
    private val favoriteIdentities = mutableMapOf<Long, FavoriteFileIdentity>()

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

    fun hasFavoriteIdentity(folder: String, name: String, excludingIds: Set<Long> = emptySet()): Boolean = synchronized(lock) {
        val identity = FavoriteFileIdentity.of(folder, name)
        favoriteIdentities.any { (id, existing) -> id !in excludingIds && existing == identity }
    }

    fun favoriteIdentity(groupId: Long): FavoriteFileIdentity? = synchronized(lock) { favoriteIdentities[groupId] }

    fun favoriteIdentitiesSnapshot(): Map<Long, FavoriteFileIdentity> = synchronized(lock) { favoriteIdentities.toMap() }

    fun favoriteIdentityFolderPathsSnapshot(): List<String> = synchronized(lock) {
        favoriteIdentities.values.map { it.folder }.filter { it.isNotBlank() }
    }

    fun maxFavoriteGroupId(): Long = synchronized(lock) { favoriteIdentities.keys.maxOrNull() ?: 0L }

    fun setFavoriteIdentity(groupId: Long, folder: String, name: String) = synchronized(lock) {
        favoriteIdentities[groupId] = FavoriteFileIdentity.of(folder, name)
    }

    fun removeFavoriteIdentity(groupId: Long) = synchronized(lock) { favoriteIdentities.remove(groupId) }

    fun renameFavoriteIdentityFolders(path: String, renamedPath: String) = synchronized(lock) {
        favoriteIdentities.replaceAll { _, identity ->
            if (identity.folder == path || identity.folder.startsWith("$path/")) {
                identity.copy(folder = if (identity.folder == path) renamedPath else renamedPath + identity.folder.removePrefix(path))
            } else identity
        }
    }

    fun removeFavoriteIdentitiesInFolder(path: String) = synchronized(lock) {
        favoriteIdentities.entries.removeAll { (_, identity) ->
            identity.folder == path || identity.folder.startsWith("$path/")
        }
    }

    fun markGroupLinksLoaded(groupId: Long) = synchronized(lock) {
        if (groups.any { it.id == groupId }) loadedGroupLinkIds += groupId
    }

    fun markGroupLinksChanged(groupId: Long) = markGroupLinksLoaded(groupId)

    fun loadedGroupLinkIdsSnapshot(): Set<Long> = synchronized(lock) { loadedGroupLinkIds.toSet() }

    fun replace(
        newItems: List<CodeItem>,
        newGroups: List<FavoriteGroup>,
        newFolders: List<String>,
        identityGroups: List<FavoriteGroup> = newGroups,
    ) {
        edit {
            items.clear()
            items.addAll(newItems)
            groups.clear()
            groups.addAll(newGroups)
            loadedGroupLinkIds.clear()
            loadedGroupLinkIds.addAll(newGroups.filter { it.itemIds.isNotEmpty() }.map { it.id })
            favoriteIdentities.clear()
            favoriteIdentities.putAll(identityGroups.associate { it.id to FavoriteFileIdentity.of(it.folder, it.name) })
            folders.clear()
            folders.addAll(newFolders)
        }
    }

    fun clearFavorites() = edit {
        groups.clear()
        folders.clear()
        loadedGroupLinkIds.clear()
        favoriteIdentities.clear()
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
