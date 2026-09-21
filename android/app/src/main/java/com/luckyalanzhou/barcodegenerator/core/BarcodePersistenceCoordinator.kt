package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.data.ExternalFavoritesStore

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
    private val externalFavoritesStore: ExternalFavoritesStore,
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
        syncExternal: Boolean = true,
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
            if (syncExternal) syncExternalFavorites()
        }
    }

    fun persistItems(scope: CoroutineScope, items: List<CodeItem>): Deferred<Result<Unit>> {
        val snapshot = (items.filter { it.favorite } + items.filterNot { it.favorite }.take(500)).map { it.copy() }
        return enqueue(scope) { barcodeRepository.saveItems(snapshot); syncExternalFavorites() }
    }

    fun persistFavoriteFolders(scope: CoroutineScope, folders: List<String>): Deferred<Result<Unit>> {
        val snapshot = folders.filter { it.isNotBlank() }.distinct()
        return enqueue(scope) { barcodeRepository.saveFavoriteFolders(snapshot); syncExternalFavorites() }
    }

    fun clearFavoriteFlags(scope: CoroutineScope, itemIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.clearFavoriteFlags(itemIds); syncExternalFavorites() }
    }

    fun clearAllFavoriteFlags(scope: CoroutineScope): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.clearAllFavoriteFlags(); syncExternalFavorites() }
    }

    fun clearFavoriteFlagsForGroups(scope: CoroutineScope, groupIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.clearFavoriteFlagsForGroups(groupIds); syncExternalFavorites() }
    }

    fun deleteFavoriteGroups(scope: CoroutineScope, groupIds: List<Long>): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.deleteFavoriteGroups(groupIds); syncExternalFavorites() }
    }

    fun clearAllFavoriteGroups(scope: CoroutineScope): Deferred<Result<Unit>> {
        // Keep the external mirror as a recovery source when app favorites are cleared.
        return enqueue(scope) {
            barcodeRepository.clearAllFavoriteGroups()
            externalFavoritesStore.markRestoreRequired(true)
        }
    }

    fun renameFavoriteFolder(scope: CoroutineScope, path: String, renamedPath: String): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.renameFavoriteFolder(path, renamedPath); syncExternalFavorites() }
    }

    fun deleteFavoriteFolder(scope: CoroutineScope, path: String): Deferred<Result<Unit>> {
        return enqueue(scope) { barcodeRepository.deleteFavoriteFolder(path); syncExternalFavorites() }
    }

    suspend fun load(): LoadedData {
        writeQueue.awaitIdle()
        legacyBarcodeDataMigrator.migrateIfNeeded()
        repairFromExternalFavorites()
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
        val startupSnapshot = BarcodeSnapshot(snapshot.items, snapshot.groups, snapshot.links, snapshot.folders)
        if (externalFavoritesStore.isSyncPending()) {
            runCatching { syncExternalFavorites() }
                .onFailure { error ->
                    com.luckyalanzhou.barcodegenerator.ui.DebugLog.record(
                        "favorites",
                        "external mirror retry deferred",
                        error,
                    )
                }
        } else {
            runCatching { externalFavoritesStore.ensureSharedMirror(startupSnapshot) }
                .onFailure { error ->
                    runCatching { externalFavoritesStore.markSyncPending(true) }
                    com.luckyalanzhou.barcodegenerator.ui.DebugLog.record(
                        "favorites",
                        "external mirror initialization deferred",
                        error,
                    )
                }
        }
        return LoadedData(loadedItems, loadedGroups, loadedFolders, snapshot.hasMoreGroups)
    }

    /** Restores missing favorite links/items from the uninstall-safe external mirror. */
    suspend fun repairFromExternalFavorites(force: Boolean = false): Int {
        val roomGroups = barcodeRepository.loadGroups()
        // Room is the fast runtime index and survives app updates. Do not scan the
        // shared-storage mirror on every cold start: that made every update look
        // like a restore operation and delayed the favorites page. The mirror is
        // only a recovery source when the Room favorite index is completely absent.
        if (roomGroups.isNotEmpty()) return 0
        if (!force && externalFavoritesStore.isRestoreRequired()) return 0
        externalFavoritesStore.readSnapshot()?.let { externalSnapshot ->
            // Recovery must never replace the whole database: Room may still contain
            // history rows even when the favorite index was lost. Allocate fresh IDs
            // so restoring external favorites cannot overwrite those rows.
            val existingItems = barcodeRepository.loadItems()
            val existingFolders = barcodeRepository.loadFolders()
            val itemIds = externalSnapshot.items.map { it.id }.distinct()
            val itemIdMap = itemIds.mapIndexed { index, oldId ->
                oldId to (existingItems.maxOfOrNull { it.id } ?: 0L) + index + 1L
            }.toMap()
            val groupIdMap = externalSnapshot.groups.mapIndexed { index, group ->
                group.id to ((roomGroups.maxOfOrNull { it.id } ?: 0L) + index + 1L)
            }.toMap()
            val restoredItems = externalSnapshot.items.distinctBy { it.id }.map { item ->
                item.copy(id = itemIdMap.getValue(item.id), favorite = true, inHistory = false)
            }
            val restoredGroups = externalSnapshot.groups.map { group ->
                group.copy(
                    id = groupIdMap.getValue(group.id),
                    itemIds = group.itemIds.mapNotNull { itemIdMap[it] }.toMutableList(),
                )
            }
            val restoredLinks = restoredGroups.flatMap { group ->
                group.itemIds.map { itemId ->
                    com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem(group.id, itemId)
                }
            }
            barcodeRepository.appendSnapshot(
                BarcodeSnapshot(
                    items = restoredItems,
                    groups = restoredGroups,
                    links = restoredLinks,
                    folders = (existingFolders + externalSnapshot.folders).distinct(),
                ),
            )
            externalFavoritesStore.markRestoreRequired(false)
            return externalSnapshot.groups.size
        }
        return 0
    }

    fun restoreExternalFavorites(scope: CoroutineScope): Deferred<Result<Unit>> = enqueue(scope) {
        val restoredCount = repairFromExternalFavorites(force = true)
        check(restoredCount > 0) { "外部目录中没有可恢复的收藏文件" }
        syncExternalFavorites()
    }

    /** Restores only the favorite document the user is opening. */
    suspend fun repairExternalFavorite(group: FavoriteGroup): List<CodeItem> {
        val external = externalFavoritesStore.readFavorite(group) ?: return emptyList()
        val externalGroup = group.copy(itemIds = external.second.map { it.id }.toMutableList())
        if (external.second.isEmpty()) return emptyList()
        applyFavoriteRepair(externalGroup, external.second)
        return external.second
    }

    private suspend fun applyFavoriteRepair(group: FavoriteGroup, items: List<CodeItem>) {
        barcodeRepository.applyFavoritesMutation(
            BarcodeSnapshot(
                items = items,
                groups = listOf(group),
                links = group.itemIds.map { itemId ->
                    com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem(group.id, itemId)
                },
                folders = (barcodeRepository.loadFolders() + group.folder).filter { it.isNotBlank() }.distinct(),
                replaceGroupLinkIds = setOf(group.id),
            ),
        )
    }

    suspend fun awaitPendingWrites() = writeQueue.awaitIdle()

    internal suspend fun syncExternalFavorites() {
        val snapshot = barcodeRepository.loadSnapshot()
        // After an intentional in-app clear, history writes can still arrive with
        // an empty favorite set. Never mirror that empty snapshot over the retained
        // external recovery files; only an explicit restore or a new favorite may
        // replace the retained mirror.
        if (snapshot.groups.isEmpty() && externalFavoritesStore.isRestoreRequired()) return
        externalFavoritesStore.markSyncPending(true)
        externalFavoritesStore.mirror(snapshot)
        externalFavoritesStore.markSyncPending(false)
        externalFavoritesStore.markRestoreRequired(false)
    }

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
