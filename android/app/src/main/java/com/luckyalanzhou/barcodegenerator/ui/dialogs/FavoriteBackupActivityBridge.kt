package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.MAX_FAVORITES_BACKUP_INPUT_BYTES
import com.luckyalanzhou.barcodegenerator.ui.app.composeAppShellActions
import com.luckyalanzhou.barcodegenerator.ui.dialogs.confirmImportFavoritesCompose
import com.luckyalanzhou.barcodegenerator.ui.app.showComposeDialog
import com.luckyalanzhou.barcodegenerator.ui.app.toast

import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收藏备份的业务/系统文件选择桥接。
 *
 * 备份选择和导入确认界面由 ComposeBackupDialogs.kt 绘制；
 * SAF、ZIP 读写和数据库事务仍沿用原实现。
 */
/** 生成 ZIP 后交给系统分享面板，可发送至聊天、邮件、网盘或文件管理器。 */
internal fun MainActivity.shareFavoritesExportForCompose() {
    val name = timestampedBackupFileName()
    lifecycleScope.launch(Dispatchers.IO) {
        val exportFile = File(cacheDir, name)
        val exportUri = FileProvider.getUriForFile(this@shareFavoritesExportForCompose, "$packageName.fileprovider", exportFile)
        val result = runCatching {
            val bytes = favoritesViewModel.exportFavorites()
            contentResolver.openOutputStream(exportUri)?.use { it.write(bytes) }
                ?: error("无法创建备份文件")
        }
        withContext(Dispatchers.Main) {
            result.onSuccess {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, exportUri)
                    putExtra(Intent.EXTRA_TITLE, name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("收藏备份", exportUri)
                }
                startActivity(Intent.createChooser(share, "导出收藏到"))
            }.onFailure { toast(formatFavoritesExportError(it)) }
        }
    }
}

/** 保留 SAF 文件保存入口，供用户指定 ZIP 保存位置。 */
internal fun MainActivity.createFavoritesDocumentExportForCompose() {
    val name = timestampedBackupFileName()
    launchExternalActivity(
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, name)
            addCategory(Intent.CATEGORY_OPENABLE)
        },
        MainActivity.REQUEST_FAVORITES_EXPORT,
    )
}

private fun timestampedBackupFileName(): String {
    val baseName = BuildConfig.BACKUP_FILE_NAME.removeSuffix(".zip")
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    return "$baseName-$timestamp.zip"
}

internal fun MainActivity.restoreFavoritesImport() {
    launchExternalActivity(
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/zip"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        MainActivity.REQUEST_FAVORITES_IMPORT,
    )
}

internal fun MainActivity.exportFavorites(uri: Uri) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
            val bytes = favoritesViewModel.exportFavorites()
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("无法写入备份文件")
        }
        withContext(Dispatchers.Main) {
            result
                .onSuccess { toast("收藏备份已导出") }
                .onFailure { toast(formatFavoritesExportError(it)) }
        }
    }
}

private fun formatFavoritesExportError(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        message.contains("没有有效内容") || message.contains("空内容") ->
            "收藏导出失败：存在没有条码内容的收藏文件，请补充内容后重试"
        message.contains("ZIP") || message.contains("备份") || message.contains("JSON") ->
            "收藏导出失败：导出的 ZIP 结构或内容校验不通过，请重试"
        else -> "收藏导出失败：${message.ifBlank { "无法生成有效备份" }}"
    }
}

internal fun MainActivity.confirmImportFavorites(uri: Uri) {
    lifecycleScope.launch(Dispatchers.IO) {
        val parsed = runCatching {
            val declaredSize = runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull() ?: -1L
            require(declaredSize < 0L || declaredSize <= MAX_FAVORITES_BACKUP_INPUT_BYTES.toLong()) {
                "备份文件超过 64 MB 限制"
            }
            val bytes = contentResolver.openInputStream(uri)?.use { input -> readFavoritesBackupBounded(input) }
                ?: error("无法读取备份文件")
            favoritesViewModel.restoreFavorites(bytes)
        }
        withContext(Dispatchers.Main) {
            parsed
                .onFailure { toast("无法导入收藏：${it.message ?: "文件格式无效"}") }
                .onSuccess { backup -> confirmImportFavoritesCompose(uri, backup) }
        }
    }
}

internal fun MainActivity.importFavoritesForCompose(backup: InterchangeBackup, overwriteConflicts: Boolean = false) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
            val counts = favoritesViewModel.importFavorites(backup, overwriteConflicts)
            favoritesViewModel.addCollapsed(
                (backup.folders + backup.favorites.map { it.folder }).filter { it.isNotBlank() }.toSet(),
            )
            counts
        }
        withContext(Dispatchers.Main) {
            result
                .onSuccess { (itemCount, groupCount) ->
                    composeAppShellActions().navigateTo(AppRoute.Favorites)
                    toast("已导入 $groupCount 个收藏，$itemCount 条码")
                }
                .onFailure { toast("收藏导入失败：${it.message ?: "无法写入数据"}") }
        }
    }
}
