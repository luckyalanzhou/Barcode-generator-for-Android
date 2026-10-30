package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File

internal const val SHARE_CACHE_ARTIFACT_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
internal const val INTERRUPTED_FAVORITE_EXPORT_GRACE_MILLIS = 5_000L

/** 清理中断的收藏导出临时 ZIP，以及本应用生成的过期分享 ZIP 和结果 PNG。 */
internal fun cleanupExpiredShareCacheArtifacts(
    cacheDirectory: File,
    backupFileName: String,
    nowMillis: Long,
): Int {
    val expiredBeforeMillis = nowMillis - SHARE_CACHE_ARTIFACT_RETENTION_MILLIS
    val backupPrefix = backupFileName
        .takeIf { it == File(it).name && it.endsWith(".zip") }
        ?.removeSuffix(".zip")
        ?.takeIf(String::isNotBlank)
        ?.plus("-")
    val sharedDirectory = File(cacheDirectory, SHAREABLE_CACHE_ROOT)
    val expiredBackups = backupPrefix?.let { prefix ->
        listOf(cacheDirectory, File(sharedDirectory, SHAREABLE_FAVORITES_DIRECTORY)).sumOf { directory ->
            deleteExpiredFiles(directory, expiredBeforeMillis) { name ->
                name.startsWith(prefix) && name.endsWith(".zip")
            }
        }
    } ?: 0
    val expiredImages = listOf(
        File(cacheDirectory, LEGACY_SHARED_IMAGES_DIRECTORY),
        File(sharedDirectory, SHAREABLE_IMAGES_DIRECTORY),
    ).sumOf { directory ->
        deleteExpiredFiles(directory, expiredBeforeMillis) { it.endsWith(".png") }
    }
    val interruptedFavoriteExports = deleteExpiredFiles(
        cacheDirectory,
        nowMillis - INTERRUPTED_FAVORITE_EXPORT_GRACE_MILLIS,
    ) { name ->
        name.startsWith(FAVORITE_EXPORT_TEMP_PREFIX) && name.endsWith(".zip")
    }
    val expiredResultDocumentExports = deleteExpiredFiles(cacheDirectory, expiredBeforeMillis) { name ->
        name.startsWith(RESULT_DOCUMENT_EXPORT_TEMP_PREFIX) && name.endsWith(".png")
    }
    return expiredBackups + expiredImages + interruptedFavoriteExports + expiredResultDocumentExports
}

private fun deleteExpiredFiles(
    directory: File,
    expiredBeforeMillis: Long,
    matches: (String) -> Boolean,
): Int {
    val entries = try {
        directory.listFiles().orEmpty()
    } catch (_: Exception) {
        return 0
    }
    var deletedCount = 0
    entries.forEach { file ->
        if (!file.isFile || file.lastModified() >= expiredBeforeMillis || !matches(file.name)) return@forEach
        try {
            if (file.delete()) deletedCount++
        } catch (_: Exception) {
            // 缓存清理尽力执行；失败不影响应用启动或分享。
        }
    }
    return deletedCount
}

private const val LEGACY_SHARED_IMAGES_DIRECTORY = "shared-images"
private const val FAVORITE_EXPORT_TEMP_PREFIX = "favorites-export-"
private const val RESULT_DOCUMENT_EXPORT_TEMP_PREFIX = "result-export-"
