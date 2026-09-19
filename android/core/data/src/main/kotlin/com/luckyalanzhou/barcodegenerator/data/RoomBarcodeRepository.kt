package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.*

import androidx.room.withTransaction
import org.json.JSONArray

/** Room 实现；只在 Data 层处理 Entity 和旧版数据迁移。 */
class RoomBarcodeRepository(private val database: BarcodeDatabase) : BarcodeRepository {
    private val dao get() = database.barcodeDao()

    override suspend fun saveAll(snapshot: BarcodeSnapshot) {
        database.withTransaction {
            dao.clearGroupItems()
            dao.clearGroups()
            dao.clearItems()
            dao.clearFolders()
            dao.saveItems(snapshot.items.map(CodeItem::toEntity))
            dao.saveGroups(snapshot.groups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) })
            dao.saveGroupItems(snapshot.links.map { FavoriteGroupItemEntity(it.groupId, it.itemId) })
            dao.saveFolders(snapshot.folders.filter { it.isNotBlank() }.distinct().map(::FavoriteFolderEntity))
        }
    }

    override suspend fun saveItems(items: List<CodeItem>) {
        database.withTransaction {
            dao.clearItems()
            dao.saveItems(items.map(CodeItem::toEntity))
        }
    }

    override suspend fun saveFavoriteGroups(groups: List<FavoriteGroup>, links: List<FavoriteGroupItem>) {
        database.withTransaction {
            dao.clearGroupItems()
            dao.clearGroups()
            dao.saveGroups(groups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) })
            dao.saveGroupItems(links.map { FavoriteGroupItemEntity(it.groupId, it.itemId) })
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

    override suspend fun loadFolders(): List<String> = dao.loadFolders().map { it.name }

    override suspend fun loadSnapshot(): BarcodeSnapshot = database.withTransaction {
        BarcodeSnapshot(
            items = dao.loadItems().map(CodeItemEntity::toDomain),
            groups = dao.loadGroups().map { FavoriteGroup(it.id, it.folder, it.name, it.savedAt, mutableListOf()) },
            links = dao.loadGroupItems().map { FavoriteGroupItem(it.groupId, it.itemId) },
            folders = dao.loadFolders().map { it.name },
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
