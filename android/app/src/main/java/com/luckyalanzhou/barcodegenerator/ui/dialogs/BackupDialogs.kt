package com.luckyalanzhou.barcodegenerator

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 收藏备份的业务/系统文件选择桥接。
 *
 * 备份选择和导入确认界面由 ComposeBackupDialogs.kt 绘制；
 * SAF、ZIP 读写和数据库事务仍沿用原实现。
 */
internal fun MainActivity.createFavoritesExport() {
    createFavoritesExportCompose()
}

/** 生成 ZIP 后交给系统分享面板，可发送至聊天、邮件、网盘或文件管理器。 */
internal fun MainActivity.shareFavoritesExportForCompose() {
    val name = "barcode-generator-backup-android.zip"
    lifecycleScope.launch(Dispatchers.IO) {
        val exportFile = File(cacheDir, name)
        val exportUri = FileProvider.getUriForFile(this@shareFavoritesExportForCompose, "$packageName.fileprovider", exportFile)
        val result = runCatching {
            FavoritesTransferManager.export(
                contentResolver,
                exportUri,
                dao.loadGroups(),
                dao.loadGroupItems(),
                dao.loadItems(),
                dao.loadFolders(),
            )
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
            }.onFailure { toast("收藏导出失败：${it.message ?: "无法生成备份"}") }
        }
    }
}

/** 保留 SAF 文件保存入口，供用户指定 ZIP 保存位置。 */
internal fun MainActivity.createFavoritesDocumentExportForCompose() {
    val name = "barcode-generator-backup-android.zip"
    startActivityForResult(
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, name)
            addCategory(Intent.CATEGORY_OPENABLE)
        },
        MainActivity.REQUEST_FAVORITES_EXPORT,
    )
}

internal fun MainActivity.restoreFavoritesImport() {
    startActivityForResult(
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
            FavoritesTransferManager.export(
                contentResolver,
                uri,
                dao.loadGroups(),
                dao.loadGroupItems(),
                dao.loadItems(),
                dao.loadFolders(),
            )
        }
        withContext(Dispatchers.Main) {
            result
                .onSuccess { toast("收藏备份已导出") }
                .onFailure { toast("收藏导出失败：${it.message ?: "无法写入文件"}") }
        }
    }
}

internal fun MainActivity.confirmImportFavorites(uri: Uri) {
    lifecycleScope.launch(Dispatchers.IO) {
        val parsed = runCatching { FavoritesTransferManager.restore(contentResolver, uri) }
        withContext(Dispatchers.Main) {
            parsed
                .onFailure { toast("无法导入收藏：${it.message ?: "文件格式无效"}") }
                .onSuccess { backup -> confirmImportFavoritesCompose(uri, backup) }
        }
    }
}

internal fun MainActivity.importFavoritesForCompose(backup: InterchangeBackup) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
            databaseMutex.withLock {
                val counts = database.withTransaction {
                    val transfer = FavoritesTransferManager.appendEntities(
                        backup,
                        dao.loadItems(),
                        dao.loadGroups(),
                        dao.loadGroupItems(),
                    )
                    dao.saveItems(transfer.items)
                    dao.saveGroups(transfer.groups)
                    dao.saveGroupItems(transfer.links)
                    dao.saveFolders(transfer.folders)
                    transfer.items.size to transfer.groups.size
                }
                loadItemsOnIo()
                loadFavoriteGroupsOnIo()
                loadFavoriteFoldersOnIo()
                collapsedFavoriteFolders.addAll(backup.folders.filter { it.isNotBlank() })
                collapsedFavoriteFolders.addAll(backup.favorites.map { it.folder }.filter { it.isNotBlank() })
                counts
            }
        }
        withContext(Dispatchers.Main) {
            result
                .onSuccess { (itemCount, groupCount) ->
                    page = "favorites"
                    render()
                    toast("已导入 $groupCount 个收藏，$itemCount 条码")
                }
                .onFailure { toast("收藏导入失败：${it.message ?: "无法写入数据"}") }
        }
    }
}
