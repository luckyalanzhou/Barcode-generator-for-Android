package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 条码、历史与收藏的持久化协调器，隔离 ViewModel 与具体数据源。 */
class BarcodePersistenceCoordinator(
    private val barcodeRepository: BarcodeRepository,
    private val legacyBarcodeDataMigrator: BarcodeDataMigration,
) {
    data class LoadedData(
        val items: List<CodeItem>,
        val groups: List<FavoriteGroup>,
        val folders: List<String>,
        val hasMoreGroups: Boolean,
    )

    private val writeQueue = PersistenceWriteQueue()
    private val _writeFailures = MutableSharedFlow<Throwable>(extraBufferCapacity = 8)
    val writeFailures: SharedFlow<Throwable> = _writeFailures.asSharedFlow()

    fun persistAllFavorites(
        scope: CoroutineScope,
        items: List<CodeItem>,
        groups: List<FavoriteGroup>,
        folders: List<String>,
        replaceGroupLinkIds: Set<Long> = emptySet(),
    ): Deferred<Result<Unit>> {
        val itemSnapshot = items.map { it.copy() }
        val groupSnapshot = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val folderSnapshot = folders.filter { it.isNotBlank() }.distinct()
        return enqueue(scope) {
            barcodeRepository.applyFavoritesMutation(
                BarcodeSnapshot(itemSnapshot, groupSnapshot, groupSnapshot.flatMap { group ->
                    group.itemIds.map { itemId -> com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem(group.id, itemId) }
                }, folderSnapshot, replaceGroupLinkIds),
            )
        }
    }

    fun persistItems(scope: CoroutineScope, items: List<CodeItem>): Deferred<Result<Unit>> {
        val snapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500)).map { it.copy() }
        return enqueue(scope) { barcodeRepository.saveItems(snapshot) }
    }

    fun persistFavoriteFolders(scope: CoroutineScope, folders: List<String>): Deferred<Result<Unit>> {
        val snapshot = folders.filter { it.isNotBlank() }.distinct()
        return enqueue(scope) { barcodeRepository.saveFavoriteFolders(snapshot) }
    }

    fun clearFavoriteFlags(scope: CoroutineScope, itemIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.clearFavoriteFlags(itemIds) }
    }

    fun clearAllFavoriteFlags(scope: CoroutineScope): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.clearAllFavoriteFlags() }
    }

    fun clearFavoriteFlagsForGroups(scope: CoroutineScope, groupIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.clearFavoriteFlagsForGroups(groupIds) }
    }

    fun deleteFavoriteGroups(scope: CoroutineScope, groupIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.deleteFavoriteGroups(groupIds) }
    }

    fun clearAllFavoriteGroups(scope: CoroutineScope): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.clearAllFavoriteGroups() }
    }

    fun renameFavoriteFolder(scope: CoroutineScope, path: String, renamedPath: String): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.renameFavoriteFolder(path, renamedPath) }
    }

    fun deleteFavoriteFolder(scope: CoroutineScope, path: String): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.deleteFavoriteFolder(path) }
    }

    suspend fun load(): LoadedData {
        writeQueue.awaitIdle()
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

    suspend fun awaitPendingWrites() = writeQueue.awaitIdle()

    private fun enqueue(scope: CoroutineScope, write: suspend () -> Unit): Deferred<Result<Unit>> {
        val deferred = writeQueue.enqueue(scope, write)
        scope.launch {
            runCatching { deferred.await() }
                .getOrNull()
                ?.exceptionOrNull()
                ?.let { _writeFailures.emit(it) }
        }
        return deferred
    }
}
