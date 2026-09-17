package com.luckyalanzhou.barcodegenerator

import android.content.SharedPreferences
import androidx.room.withTransaction
import org.json.JSONArray

/** Room 数据访问边界；UI 和 Activity 不直接负责数据库事务细节。 */
class BarcodeRepository(private val database: BarcodeDatabase) {
    private val dao get() = database.barcodeDao()

    suspend fun saveFavoriteFolders(folders: List<FavoriteFolderEntity>) {
        database.withTransaction {
            dao.clearFolders()
            dao.saveFolders(folders)
        }
    }

    suspend fun saveAllFavorites(
        items: List<CodeItemEntity>,
        groups: List<FavoriteGroupEntity>,
        links: List<FavoriteGroupItemEntity>,
        folders: List<FavoriteFolderEntity>,
    ) {
        database.withTransaction {
            dao.clearGroupItems()
            dao.clearGroups()
            dao.clearItems()
            dao.clearFolders()
            dao.saveItems(items)
            dao.saveGroups(groups)
            dao.saveGroupItems(links)
            dao.saveFolders(folders)
        }
    }

    suspend fun saveItems(items: List<CodeItemEntity>) {
        database.withTransaction {
            dao.clearItems()
            dao.saveItems(items)
        }
    }

    suspend fun saveFavoriteGroups(
        groups: List<FavoriteGroupEntity>,
        links: List<FavoriteGroupItemEntity>,
    ) {
        database.withTransaction {
            dao.clearGroupItems()
            dao.clearGroups()
            dao.saveGroups(groups)
            dao.saveGroupItems(links)
        }
    }

    suspend fun loadItems() = dao.loadItems()
    suspend fun loadGroups() = dao.loadGroups()
    suspend fun loadGroupItems() = dao.loadGroupItems()
    suspend fun loadFolders() = dao.loadFolders()

    suspend fun loadTransferEntities() = TransferEntities(
        items = dao.loadItems(),
        groups = dao.loadGroups(),
        links = dao.loadGroupItems(),
        folders = dao.loadFolders(),
    )

    suspend fun appendTransfer(transfer: TransferEntities) {
        database.withTransaction {
            dao.saveItems(transfer.items)
            dao.saveGroups(transfer.groups)
            dao.saveGroupItems(transfer.links)
            dao.saveFolders(transfer.folders)
        }
    }

    suspend fun migrateLegacyDataIfNeeded(legacyPrefs: SharedPreferences) {
        if (legacyPrefs.getBoolean("room_data_migrated", false)) return
        val legacyItems = runCatching { JSONArray(legacyPrefs.getString("items", "[]")) }.getOrDefault(JSONArray())
        val legacyGroups = runCatching { JSONArray(legacyPrefs.getString("favorite_groups", "[]")) }.getOrDefault(JSONArray())
        val legacyFolders = legacyPrefs.getStringSet("favorite_folders", emptySet()).orEmpty()
        database.withTransaction {
            if (dao.loadItems().isEmpty()) {
                val migratedItems = (0 until legacyItems.length()).mapNotNull { index ->
                    runCatching { legacyItems.getJSONObject(index) }.getOrNull()?.let { item ->
                        CodeItemEntity(item.getLong("id"), item.getString("text"), item.getString("format"), item.optLong("createdAt", item.getLong("id")), item.optBoolean("favorite"), item.optString("folder", "默认"), item.optBoolean("inHistory", true))
                    }
                }
                dao.saveItems(migratedItems)
            }
            if (dao.loadGroups().isEmpty()) {
                val groups = mutableListOf<FavoriteGroupEntity>()
                val links = mutableListOf<FavoriteGroupItemEntity>()
                for (index in 0 until legacyGroups.length()) {
                    runCatching { legacyGroups.getJSONObject(index) }.getOrNull()?.let { group ->
                        val groupId = group.getLong("id")
                        groups.add(FavoriteGroupEntity(groupId, group.optString("folder", "默认"), group.optString("name", "未命名收藏"), group.optLong("savedAt", groupId)))
                        val itemIds = group.optJSONArray("itemIds") ?: JSONArray()
                        for (itemIndex in 0 until itemIds.length()) links.add(FavoriteGroupItemEntity(groupId, itemIds.getLong(itemIndex)))
                    }
                }
                dao.saveGroups(groups)
                dao.saveGroupItems(links)
            }
            if (dao.loadFolders().isEmpty()) dao.saveFolders(legacyFolders.filter { it.isNotBlank() && it != "默认" }.map(::FavoriteFolderEntity))
        }
        legacyPrefs.edit().putBoolean("room_data_migrated", true).remove("items").remove("favorite_groups").remove("favorite_folders").remove("next_item_id").remove("next_group_id").apply()
    }
}

