package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.MAX_FAVORITES_BACKUP_INPUT_BYTES
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesImportRefreshException
import com.luckyalanzhou.barcodegenerator.ui.dialogs.readFavoritesBackupBounded
import com.luckyalanzhou.barcodegenerator.ui.app.composeAppShellActions
import com.luckyalanzhou.barcodegenerator.ui.app.showComposeDialog
import com.luckyalanzhou.barcodegenerator.ui.app.toast

import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    DebugLog.actionStarted("favorites_backup_share")
    val name = timestampedBackupFileName()
    toast("正在准备收藏备份…")
    lifecycleScope.launch(Dispatchers.IO) {
        val shareDirectory = ensureShareableCacheDirectory(cacheDir, SHAREABLE_FAVORITES_DIRECTORY)
        val result = prepareFavoriteBackupShareArtifact(shareDirectory, name) { artifact ->
            artifact.outputStream().use { output -> favoritesViewModel.exportFavorites(output) }
            FileProvider.getUriForFile(this@shareFavoritesExportForCompose, "$packageName.fileprovider", artifact)
        }
        withContext(Dispatchers.Main) {
            result.onSuccess { (exportFile, exportUri) ->
                DebugLog.actionSucceeded("favorites_backup_prepare", "bytes=${exportFile.length()}")
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, exportUri)
                    putExtra(Intent.EXTRA_TITLE, "收藏备份 · $name")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("收藏备份", exportUri)
                }
                launchFavoriteBackupChooser(
                    launch = { startActivity(Intent.createChooser(share, "分享收藏备份")) },
                    cleanupArtifact = { exportFile.delete() },
                    onFailure = { error ->
                        DebugLog.actionFailed("favorites_backup_share", error)
                        toast("无法打开分享面板，请使用保存备份")
                    },
                )
            }.onFailure {
                DebugLog.actionFailed("favorites_backup_prepare", it)
                toast(formatFavoritesExportError(it))
            }
        }
    }
}

/** 失败或取消分享备份生成时删除部分 ZIP；只将普通异常作为可恢复结果返回。 */
internal suspend fun <T> prepareFavoriteBackupShareArtifact(
    directory: File,
    requestedName: String,
    deleteArtifact: (File) -> Boolean = File::delete,
    prepare: suspend (File) -> T,
): Result<Pair<File, T>> {
    var artifact: File? = null
    return try {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建备份缓存目录" }
        val createdArtifact = createUniqueFavoriteBackupShareFile(directory, requestedName)
        artifact = createdArtifact
        Result.success(createdArtifact to prepare(createdArtifact))
    } catch (cancelled: CancellationException) {
        cleanupPartialFavoriteBackupArtifact(artifact, cancelled, deleteArtifact)
        throw cancelled
    } catch (error: Throwable) {
        cleanupPartialFavoriteBackupArtifact(artifact, error, deleteArtifact)
        if (error !is Exception) throw error
        Result.failure(error)
    }
}

/** 清理失败不得遮蔽准备失败；清理抛出致命错误时保留它并附带原始失败。 */
private fun cleanupPartialFavoriteBackupArtifact(
    artifact: File?,
    failure: Throwable,
    deleteArtifact: (File) -> Boolean,
) {
    if (artifact == null) return
    try {
        deleteArtifact(artifact)
    } catch (cleanupFailure: Throwable) {
        if (cleanupFailure === failure) return
        if (cleanupFailure is Error && failure !is Error) {
            cleanupFailure.addSuppressed(failure)
            throw cleanupFailure
        }
        failure.addSuppressed(cleanupFailure)
    }
}

