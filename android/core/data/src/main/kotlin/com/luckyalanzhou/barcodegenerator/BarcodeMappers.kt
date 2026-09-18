package com.luckyalanzhou.barcodegenerator

/** Data 层唯一的 Entity/Domain 转换边界。 */
internal fun CodeItemEntity.toDomain(): CodeItem =
    CodeItem(id, text, format, createdAt, favorite, folder, inHistory)

internal fun CodeItem.toEntity(): CodeItemEntity =
    CodeItemEntity(id, text, format, createdAt, favorite, folder, inHistory)

internal fun BarcodeSnapshot.toTransferEntities(): TransferEntities = TransferEntities(
    items = items.map(CodeItem::toEntity),
    groups = groups.map { FavoriteGroupEntity(it.id, it.folder, it.name, it.savedAt) },
    links = links.map { FavoriteGroupItemEntity(it.groupId, it.itemId) },
    folders = folders.map(::FavoriteFolderEntity),
)

internal fun TransferEntities.toSnapshot(): BarcodeSnapshot {
    val links = links.map { FavoriteGroupItem(it.groupId, it.itemId) }
    val linksByGroup = links.groupBy(FavoriteGroupItem::groupId)
    return BarcodeSnapshot(
        items = items.map(CodeItemEntity::toDomain),
        groups = groups.map {
            FavoriteGroup(
                id = it.id,
                folder = it.folder,
                name = it.name,
                savedAt = it.savedAt,
                itemIds = linksByGroup[it.id].orEmpty().map(FavoriteGroupItem::itemId).toMutableList(),
            )
        },
        links = links,
        folders = folders.map(FavoriteFolderEntity::name),
    )
}
