package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    showComposeConfirmDialog(
        title = "导入跨平台收藏？",
        message = "将合并 ${backup.favorites.size} 个收藏，并保留一级文件夹、二级文件夹和收藏文件名。不会删除当前数据。",
        positive = "导入",
    ) { importFavoritesForCompose(backup) }
}