/** 文件名秒级时间戳可能重复；原子预留同名候选或带序号的新文件，绝不截断已有 ZIP。 */
internal fun createUniqueFavoriteBackupShareFile(directory: File, requestedName: String): File {
    require(requestedName.isNotBlank() && File(requestedName).name == requestedName) {
        "备份文件名无效"
    }
    val extension = requestedName.substringAfterLast('.', "")
    val stem = if (extension.isEmpty()) requestedName else requestedName.removeSuffix(".$extension")
    val extensionSuffix = if (extension.isEmpty()) "" else ".$extension"
    var suffix = 0L
    while (true) {
        val candidateName = if (suffix == 0L) requestedName else "$stem-$suffix$extensionSuffix"
        val candidate = File(directory, candidateName)
        if (candidate.createNewFile()) return candidate
        suffix++
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
    DebugLog.actionStarted("favorites_backup_export")
    lifecycleScope.launch(Dispatchers.IO) {
        val result = captureRecoverableFavoriteBackupOperation {
            writeCreatedDocumentWithRollbackSuspending(
                write = {
                    val temporary = File.createTempFile("favorites-export-", ".zip", cacheDir)
                    try {
                        temporary.outputStream().use { output -> favoritesViewModel.exportFavorites(output) }
                        contentResolver.openOutputStream(uri)?.use { output ->
                            temporary.inputStream().use { input -> input.copyTo(output) }
                        } ?: error("无法写入备份文件")
                    } finally {
                        temporary.delete()
                    }
                },
                deleteCreatedDocument = { contentResolver.delete(uri, null, null) > 0 },
            )
        }
        withContext(Dispatchers.Main) {
            result
                .onSuccess { DebugLog.actionSucceeded("favorites_backup_export"); toast("收藏备份已导出") }
                .onFailure { DebugLog.actionFailed("favorites_backup_export", it); toast(formatFavoritesExportError(it)) }
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
    DebugLog.actionStarted("favorites_backup_parse")
    lifecycleScope.launch(Dispatchers.IO) {
        val parsed = captureRecoverableFavoriteBackupOperation {
            val declaredSize = try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            } ?: -1L
            require(declaredSize < 0L || declaredSize <= MAX_FAVORITES_BACKUP_INPUT_BYTES.toLong()) {
                "备份文件超过 64 MB 限制"
            }
            val bytes = contentResolver.openInputStream(uri)?.use { input -> readFavoritesBackupBounded(input) }
                ?: error("无法读取备份文件")
            favoritesViewModel.restoreFavorites(bytes)
        }
        withContext(Dispatchers.Main) {
            parsed
                .onFailure { DebugLog.actionFailed("favorites_backup_parse", it); toast("无法导入收藏：${it.message ?: "文件格式无效"}") }
                .onSuccess { backup -> DebugLog.actionSucceeded("favorites_backup_parse"); confirmImportFavoritesCompose(uri, backup) }
        }
    }
}

internal fun MainActivity.importFavoritesForCompose(backup: InterchangeBackup, overwriteConflicts: Boolean = false) {
    DebugLog.actionStarted("favorites_backup_import", "overwrite=$overwriteConflicts")
    lifecycleScope.launch(Dispatchers.IO) {
        val result = captureRecoverableFavoriteBackupOperation {
            val counts = favoritesViewModel.importFavorites(backup, overwriteConflicts)
            favoritesViewModel.addCollapsed(
                (backup.folders + backup.favorites.map { it.folder }).filter { it.isNotBlank() }.toSet(),
            )
            counts
        }
        withContext(Dispatchers.Main) {
            result
                .onSuccess { (itemCount, groupCount) ->
                    DebugLog.actionSucceeded("favorites_backup_import", "groups=$groupCount items=$itemCount")
                    composeAppShellActions().navigateTo(AppRoute.Favorites)
                    toast("已导入 $groupCount 个收藏，$itemCount 条码")
                }
                .onFailure { error ->
                    DebugLog.actionFailed("favorites_backup_import", error)
                    toast(favoritesImportFailureMessage(error))
                }
        }
    }
}

/** 仅把普通备份操作异常转为界面结果；协程取消和 JVM 致命错误继续向上传播。 */
internal suspend fun <T> captureRecoverableFavoriteBackupOperation(
    operation: suspend () -> T,
): Result<T> = try {
    Result.success(operation())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

internal fun favoritesImportFailureMessage(error: Throwable): String =
    (error as? FavoritesImportRefreshException)?.let {
        "数据库已写入 ${it.importedGroupCount} 个收藏、${it.importedItemCount} 条码，但列表刷新失败；请重启应用后查看"
    } ?: "收藏导入失败：${error.message ?: "无法写入数据"}"
