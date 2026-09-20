package com.luckyalanzhou.barcodegenerator.domain

data class InterchangeFavorite(
    val id: String?,
    val name: String,
    val rootFolder: String,
    val subFolder: String,
    val type: String,
    val time: Long,
    val texts: List<String>,
) {
    val folder: String get() = listOf(rootFolder, subFolder).filter { it.isNotBlank() }.joinToString("/")
}

data class InterchangeBackup(val favorites: List<InterchangeFavorite>, val folders: List<String>)

data class FavoritesImportConflictSummary(
    val fileKeys: List<String>,
) {
    val hasConflicts: Boolean get() = fileKeys.isNotEmpty()
}
