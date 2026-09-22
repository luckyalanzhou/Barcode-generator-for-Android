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
        runCatching { externalFavoritesStore.ensureManagedRoot() }
            .onFailure { error ->
                com.luckyalanzhou.barcodegenerator.ui.DebugLog.record(
                    "favorites",
                    "external favorites root initialization deferred",
                    error,
                )
            }
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
        if (!force && externalFavoritesStore.isRestoreRequired()) return 0

        // Keep normal startup Room-only, but an explicit user restore must also
        // repair a partially damaged Room index. Previously the mere presence of
        // any group short-circuited restoration, even when many groups had no links.
        if (roomGroups.isNotEmpty()) {
            if (!force) return 0
            var restoredGroups = 0
            roomGroups.forEach { group ->
                val itemIds = barcodeRepository.loadGroupItemIds(group.id)
                val linkedItems = barcodeRepository.loadItemsByIds(itemIds)
                val isHealthy = itemIds.isNotEmpty() && linkedItems.size == itemIds.size
                if (!isHealthy && repairExternalFavorite(group).isNotEmpty()) restoredGroups++
            }
            if (restoredGroups > 0) {
                externalFavoritesStore.markRestoreRequired(false)
                com.luckyalanzhou.barcodegenerator.ui.DebugLog.record(
                    "favorites",
                    "partial external restore completed groups=$restoredGroups roomGroups=${roomGroups.size}",
                )
            }
            return restoredGroups
        }

        // With an empty Room favorite index, restore the complete external snapshot
        // while preserving history rows and allocating IDs that cannot overwrite them.
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
        // Room is the runtime source of truth. Once the database transaction has
        // committed, a failure to refresh the uninstall-safe mirror must not report
        // that the restore itself failed or roll the UI back to the empty snapshot.
        // syncExternalFavorites marks the mirror pending before writing, so startup
        // can retry it if the shared-storage provider is temporarily unavailable.
        runCatching { syncExternalFavorites() }
            .onFailure { error ->
                com.luckyalanzhou.barcodegenerator.ui.DebugLog.record(
                    "favorites",
                    "external mirror refresh deferred after restore",
                    error,
                )
            }
    }

    /** Restores only the favorite document the user is opening. */
    suspend fun repairExternalFavorite(group: FavoriteGroup): List<CodeItem> {
        // Do not let a stale external mirror overwrite a healthy Room result. This
        // check is important after app updates: Room IDs are the live index and the
        // external document may have been written by an older app version/device.
        val roomItemIds = barcodeRepository.loadGroupItemIds(group.id)
        val roomItems = barcodeRepository.loadItemsByIds(roomItemIds)
        if (roomItemIds.isNotEmpty() && roomItems.size == roomItemIds.size) return emptyList()

        val external = externalFavoritesStore.readFavorite(group)?.takeIf { it.second.isNotEmpty() }
            ?: externalFavoritesStore.readSnapshot()
                ?.let { snapshot ->
                    val match = snapshot.groups.firstOrNull {
                        it.folder.trim('/') == group.folder.trim('/') && it.name == group.name
                    } ?: return@let null
                    val byId = snapshot.items.associateBy { it.id }
                    match to match.itemIds.mapNotNull(byId::get)
                }
        val externalItems = external?.second.orEmpty()
        if (externalItems.isEmpty()) {
            com.luckyalanzhou.barcodegenerator.ui.DebugLog.record(
                "favorites",
                "favorite repair unavailable groupId=${group.id} name=${group.name} roomLinks=${roomItemIds.size} roomItems=${roomItems.size}",
            )
            return emptyList()
        }

        // External IDs can collide with history or with IDs retained by a previous
        // recovery attempt. Allocate fresh IDs and atomically replace only this
        // favorite group's links, preserving every unrelated Room row.
        val existingItemIds = barcodeRepository.loadItems().asSequence().map { it.id }.toHashSet()
        var nextId = (existingItemIds.maxOrNull() ?: 0L) + 1L
        val restoredItems = externalItems.map { item ->
            while (nextId in existingItemIds) nextId++
            item.copy(id = nextId++, favorite = true, inHistory = false).also { existingItemIds += it.id }
        }
        val restoredGroup = group.copy(itemIds = restoredItems.map { it.id }.toMutableList())
        applyFavoriteRepair(restoredGroup, restoredItems)
        com.luckyalanzhou.barcodegenerator.ui.DebugLog.record(
            "favorites",
            "favorite repaired from external groupId=${group.id} name=${group.name} previousLinks=${roomItemIds.size} restoredItems=${restoredItems.size}",
        )
        return restoredItems
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
