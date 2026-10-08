package com.luckyalanzhou.barcodegenerator.domain

data class InterchangeFavorite(
    val id: String?,
    val name: String,
    val rootFolder: String,
    val subFolder: String,
    val type: String,
    val time: Long,
    val texts: List<String>,
    /** Optional aligned per-item types; old backups fall back to the file-wide type. */
    val formats: List<String> = emptyList(),
) {
    val folder: String get() = listOf(rootFolder, subFolder).filter { it.isNotBlank() }.joinToString("/")
}

data class InterchangeBackup(val favorites: List<InterchangeFavorite>, val folders: List<String>)

data class FavoritesImportConflictSummary(
    val fileKeys: List<String>,
    val duplicateBackupKeys: List<String> = emptyList(),
) {
    val hasConflicts: Boolean get() = fileKeys.isNotEmpty()
}
