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

/** 从旧版 SharedPreferences 提取出的纯 Kotlin 迁移输入。 */
data class LegacyBarcodeData(
    val itemsJson: String?,
    val groupsJson: String?,
    val folders: Set<String>,
)

/** Domain 定义的持久化端口；具体存储实现由 Data 层提供。 */
interface BarcodeRepository {
    suspend fun saveAll(snapshot: BarcodeSnapshot)
    suspend fun saveItems(items: List<CodeItem>)
    suspend fun saveFavoriteGroups(groups: List<FavoriteGroup>, links: List<FavoriteGroupItem>)
    suspend fun saveFavoriteFolders(folders: List<String>)

    suspend fun loadItems(): List<CodeItem>
    suspend fun loadGroups(): List<FavoriteGroup>
    suspend fun loadGroupItems(): List<FavoriteGroupItem>
    suspend fun loadFolders(): List<String>
    suspend fun loadSnapshot(): BarcodeSnapshot
    suspend fun appendSnapshot(snapshot: BarcodeSnapshot)

    suspend fun migrateLegacyDataIfNeeded(legacy: LegacyBarcodeData)
}
