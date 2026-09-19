package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.*

/** Repository 与备份传输逻辑之间共享的数据库实体快照。 */
data class TransferEntities(
    val items: List<CodeItemEntity>,
    val groups: List<FavoriteGroupEntity>,
    val links: List<FavoriteGroupItemEntity>,
    val folders: List<FavoriteFolderEntity>
)

