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
    ): List<String> {
        val existingFileKeys = existingGroups.map { fileKey(it.folder, it.name) }.toSet()
        return incomingFavorites.map { fileKey(it.folder, it.name) }
            .filter { it in existingFileKeys }
            .distinct()
    }

    fun plan(
        existingGroups: List<FavoriteGroup>,
        incomingFavorites: List<InterchangeFavorite>,
        overwriteConflicts: Boolean,
    ): Plan {
        val existingFileKeys = existingGroups.map { fileKey(it.folder, it.name) }.toSet()
        val incomingFileKeys = incomingFavorites.map { fileKey(it.folder, it.name) }.toSet()
        val conflictingGroups = existingGroups.filter { fileKey(it.folder, it.name) in incomingFileKeys }
        val replacedGroupIds = if (overwriteConflicts) conflictingGroups.map { it.id }.toSet() else emptySet()
        val deduplicatedFavorites = incomingFavorites.asReversed()
            .distinctBy { fileKey(it.folder, it.name) }
            .asReversed()
        val favoritesToImport = if (overwriteConflicts) deduplicatedFavorites else {
            deduplicatedFavorites.filterNot { fileKey(it.folder, it.name) in existingFileKeys }
        }

        return Plan(
            conflictingFileKeys = incomingFavorites.map { fileKey(it.folder, it.name) }
                .filter { it in existingFileKeys }
                .distinct(),
            replacedGroupIds = replacedGroupIds,
            favoritesToImport = favoritesToImport,
        )
    }

    private fun fileKey(folder: String, name: String): String =
        "${folder.trim().trim('/').let { if (it == "默认") "" else it }}\u0000${name.trim()}"
}
