package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.StartupBarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.LegacyBarcodeData
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupPageCursor

import androidx.room.withTransaction
import org.json.JSONArray

/** Room 实现；只在 Data 层处理 Entity 和旧版数据迁移。 */
class RoomBarcodeRepository(private val database: BarcodeDatabase) : BarcodeRepository {
    private val dao get() = database.barcodeDao()

    override suspend fun saveAll(snapshot: BarcodeSnapshot) {
        database.withTransaction {
            val retainedItemIds = snapshot.items.map { it.id }
            if (retainedItemIds.isEmpty()) dao.clearItems()
            else dao.deleteItemsExcept(retainedItemIds)
            val retainedGroupIds = snapshot.groups.map { it.id }
            if (retainedGroupIds.isEmpty()) {
                dao.clearGroupItems()
                dao.clearGroups()
            } else {
                dao.deleteGroupItemsExcept(retainedGroupIds)
                dao.deleteGroupsExcept(retainedGroupIds)
                dao.clearGroupItemsForGroups(retainedGroupIds)
            }
            dao.clearFolders()
            dao.saveItems(snapshot.items.map(CodeItem::toEntity))
            dao.saveGroups(snapshot.groups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) })
            dao.saveGroupItems(snapshot.links.map { FavoriteGroupItemEntity(it.groupId, it.itemId) })
            dao.saveFolders(snapshot.folders.filter { it.isNotBlank() }.distinct().map(::FavoriteFolderEntity))
        }
    }

    override suspend fun applyFavoritesMutation(snapshot: BarcodeSnapshot) {
        database.withTransaction {
            // The in-memory store may contain only the loaded favorite pages. Upsert the
            // supplied rows and replace links only for those groups; never delete unloaded data.
            dao.saveItems(snapshot.items.map(CodeItem::toEntity))
            if (snapshot.groups.isNotEmpty()) {
                val groupIds = snapshot.groups.map { it.id }
                dao.saveGroups(snapshot.groups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) })
                dao.clearGroupItemsForGroups(groupIds)
                dao.saveGroupItems(snapshot.groups.flatMap { group ->
                    group.itemIds.map { FavoriteGroupItemEntity(group.id, it) }
                })
            }
            dao.clearFolders()
            dao.saveFolders(snapshot.folders.filter { it.isNotBlank() }.distinct().map(::FavoriteFolderEntity))
        }
    }

    override suspend fun saveItems(items: List<CodeItem>) {
        database.withTransaction {
            val retainedHistoryIds = items.filter { !it.favorite }.map { it.id }
            if (retainedHistoryIds.isEmpty()) dao.clearNonFavoriteItems()
            else dao.deleteNonFavoriteItemsExcept(retainedHistoryIds)
            dao.saveItems(items.map(CodeItem::toEntity))
        }
    }

    override suspend fun upsertItems(items: List<CodeItem>) {
        if (items.isNotEmpty()) dao.saveItems(items.map(CodeItem::toEntity))
    }

    override suspend fun loadItemsByIds(ids: List<Long>): List<CodeItem> =
        if (ids.isEmpty()) emptyList() else dao.loadItemsByIds(ids).map(CodeItemEntity::toDomain)

    override suspend fun searchFavoriteItems(query: String): List<CodeItem> =
        if (query.isBlank()) emptyList() else dao.searchFavoriteItems(query).map(CodeItemEntity::toDomain)

    override suspend fun clearFavoriteFlags(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.clearFavoriteFlags(ids)
    }

    override suspend fun clearFavoriteFlagsForGroups(groupIds: List<Long>) {
        if (groupIds.isNotEmpty()) dao.clearFavoriteFlagsForGroups(groupIds)
    }

    override suspend fun clearAllFavoriteFlags() = dao.clearAllFavoriteFlags()

    override suspend fun saveFavoriteGroups(groups: List<FavoriteGroup>, links: List<FavoriteGroupItem>) {
        database.withTransaction {
            val retainedGroupIds = groups.map { it.id }
            if (retainedGroupIds.isEmpty()) {
                dao.clearGroupItems()
                dao.clearGroups()
            } else {
                dao.deleteGroupItemsExcept(retainedGroupIds)
                dao.deleteGroupsExcept(retainedGroupIds)
                dao.clearGroupItemsForGroups(retainedGroupIds)
            }
            dao.saveGroups(groups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) })
            dao.saveGroupItems(links.map { FavoriteGroupItemEntity(it.groupId, it.itemId) })
        }
    }

    override suspend fun saveFavoriteGroupMetadata(groups: List<FavoriteGroup>) {
        if (groups.isNotEmpty()) dao.saveGroups(groups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) })
    }

    override suspend fun saveFavoriteGroupLinks(groups: List<FavoriteGroup>) {
        if (groups.isEmpty()) return
        database.withTransaction {
            val ids = groups.map { it.id }
            dao.clearGroupItemsForGroups(ids)
            dao.saveGroupItems(groups.flatMap { group -> group.itemIds.map { FavoriteGroupItemEntity(group.id, it) } })
        }
    }

    override suspend fun deleteFavoriteGroups(ids: List<Long>) {
        if (ids.isEmpty()) return
        database.withTransaction {
            dao.clearFavoriteFlagsForGroups(ids)
            dao.deleteGroupItems(ids)
            dao.deleteGroups(ids)
        }
    }

    override suspend fun clearAllFavoriteGroups() {
        database.withTransaction {
            dao.clearAllFavoriteFlags()
            dao.clearGroupItems()
            dao.clearGroups()
        }
    }

    override suspend fun renameFavoriteFolder(path: String, renamedPath: String) {
        val prefix = "$path/%"
        database.withTransaction {
            dao.renameGroupsFolder(path, prefix, renamedPath)
            dao.renameFolders(path, prefix, renamedPath)
        }
    }

    override suspend fun deleteFavoriteFolder(path: String) {
        val prefix = "$path/%"
        database.withTransaction {
            val groupIds = dao.loadGroupIdsByFolder(path, prefix)
            if (groupIds.isNotEmpty()) {
                dao.clearFavoriteFlagsForGroups(groupIds)
                dao.deleteGroupItems(groupIds)
            }
            dao.deleteGroupsByFolder(path, prefix)
            dao.deleteFoldersByPath(path, prefix)
        }
    }

    override suspend fun saveFavoriteFolders(folders: List<String>) {
        database.withTransaction {
            dao.clearFolders()
            dao.saveFolders(folders.filter { it.isNotBlank() }.distinct().map(::FavoriteFolderEntity))
        }
    }

    override suspend fun loadItems(): List<CodeItem> = dao.loadItems().map(CodeItemEntity::toDomain)

    override suspend fun loadGroups(): List<FavoriteGroup> = dao.loadGroups().map {
        FavoriteGroup(it.id, it.folder, it.name, it.savedAt, mutableListOf())
    }

    override suspend fun loadGroupItems(): List<FavoriteGroupItem> =
        dao.loadGroupItems().map { FavoriteGroupItem(it.groupId, it.itemId) }

    override suspend fun loadGroupItemIds(groupId: Long): List<Long> = dao.loadGroupItemIds(groupId)

    override suspend fun loadFavoriteGroupPage(limit: Int, cursor: FavoriteGroupPageCursor?): List<FavoriteGroup> =
        dao.loadGroupsPage(limit, cursor?.savedAt, cursor?.id)
            .map { FavoriteGroup(it.id, it.folder, it.name, it.savedAt, mutableListOf()) }

    override suspend fun loadFavoriteGroupsByIds(ids: List<Long>): List<FavoriteGroup> =
        if (ids.isEmpty()) emptyList() else {
            val itemIdsByGroup = dao.loadGroupItemsByGroupIds(ids).groupBy { it.groupId }
            dao.loadGroupsByIds(ids).map { group ->
                FavoriteGroup(
                    group.id,
                    group.folder,
                    group.name,
                    group.savedAt,
                    itemIdsByGroup[group.id].orEmpty().map { it.itemId }.toMutableList(),
                )
            }
        }

    override suspend fun searchFavoriteGroupIds(query: String): List<Long> {
        if (query.isBlank()) return emptyList()
        val metadataIds = dao.searchFavoriteGroupIdsByMetadata(query)
        return (metadataIds + dao.searchFavoriteGroupIdsByContent(query)).distinct()
    }

    override suspend fun loadFolders(): List<String> = dao.loadFolders().map { it.name }

    override suspend fun loadSnapshot(): BarcodeSnapshot = database.withTransaction {
        BarcodeSnapshot(
            items = dao.loadItems().map(CodeItemEntity::toDomain),
            groups = dao.loadGroups().map { FavoriteGroup(it.id, it.folder, it.name, it.savedAt, mutableListOf()) },
            links = dao.loadGroupItems().map { FavoriteGroupItem(it.groupId, it.itemId) },
            folders = dao.loadFolders().map { it.name },
        )
    }

    override suspend fun loadStartupSnapshot(): StartupBarcodeSnapshot = database.withTransaction {
        val startupGroups = dao.loadGroupsPage(101, null, null)
        StartupBarcodeSnapshot(
            items = dao.loadStartupItems().map(CodeItemEntity::toDomain),
            groups = startupGroups.take(100).map { FavoriteGroup(it.id, it.folder, it.name, it.savedAt, mutableListOf()) },
            links = emptyList(),
            folders = dao.loadFolders().map { it.name },
            hasMoreGroups = startupGroups.size > 100,
        )
    }

    override suspend fun appendSnapshot(snapshot: BarcodeSnapshot) {
        database.withTransaction {
            dao.saveItems(snapshot.items.map(CodeItem::toEntity))
            dao.saveGroups(snapshot.groups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) })
            dao.saveGroupItems(snapshot.links.map { FavoriteGroupItemEntity(it.groupId, it.itemId) })
            dao.saveFolders(snapshot.folders.filter { it.isNotBlank() }.distinct().map(::FavoriteFolderEntity))
        }
    }

    override suspend fun migrateLegacyDataIfNeeded(legacy: LegacyBarcodeData) {
        if (legacy.itemsJson == null && legacy.groupsJson == null && legacy.folders.isEmpty()) return
        database.withTransaction {
            if (dao.loadItems().isEmpty()) {
                val items = runCatching { JSONArray(legacy.itemsJson ?: "[]") }.getOrDefault(JSONArray())
                    .let { array ->
                        (0 until array.length()).mapNotNull { index ->
                            runCatching {
                                array.getJSONObject(index).let { item ->
                                    CodeItemEntity(
                                        item.getLong("id"), item.getString("text"), item.getString("format"),
                                        item.optLong("createdAt", item.getLong("id")), item.optBoolean("favorite"),
                                        item.optString("folder", "默认"), item.optBoolean("inHistory", true),
                                    )
                                }
                            }.getOrNull()
                        }
                    }
                dao.saveItems(items)
            }
            if (dao.loadGroups().isEmpty()) {
                val groups = mutableListOf<FavoriteGroupEntity>()
                val links = mutableListOf<FavoriteGroupItemEntity>()
                val array = runCatching { JSONArray(legacy.groupsJson ?: "[]") }.getOrDefault(JSONArray())
                for (index in 0 until array.length()) {
                    runCatching {
                        array.getJSONObject(index).let { group ->
                            val groupId = group.getLong("id")
                            groups += FavoriteGroupEntity(groupId, group.optString("folder", "默认"), group.optString("name", "未命名收藏"), group.optLong("savedAt", groupId))
                            val itemIds = group.optJSONArray("itemIds") ?: JSONArray()
                            for (itemIndex in 0 until itemIds.length()) {
                                runCatching { itemIds.getLong(itemIndex) }
                                    .getOrNull()
                                    ?.let { links += FavoriteGroupItemEntity(groupId, it) }
                            }
                        }
                    }
                }
                dao.saveGroups(groups)
                dao.saveGroupItems(links)
            }
            if (dao.loadFolders().isEmpty()) dao.saveFolders(legacy.folders.filter { it.isNotBlank() && it != "默认" }.map(::FavoriteFolderEntity))
        }
    }
}
