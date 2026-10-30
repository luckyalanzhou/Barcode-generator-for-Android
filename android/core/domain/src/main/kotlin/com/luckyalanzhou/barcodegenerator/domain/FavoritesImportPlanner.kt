package com.luckyalanzhou.barcodegenerator.domain

/**
 * 收藏导入的纯规则规划器：检测现存重名和备份内重复，并按覆盖策略计算待替换与待导入文件。
 * 不执行数据库写入，便于在真正提交事务前向用户展示冲突并单独测试规则。
 */
class FavoritesImportPlanner {
    data class Plan(
        val conflictingFileKeys: List<String>,
        val replacedGroupIds: Set<Long>,
        val favoritesToImport: List<InterchangeFavorite>,
    )

    /** 汇总与现有收藏重名、以及备份自身重复的文件键，供确认界面展示。 */
    fun inspectConflicts(
        existingGroups: List<FavoriteGroup>,
        incomingFavorites: List<InterchangeFavorite>,
    ): FavoritesImportConflictSummary {
        val existingFileKeys = existingGroups.map { identity(it.folder, it.name) }.toSet()
        val incomingKeys = incomingFavorites.map { identity(it.folder, it.name) }
        val existingConflicts = incomingKeys
            .filter { it in existingFileKeys }
            .distinct()
        val duplicateBackupKeys = incomingKeys.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys.toList()
        val allConflicts = existingConflicts + duplicateBackupKeys.filterNot { it in existingConflicts }
        return FavoritesImportConflictSummary(allConflicts.map(::displayKey), duplicateBackupKeys.map(::displayKey))
    }

    /** 根据是否覆盖冲突生成最终导入清单，同时给出需要替换的既有收藏 ID。 */
    fun plan(
        existingGroups: List<FavoriteGroup>,
        incomingFavorites: List<InterchangeFavorite>,
        overwriteConflicts: Boolean,
    ): Plan {
        val existingFileKeys = existingGroups.map { identity(it.folder, it.name) }.toSet()
        val incomingKeys = incomingFavorites.map { identity(it.folder, it.name) }
        val incomingFileKeys = incomingKeys.toSet()
        val duplicateBackupKeys = incomingKeys.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys
        val conflictingGroups = existingGroups.filter { identity(it.folder, it.name) in incomingFileKeys }
        val replacedGroupIds = if (overwriteConflicts) conflictingGroups.map { it.id }.toSet() else emptySet()
        val deduplicatedFavorites = incomingFavorites.asReversed()
            .distinctBy { identity(it.folder, it.name) }
            .asReversed()
        val favoritesToImport = if (overwriteConflicts) deduplicatedFavorites else deduplicatedFavorites.filterNot {
            val key = identity(it.folder, it.name)
            key in existingFileKeys || key in duplicateBackupKeys
        }

        return Plan(
            conflictingFileKeys = (incomingKeys.filter { it in existingFileKeys } + duplicateBackupKeys)
                .distinct().map(::displayKey),
            replacedGroupIds = replacedGroupIds,
            favoritesToImport = favoritesToImport,
        )
    }

    private fun identity(folder: String, name: String): FavoriteFileIdentity = FavoriteFileIdentity.of(folder, name)

    /** 展示字段保留原格式；身份比较始终使用字段元组，避免分隔符碰撞。 */
    private fun displayKey(identity: FavoriteFileIdentity): String = "${identity.folder}\u0000${identity.name}"
}
