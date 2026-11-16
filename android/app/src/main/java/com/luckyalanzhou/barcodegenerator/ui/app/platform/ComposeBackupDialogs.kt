package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeGlassDialogCard
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 收藏导出方式选择，继续调用原有系统分享和 SAF 文件保存入口。 */
internal fun MainActivity.createFavoritesExportCompose() {
    showComposeDialog(compact = true) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(getString(R.string.favorite_export), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(getString(R.string.favorite_backup_export_help), color = LocalAppColorScheme.current.text.secondary,
                fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            ComposeDialogChoice(getString(R.string.backup_share), dark) {
                dismiss()
                shareFavoritesExportForCompose()
            }
            ComposeDialogChoice(getString(R.string.backup_save), dark) {
                dismiss()
                createFavoritesDocumentExportForCompose()
            }
        }
    }
}

@Composable
private fun ComposeDialogChoice(text: String, dark: Boolean, onClick: () -> Unit) {
    DialogAction(
        text = text,
        dark = dark,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

internal fun MainActivity.confirmImportFavoritesCompose(uri: android.net.Uri, backup: InterchangeBackup) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = captureRecoverableFavoriteBackupOperation {
            favoritesViewModel.inspectFavoriteImport(backup)
        }
        withContext(Dispatchers.Main) {
            result
                .onSuccess { conflicts ->
                    if (conflicts.hasConflicts) {
                        showFavoriteImportConflictDialog(backup, conflicts)
                    } else {
                        showComposeConfirmDialog(
                            title = getString(R.string.favorite_import_platform_title),
                            message = resources.getQuantityString(R.plurals.favorite_import_platform_detail, backup.favorites.size, backup.favorites.size),
                            positive = getString(R.string.action_import),
                        ) { importFavoritesForCompose(backup, overwriteConflicts = false) }
                    }
                }
                .onFailure { error ->
                    DebugLog.actionFailed("favorites_backup_conflict_inspection", error)
                    toast(getString(R.string.favorite_conflict_check_failed))
                }
        }
    }
}

private fun MainActivity.showFavoriteImportConflictDialog(
    backup: InterchangeBackup,
    conflicts: FavoritesImportConflictSummary,
) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        val colors = LocalAppColorScheme.current
        ComposeGlassDialogCard(dark) {
            Text(getString(R.string.favorite_conflicts), color = colors.text.primary, fontSize = 18.sp)
            Text(
                buildString {
                    append(resources.getQuantityString(R.plurals.favorite_conflicts_detail, conflicts.fileKeys.size, conflicts.fileKeys.size))
                    if (conflicts.duplicateBackupKeys.isNotEmpty()) {
                        append(resources.getQuantityString(R.plurals.favorite_internal_conflicts_detail, conflicts.duplicateBackupKeys.size, conflicts.duplicateBackupKeys.size))
                    }
                },
                color = colors.text.secondary,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction(getString(R.string.action_cancel), dark, dismiss)
                DialogAction(
                    getString(R.string.favorite_skip_conflicts),
                    dark,
                    { importFavoritesForCompose(backup, overwriteConflicts = false); dismiss() },
                    Modifier.padding(start = 20.dp),
                )
                DialogAction(
                    getString(R.string.favorite_import_overwrite),
                    dark,
                    { importFavoritesForCompose(backup, overwriteConflicts = true); dismiss() },
                    Modifier.padding(start = 20.dp),
                )
            }
        }
    }
}
