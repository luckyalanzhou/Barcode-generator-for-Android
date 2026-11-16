package com.luckyalanzhou.barcodegenerator.ui.app.platform

import android.content.res.Resources
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.domain.FavoriteBackupFailure
import com.luckyalanzhou.barcodegenerator.domain.FavoriteBackupIssue
import com.luckyalanzhou.barcodegenerator.domain.MAX_FAVORITES_BACKUP_ENTRIES

internal fun Resources.favoriteBackupFailureMessage(
    error: Throwable,
    exporting: Boolean = false,
    reading: Boolean = false,
): String {
    val issue = (error as? FavoriteBackupFailure)?.issue
    if (issue == null) {
        val fallback = when {
            exporting -> R.string.backup_export_failed
            reading -> R.string.backup_invalid_file
            else -> R.string.backup_import_failed
        }
        return getString(fallback)
    }
    if (issue == FavoriteBackupIssue.EmptyFavorite && exporting) {
        return getString(R.string.backup_export_empty)
    }
    val detail = when (issue) {
        FavoriteBackupIssue.InvalidJson -> getString(R.string.backup_json_invalid)
        FavoriteBackupIssue.EmptyFavorite -> getString(R.string.backup_favorite_empty)
        FavoriteBackupIssue.MissingName -> getString(R.string.backup_favorite_missing_name)
        FavoriteBackupIssue.TooManyEntries -> getString(R.string.backup_entry_limit, MAX_FAVORITES_BACKUP_ENTRIES)
        FavoriteBackupIssue.PathTooLong -> getString(R.string.backup_entry_path_too_long)
        FavoriteBackupIssue.FavoriteTooLarge -> getString(R.string.backup_entry_size_limit)
        FavoriteBackupIssue.UncompressedTooLarge -> getString(R.string.backup_uncompressed_size_limit)
        FavoriteBackupIssue.ArchiveTooLarge -> getString(R.string.backup_import_size_limit)
        FavoriteBackupIssue.ZipRequired -> getString(R.string.backup_zip_required)
        FavoriteBackupIssue.NoFavoritesRoot -> getString(R.string.backup_favorites_missing)
        FavoriteBackupIssue.MismatchedFormats -> getString(R.string.backup_format_count_mismatch)
        FavoriteBackupIssue.InvalidFolder -> getString(R.string.backup_folder_invalid)
        FavoriteBackupIssue.FolderTooDeep -> getString(R.string.backup_folder_depth_exceeded)
        FavoriteBackupIssue.StorageLimit -> getString(R.string.backup_storage_limit)
        FavoriteBackupIssue.UnsupportedFormat -> getString(R.string.barcode_format_unsupported)
        FavoriteBackupIssue.Unreadable -> getString(R.string.backup_read_failed)
    }
    return getString(
        if (exporting) R.string.backup_export_failure_detail else R.string.backup_import_failure_detail,
        detail,
    )
}
