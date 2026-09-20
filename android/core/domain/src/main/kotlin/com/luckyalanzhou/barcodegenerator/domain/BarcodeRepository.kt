package com.luckyalanzhou.barcodegenerator.domain

/** 收藏分组与条码之间的持久化关联。 */
data class FavoriteGroupItem(
    val groupId: Long,
    val itemId: Long,
)

/** 持久化边界使用的领域快照，不暴露 Room Entity。 */
data class BarcodeSnapshot(
    val items: List<CodeItem>,
    val groups: List<FavoriteGroup>,
    val links: List<FavoriteGroupItem>,
    val folders: List<String>,
)

/** Stable cursor for favorite-group paging; avoids OFFSET drift after mutations. */
data class FavoriteGroupPageCursor(
    val savedAt: Long,
    val id: Long,
)

data class FavoriteSearchGroupCursor(
    val savedAt: Long,
    val id: Long,
)

data class FavoriteSearchItemCursor(
    val createdAt: Long,
    val id: Long,
)

/** 启动快照：只保留收藏条码和最近历史，避免旧历史无限增长拖慢冷启动。 */
data class StartupBarcodeSnapshot(
    val items: List<CodeItem>,
    val groups: List<FavoriteGroup>,
    val links: List<FavoriteGroupItem>,
    val folders: List<String>,
    val hasMoreGroups: Boolean,
)

/** 从旧版 SharedPreferences 提取出的纯 Kotlin 迁移输入。 */
data class LegacyBarcodeData(
    val itemsJson: String?,
    val groupsJson: String?,
    val folders: Set<String>,
)

/** Domain 定义的持久化端口；具体存储实现由 Data 层提供。 */
interface BarcodeRepository {
    suspend fun saveAll(snapshot: BarcodeSnapshot)
    /** Applies the currently loaded favorite state atomically without deleting unloaded pages. */
    suspend fun applyFavoritesMutation(snapshot: BarcodeSnapshot)
    suspend fun saveItems(items: List<CodeItem>)
    suspend fun upsertItems(items: List<CodeItem>)
    suspend fun loadItemsByIds(ids: List<Long>): List<CodeItem>
    suspend fun searchFavoriteItems(query: String, limit: Int, cursor: FavoriteSearchItemCursor?): List<CodeItem>
    suspend fun clearFavoriteFlags(ids: List<Long>)
    suspend fun clearFavoriteFlagsForGroups(groupIds: List<Long>)
    suspend fun clearAllFavoriteFlags()
    suspend fun saveFavoriteGroups(groups: List<FavoriteGroup>, links: List<FavoriteGroupItem>)
    suspend fun deleteFavoriteGroups(ids: List<Long>)
    suspend fun clearAllFavoriteGroups()
    suspend fun renameFavoriteFolder(path: String, renamedPath: String)
    suspend fun deleteFavoriteFolder(path: String)
    suspend fun saveFavoriteFolders(folders: List<String>)

    suspend fun loadItems(): List<CodeItem>
    suspend fun loadGroups(): List<FavoriteGroup>
    suspend fun loadGroupItems(): List<FavoriteGroupItem>
    suspend fun loadGroupItemIds(groupId: Long): List<Long>
    suspend fun loadFavoriteGroupPage(limit: Int, cursor: FavoriteGroupPageCursor?): List<FavoriteGroup>
    suspend fun loadFavoriteGroupsByIds(ids: List<Long>): List<FavoriteGroup>
    suspend fun searchFavoriteGroups(query: String, limit: Int, cursor: FavoriteSearchGroupCursor?): List<FavoriteGroup>
    suspend fun loadFolders(): List<String>
    suspend fun loadSnapshot(): BarcodeSnapshot
    suspend fun loadStartupSnapshot(): StartupBarcodeSnapshot
    suspend fun appendSnapshot(snapshot: BarcodeSnapshot)

    suspend fun migrateLegacyDataIfNeeded(legacy: LegacyBarcodeData)
}
