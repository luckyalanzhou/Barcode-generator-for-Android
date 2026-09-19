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
    private val localBarcodeFileStore: LocalBarcodeFileStore,
    private val legacyBarcodeDataMigrator: LegacyBarcodeDataMigrator,
) {
    data class LoadedData(
        val items: List<CodeItem>,
        val groups: List<FavoriteGroup>,
        val folders: List<String>,
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
        val favoriteFileGroups = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val favoriteFileItems = items.map { it.copy() }
        localBarcodeFileStore.rebuildFavorites(favoriteFileGroups, favoriteFileItems)
        val itemSnapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500)).map { it.copy() }
        val groupSnapshot = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val groupItemSnapshot = groups.flatMap { group -> group.itemIds.map { FavoriteGroupItem(group.id, it) } }
        val folderSnapshot = folders.filter { it.isNotBlank() }.distinct()
        publish()
        enqueue(scope) { barcodeRepository.saveAll(BarcodeSnapshot(itemSnapshot, groupSnapshot, groupItemSnapshot, folderSnapshot)) }
    }

    fun persistItems(scope: CoroutineScope, items: List<CodeItem>, publish: () -> Unit) {
        localBarcodeFileStore.rebuildHistory(items.map { it.copy() }.filter { it.inHistory })
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
        val links = groups.flatMap { group -> group.itemIds.map { FavoriteGroupItem(group.id, it) } }
        publish()
        enqueue(scope) { barcodeRepository.saveFavoriteGroups(snapshots, links) }
    }

    fun persistFavoriteFolders(scope: CoroutineScope, folders: List<String>, publish: () -> Unit) {
        val snapshot = folders.filter { it.isNotBlank() }.distinct()
        publish()
        enqueue(scope) { barcodeRepository.saveFavoriteFolders(snapshot) }
    }

    suspend fun load(scope: CoroutineScope? = null): LoadedData {
        legacyBarcodeDataMigrator.migrateIfNeeded()
        val snapshot = barcodeRepository.loadSnapshot()
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
        return LoadedData(loadedItems, loadedGroups, loadedFolders)
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
