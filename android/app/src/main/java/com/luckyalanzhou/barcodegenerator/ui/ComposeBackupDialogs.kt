package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.ui.dialogs.createFavoritesDocumentExportForCompose
import com.luckyalanzhou.barcodegenerator.ui.dialogs.importFavoritesForCompose
import com.luckyalanzhou.barcodegenerator.ui.dialogs.shareFavoritesExportForCompose

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
    showComposeDialog(compact = true, metricsLabel = null) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text("导出收藏", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            ComposeDialogChoice("分享到其他应用", dark) {
                dismiss()
                shareFavoritesExportForCompose()
            }
            ComposeDialogChoice("保存到文件", dark) {
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
        val conflicts = viewModel.inspectFavoriteImport(backup)
        withContext(Dispatchers.Main) {
            if (conflicts.hasConflicts) {
                showFavoriteImportConflictDialog(backup, conflicts)
            } else {
                showComposeConfirmDialog(
                    title = "导入跨平台收藏？",
                    message = "将导入 ${backup.favorites.size} 个收藏，并保留一级文件夹、二级文件夹和收藏文件名。",
                    positive = "导入",
                ) { importFavoritesForCompose(backup, overwriteConflicts = false) }
            }
        }
    }
}

private fun MainActivity.showFavoriteImportConflictDialog(
    backup: InterchangeBackup,
    conflicts: FavoritesImportConflictSummary,
) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        val colors = LocalBarcodeThemeColors.current
        ComposeGlassDialogCard(dark) {
            Text("发现同名内容", color = colors.primary, fontSize = 18.sp)
            Text(
                "发现 ${conflicts.fileKeys.size} 个同路径同名收藏文件。请选择如何处理这些文件；同名文件夹下的其他文件仍会直接导入。",
                color = colors.secondary,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction("取消", dark, dismiss)
                DialogAction(
                    "跳过冲突",
                    dark,
                    { importFavoritesForCompose(backup, overwriteConflicts = false); dismiss() },
                    Modifier.padding(start = 8.dp),
                )
                DialogAction(
                    "覆盖导入",
                    dark,
                    { importFavoritesForCompose(backup, overwriteConflicts = true); dismiss() },
                    Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
