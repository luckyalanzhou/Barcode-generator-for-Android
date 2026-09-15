package com.luckyalanzhou.barcodegenerator

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import android.text.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import org.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.*
import kotlin.math.roundToInt

internal fun MainActivity.createFavoritesExport() {
    AlertDialog.Builder(this)
        .setTitle("导出收藏")
        .setItems(arrayOf("分享到其他应用", "保存到文件")) { _, which ->
            if (which == 0) shareFavoritesExport() else createFavoritesDocumentExport()
        }
        .create()
        .also { showIos26Dialog(it) }
}

/** 生成临时 ZIP 备份并交给系统分享面板，可发送至聊天、邮件、网盘或文件管理器。 */
private fun MainActivity.shareFavoritesExport() {
    val name = "barcode-generator-backup-android.zip"
    lifecycleScope.launch(Dispatchers.IO) {
        val exportFile = File(cacheDir, name)
        val exportUri = FileProvider.getUriForFile(this@shareFavoritesExport, "$packageName.fileprovider", exportFile)
        val result = runCatching { FavoritesTransferManager.export(contentResolver, exportUri, dao.loadGroups(), dao.loadGroupItems(), dao.loadItems(), dao.loadFolders()) }
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

/** 保留原有的 SAF 文件保存入口，供需要指定位置时使用。 */
private fun MainActivity.createFavoritesDocumentExport() {
    val name = "barcode-generator-backup-android.zip"
    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/zip"; putExtra(Intent.EXTRA_TITLE, name); addCategory(Intent.CATEGORY_OPENABLE) }, MainActivity.REQUEST_FAVORITES_EXPORT)
}

internal fun MainActivity.restoreFavoritesImport() {
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, MainActivity.REQUEST_FAVORITES_IMPORT)
}

internal fun MainActivity.exportFavorites(uri: Uri) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching { FavoritesTransferManager.export(contentResolver, uri, dao.loadGroups(), dao.loadGroupItems(), dao.loadItems(), dao.loadFolders()) }
        withContext(Dispatchers.Main) { result.onSuccess { toast("收藏备份已导出") }.onFailure { toast("收藏导出失败：${it.message ?: "无法写入文件"}") } }
    }
}

internal fun MainActivity.confirmImportFavorites(uri: Uri) {
    val activity = this
    lifecycleScope.launch(Dispatchers.IO) {
        val parsed = runCatching { FavoritesTransferManager.restore(contentResolver, uri) }
        withContext(Dispatchers.Main) {
            parsed.onFailure { toast("无法导入收藏：${it.message ?: "文件格式无效"}") }.onSuccess { backup ->
                AlertDialog.Builder(activity).setTitle("导入跨平台收藏？").setMessage("将合并 ${backup.favorites.size} 个收藏，并保留一级文件夹、二级文件夹和收藏文件名。不会删除当前数据。")
                    .setNegativeButton("取消", null).setPositiveButton("导入") { _, _ -> importFavorites(backup) }.create().also { showIos26Dialog(it) }
            }
        }
    }
}

private fun MainActivity.importFavorites(backup: InterchangeBackup) {
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runCatching {
            databaseMutex.withLock {
                val counts = database.withTransaction {
                    val transfer = FavoritesTransferManager.appendEntities(backup, dao.loadItems(), dao.loadGroups(), dao.loadGroupItems())
                    dao.saveItems(transfer.items); dao.saveGroups(transfer.groups); dao.saveGroupItems(transfer.links); dao.saveFolders(transfer.folders)
                    transfer.items.size to transfer.groups.size
                }
                loadItemsOnIo(); loadFavoriteGroupsOnIo(); loadFavoriteFoldersOnIo()
                collapsedFavoriteFolders.addAll(backup.folders.filter { it.isNotBlank() })
                collapsedFavoriteFolders.addAll(backup.favorites.map { it.folder }.filter { it.isNotBlank() })
                counts
            }
        }
        withContext(Dispatchers.Main) { result.onSuccess { (itemCount, groupCount) -> page = "favorites"; render(); toast("已导入 $groupCount 个收藏，$itemCount 条码") }.onFailure { toast("收藏导入失败：${it.message ?: "无法写入数据"}") } }
    }
}

