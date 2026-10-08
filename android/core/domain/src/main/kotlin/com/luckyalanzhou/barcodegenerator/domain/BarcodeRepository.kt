package com.luckyalanzhou.barcodegenerator.domain

/** 收藏分组与条码之间的持久化关联。 */
data class FavoriteGroupItem(
    val groupId: Long,
    val itemId: Long,
    /** 条码在收藏文件中的从零开始的固定顺序。 */
    val position: Int,
)

/** 单次读取得到的收藏文件及其全部关联条码，避免分步查询期间数据变化造成内容不一致。 */
data class FavoriteGroupContent(
    val group: FavoriteGroup,
    val items: List<CodeItem>,
    /** 无法读取或条码内容为空的关联条目 ID；上层据此阻止打开不完整文件。 */
    val invalidItemIds: List<Long>,
)

/** 持久化边界使用的领域快照，不暴露 Room Entity。 */
data class BarcodeSnapshot(
    val items: List<CodeItem>,
    val groups: List<FavoriteGroup>,
    val links: List<FavoriteGroupItem>,
    val folders: List<String>,
    /** 分页加载时，仅这些分组的内存关联列表是完整权威数据，可安全替换其关联记录。 */
    val replaceGroupLinkIds: Set<Long> = emptySet(),
)

/** 收藏文件分页游标，以保存时间和 ID 定位，避免数据变更时 OFFSET 分页漂移。 */
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
    /** 不受界面分页影响的全部收藏文件身份，用于全局重名检测。 */
    val identityGroups: List<FavoriteGroup> = groups,
)

/** 从旧版 SharedPreferences 提取出的纯 Kotlin 迁移输入。 */
data class LegacyBarcodeData(
    val itemsJson: String?,
    val groupsJson: String?,
    val folders: Set<String>,
)

/**
 * 领域层定义的持久化边界；Data 层提供 Room 实现。
 * 插入、分页、搜索与收藏事务的约束在此声明，UI 不直接访问 DAO 或数据库实体。
 */
interface BarcodeRepository {
    suspend fun saveAll(snapshot: BarcodeSnapshot)
    /** 原子应用当前已加载的收藏状态；不得因分页未加载而删除数据库中的其他收藏页。 */
    suspend fun applyFavoritesMutation(snapshot: BarcodeSnapshot)
    suspend fun saveItems(items: List<CodeItem>)
    suspend fun upsertItems(items: List<CodeItem>)
    /** 根据全部已持久化条目分配新 ID 并插入，不覆盖任何已有条码。 */
    suspend fun insertGeneratedItems(items: List<CodeItem>): List<CodeItem>
    suspend fun deleteItem(itemId: Long, modifiedAt: Long)
    suspend fun updateFavoriteGroupMetadata(groupId: Long, name: String, folder: String, savedAt: Long)
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
    suspend fun loadFavoriteGroupContent(groupId: Long): FavoriteGroupContent?
    suspend fun loadFavoriteGroupPage(limit: Int, cursor: FavoriteGroupPageCursor?): List<FavoriteGroup>
    suspend fun loadFavoriteGroupsByIds(ids: List<Long>): List<FavoriteGroup>
    suspend fun searchFavoriteGroups(query: String, limit: Int, cursor: FavoriteSearchGroupCursor?): List<FavoriteGroup>
    suspend fun loadFolders(): List<String>
    suspend fun loadSnapshot(): BarcodeSnapshot
    suspend fun loadStartupSnapshot(): StartupBarcodeSnapshot
    suspend fun appendSnapshot(snapshot: BarcodeSnapshot)
    /** 在同一事务中替换冲突收藏文件并写入导入内容，失败时由数据库回滚。 */
    suspend fun commitFavoriteImport(snapshot: BarcodeSnapshot, replacedGroupIds: Set<Long>)

    suspend fun migrateLegacyDataIfNeeded(legacy: LegacyBarcodeData)
}
