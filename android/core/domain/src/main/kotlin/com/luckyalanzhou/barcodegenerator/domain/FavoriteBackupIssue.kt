package com.luckyalanzhou.barcodegenerator.domain

enum class FavoriteBackupIssue {
    InvalidJson,
    EmptyFavorite,
    MissingName,
    TooManyEntries,
    PathTooLong,
    FavoriteTooLarge,
    UncompressedTooLarge,
    ArchiveTooLarge,
    ZipRequired,
    NoFavoritesRoot,
    MismatchedFormats,
    InvalidFolder,
    FolderTooDeep,
    StorageLimit,
    UnsupportedFormat,
    Unreadable,
}

interface FavoriteBackupFailure {
    val issue: FavoriteBackupIssue
}

class FavoriteBackupInputException(
    override val issue: FavoriteBackupIssue,
    diagnostic: String,
) : IllegalArgumentException(diagnostic), FavoriteBackupFailure

class FavoriteBackupContentException(
    override val issue: FavoriteBackupIssue,
    diagnostic: String,
) : IllegalStateException(diagnostic), FavoriteBackupFailure
