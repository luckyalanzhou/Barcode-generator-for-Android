package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 条码、历史与收藏的持久化协调器，隔离 ViewModel 与具体数据源。 */
@Singleton
class BarcodePersistenceCoordinator @Inject constructor(
    private val barcodeRepository: BarcodeRepository,
    private val legacyBarcodeDataMigrator: BarcodeDataMigration,
) {
    data class LoadedData(
        val items: List<CodeItem>,
        val groups: List<FavoriteGroup>,
        val folders: List<String>,
        val hasMoreGroups: Boolean,
        val identityGroups: List<FavoriteGroup>,
    )

    // The process-owned scope outlives any screen ViewModel; leaving a page must not cancel a queued write.
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeQueue = PersistenceWriteQueue(persistenceScope)
    // 应用只有一个恢复消费者；暂时没有 ViewModel 时保留失败，不能用无重放广播丢弃。
    private val _writeFailures = Channel<Throwable>(Channel.BUFFERED)
    val writeFailures: Flow<Throwable> = _writeFailures.receiveAsFlow()

    fun persistAllFavorites(
        items: List<CodeItem>,
        groups: List<FavoriteGroup>,
        folders: List<String>,
        replaceGroupLinkIds: Set<Long> = emptySet(),
    ): Deferred<Result<Unit>> {
        val itemSnapshot = items.map { it.copy() }
        val groupSnapshot = groups.map { it.copy(itemIds = it.itemIds.toMutableList()) }
        val folderSnapshot = folders.filter { it.isNotBlank() }.distinct()
        return enqueue {
            barcodeRepository.applyFavoritesMutation(
                BarcodeSnapshot(itemSnapshot, groupSnapshot, groupSnapshot.flatMap { group ->
                    group.itemIds.distinct().mapIndexed { position, itemId ->
                        FavoriteGroupItem(group.id, itemId, position)
                    }
                }, folderSnapshot, replaceGroupLinkIds),
            )
        }
    }

    fun persistItems(items: List<CodeItem>): Deferred<Result<Unit>> {
        val snapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500)).map { it.copy() }
        return enqueue { barcodeRepository.saveItems(snapshot) }
    }

    suspend fun insertGeneratedItems(items: List<CodeItem>): List<CodeItem> {
        val drafts = items.map { it.copy() }
        var inserted = emptyList<CodeItem>()
        // Share ordering with queued library writes. IDs are returned only after commit.
        enqueue { inserted = barcodeRepository.insertGeneratedItems(drafts) }.await().getOrThrow()
        return inserted
    }

    internal fun refreshFavoriteFlags(store: LibraryStateStore, onRefreshed: () -> Unit) = enqueue {
        val ids = store.itemsSnapshot().map { it.id }
        val persisted = ids.chunked(900).flatMap { barcodeRepository.loadItemsByIds(it) }.associateBy { it.id }
        store.edit {
            items.forEach { item ->
                persisted[item.id]?.let { saved ->
                    item.favorite = saved.favorite
                    item.folder = saved.folder
                }
            }
        }
        onRefreshed()
    }

    fun persistFavoriteFolders(folders: List<String>): Deferred<Result<Unit>> {
        val snapshot = folders.filter { it.isNotBlank() }.distinct()
        return enqueue { barcodeRepository.saveFavoriteFolders(snapshot) }
    }

    fun clearFavoriteFlags(itemIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue { barcodeRepository.clearFavoriteFlags(itemIds) }
    }

    fun clearAllFavoriteFlags(): Deferred<Result<Unit>> {
        return enqueue { barcodeRepository.clearAllFavoriteFlags() }
    }

    fun clearFavoriteFlagsForGroups(groupIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue { barcodeRepository.clearFavoriteFlagsForGroups(groupIds) }
    }

    fun deleteFavoriteGroups(groupIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue { barcodeRepository.deleteFavoriteGroups(groupIds) }
    }

    fun deleteItem(itemId: Long, modifiedAt: Long) = enqueue { barcodeRepository.deleteItem(itemId, modifiedAt) }

    internal suspend fun awaitPendingWrites() = writeQueue.awaitIdle()

    fun updateFavoriteGroupMetadata(groupId: Long, name: String, folder: String, savedAt: Long) = enqueue {
        barcodeRepository.updateFavoriteGroupMetadata(groupId, name, folder, savedAt)
    }

    fun clearAllFavoriteGroups(): Deferred<Result<Unit>> {
        return enqueue { barcodeRepository.clearAllFavoriteGroups() }
    }

    fun renameFavoriteFolder(path: String, renamedPath: String): Deferred<Result<Unit>> {
        return enqueue { barcodeRepository.renameFavoriteFolder(path, renamedPath) }
    }

    fun deleteFavoriteFolder(path: String): Deferred<Result<Unit>> {
        return enqueue { barcodeRepository.deleteFavoriteFolder(path) }
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
        return LoadedData(loadedItems, loadedGroups, loadedFolders, snapshot.hasMoreGroups, snapshot.identityGroups)
    }

    private fun enqueue(write: suspend () -> Unit): Deferred<Result<Unit>> {
        val deferred = writeQueue.enqueue(write)
        persistenceScope.launch {
            runCatching { deferred.await() }
                .getOrNull()
                ?.exceptionOrNull()
                ?.let { _writeFailures.send(it) }
        }
        return deferred
    }
}
