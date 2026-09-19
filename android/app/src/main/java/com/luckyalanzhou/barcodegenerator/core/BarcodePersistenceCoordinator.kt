package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.*
import com.luckyalanzhou.barcodegenerator.domain.*

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 条码、历史与收藏的持久化协调器，隔离 ViewModel 与具体数据源。 */
class BarcodePersistenceCoordinator(
    private val barcodeRepository: BarcodeRepository,
    private val legacyBarcodeDataMigrator: LegacyBarcodeDataMigrator,
) {
    data class LoadedData(
        val items: List<CodeItem>,
        val groups: List<FavoriteGroup>,
        val folders: List<String>,
        val hasMoreGroups: Boolean,
    )

    private val persistenceLock = Any()
    private var persistenceWriteTail: Job? = null

    fun persistAllFavorites(
        scope: CoroutineScope,
        items: List<CodeItem>,
        groups: List<FavoriteGroup>,
        folders: List<String>,
        publish: () -> Unit,
    ) {
        val itemSnapshot = items.map { it.copy() }
        val groupSnapshot = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val folderSnapshot = folders.filter { it.isNotBlank() }.distinct()
        val groupsWithLoadedLinks = groupSnapshot.filter { it.itemIds.isNotEmpty() }
        publish()
        enqueue(scope) {
            barcodeRepository.upsertItems(itemSnapshot)
            barcodeRepository.saveFavoriteGroupMetadata(groupSnapshot)
            barcodeRepository.saveFavoriteGroupLinks(groupsWithLoadedLinks)
            barcodeRepository.saveFavoriteFolders(folderSnapshot)
        }
    }

    fun persistItems(scope: CoroutineScope, items: List<CodeItem>, publish: () -> Unit) {
        val snapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500)).map { it.copy() }
        publish()
        enqueue(scope) { barcodeRepository.saveItems(snapshot) }
    }

    fun persistFavoriteGroups(
        scope: CoroutineScope,
        groups: List<FavoriteGroup>,
        publish: () -> Unit,
    ) {
        val snapshots = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val groupsWithLoadedLinks = snapshots.filter { it.itemIds.isNotEmpty() }
        publish()
        enqueue(scope) {
            barcodeRepository.saveFavoriteGroupMetadata(snapshots)
            barcodeRepository.saveFavoriteGroupLinks(groupsWithLoadedLinks)
        }
    }

    fun persistFavoriteFolders(scope: CoroutineScope, folders: List<String>, publish: () -> Unit) {
        val snapshot = folders.filter { it.isNotBlank() }.distinct()
        publish()
        enqueue(scope) { barcodeRepository.saveFavoriteFolders(snapshot) }
    }

    fun clearFavoriteFlags(scope: CoroutineScope, itemIds: List<Long>) {
        enqueue(scope) { barcodeRepository.clearFavoriteFlags(itemIds) }
    }

    fun clearAllFavoriteFlags(scope: CoroutineScope) {
        enqueue(scope) { barcodeRepository.clearAllFavoriteFlags() }
    }

    fun clearFavoriteFlagsForGroups(scope: CoroutineScope, groupIds: List<Long>) {
        enqueue(scope) { barcodeRepository.clearFavoriteFlagsForGroups(groupIds) }
    }

    fun deleteFavoriteGroups(scope: CoroutineScope, groupIds: List<Long>) {
        enqueue(scope) { barcodeRepository.deleteFavoriteGroups(groupIds) }
    }

    fun clearAllFavoriteGroups(scope: CoroutineScope) {
        enqueue(scope) { barcodeRepository.clearAllFavoriteGroups() }
    }

    fun renameFavoriteFolder(scope: CoroutineScope, path: String, renamedPath: String) {
        enqueue(scope) { barcodeRepository.renameFavoriteFolder(path, renamedPath) }
    }

    fun deleteFavoriteFolder(scope: CoroutineScope, path: String) {
        enqueue(scope) { barcodeRepository.deleteFavoriteFolder(path) }
    }

    suspend fun load(scope: CoroutineScope? = null): LoadedData {
        legacyBarcodeDataMigrator.migrateIfNeeded()
        val snapshot = barcodeRepository.loadStartupSnapshot()
        val loadedGroups = snapshot.groups.map { group ->
            FavoriteGroup(
                group.id,
                group.folder.takeUnless { it == "默认" } ?: "",
                group.name,
                group.savedAt,
                snapshot.links.filter { it.groupId == group.id }.map { it.itemId }.toMutableList(),
            )
        }
        val loadedFolders = (snapshot.folders + loadedGroups.map { it.folder })
            .filter { it.isNotBlank() && it != "默认" }
            .distinct()
            .sorted()
        val loadedItems = snapshot.items.map { it.copy(folder = it.folder.takeUnless { folder -> folder == "默认" } ?: "") }
        return LoadedData(loadedItems, loadedGroups, loadedFolders, snapshot.hasMoreGroups)
    }

    private fun enqueue(scope: CoroutineScope, write: suspend () -> Unit) {
        val next: Job
        synchronized(persistenceLock) {
            val previous = persistenceWriteTail
            next = scope.launch(Dispatchers.IO) {
                previous?.join()
                write()
            }
            persistenceWriteTail = next
        }
        next.invokeOnCompletion {
            synchronized(persistenceLock) {
                if (persistenceWriteTail === next) persistenceWriteTail = null
            }
        }
    }
}
