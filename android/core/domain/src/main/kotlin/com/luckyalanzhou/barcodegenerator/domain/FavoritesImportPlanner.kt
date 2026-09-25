package com.luckyalanzhou.barcodegenerator.domain

/** Pure conflict and deduplication policy for importing favorite files. */
class FavoritesImportPlanner {
    data class Plan(
        val conflictingFileKeys: List<String>,
        val replacedGroupIds: Set<Long>,
        val favoritesToImport: List<InterchangeFavorite>,
    )

    fun inspectConflicts(
        existingGroups: List<FavoriteGroup>,
        incomingFavorites: List<InterchangeFavorite>,
    ): FavoritesImportConflictSummary {
        val existingFileKeys = existingGroups.map { fileKey(it.folder, it.name) }.toSet()
        val incomingKeys = incomingFavorites.map { fileKey(it.folder, it.name) }
        val existingConflicts = incomingKeys
            .filter { it in existingFileKeys }
            .distinct()
        val duplicateBackupKeys = incomingKeys.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys.toList()
        val allConflicts = existingConflicts + duplicateBackupKeys.filterNot { it in existingConflicts }
        return FavoritesImportConflictSummary(allConflicts, duplicateBackupKeys)
    }

    fun plan(
        existingGroups: List<FavoriteGroup>,
        incomingFavorites: List<InterchangeFavorite>,
        overwriteConflicts: Boolean,
    ): Plan {
        val existingFileKeys = existingGroups.map { fileKey(it.folder, it.name) }.toSet()
        val incomingKeys = incomingFavorites.map { fileKey(it.folder, it.name) }
        val incomingFileKeys = incomingKeys.toSet()
        val duplicateBackupKeys = incomingKeys.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys
        val conflictingGroups = existingGroups.filter { fileKey(it.folder, it.name) in incomingFileKeys }
        val replacedGroupIds = if (overwriteConflicts) conflictingGroups.map { it.id }.toSet() else emptySet()
        val deduplicatedFavorites = incomingFavorites.asReversed()
            .distinctBy { fileKey(it.folder, it.name) }
            .asReversed()
        val favoritesToImport = if (overwriteConflicts) deduplicatedFavorites else deduplicatedFavorites.filterNot {
            val key = fileKey(it.folder, it.name)
            key in existingFileKeys || key in duplicateBackupKeys
        }

        return Plan(
            conflictingFileKeys = (incomingKeys.filter { it in existingFileKeys } + duplicateBackupKeys).distinct(),
            replacedGroupIds = replacedGroupIds,
            favoritesToImport = favoritesToImport,
        )
    }

    private fun fileKey(folder: String, name: String): String =
        FavoriteFileIdentity.of(folder, name).let { "${it.folder}\u0000${it.name}" }
}
