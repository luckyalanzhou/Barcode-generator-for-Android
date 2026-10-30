package com.luckyalanzhou.barcodegenerator.data.platform

import java.io.File

internal const val UPDATE_APK_DOWNLOAD_FILE_PREFIX = "barcode-generator-update-"
internal const val UPDATE_APK_PARTIAL_FILE_SUFFIX = ".apk.part"
internal const val UPDATE_APK_CACHE_RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1_000L
internal const val UPDATE_APK_SHARE_CACHE_DIRECTORY = "share/updates"

/** 清理上次进程中断遗留的更新临时包，跳过当前进程仍在写入的文件。 */
internal fun cleanupOrphanedApkPartFiles(cacheDir: File, activeFiles: Set<File>): Int {
    val candidates = try {
        cacheDir.listFiles().orEmpty()
    } catch (_: Exception) {
        return 0
    }
    var removedCount = 0
    candidates.forEach { file ->
        val isOwnedPartial = file.isFile &&
            file.name.startsWith(UPDATE_APK_DOWNLOAD_FILE_PREFIX) &&
            file.name.endsWith(UPDATE_APK_PARTIAL_FILE_SUFFIX)
        if (!isOwnedPartial || file in activeFiles) return@forEach
        val deleted = try {
            file.delete()
        } catch (_: Exception) {
            false
        }
        if (deleted) removedCount++
    }
    return removedCount
}

/** 清理过期更新 APK；调用方传入 SavedState 中仍待安装的文件路径以保护安装流程。 */
fun cleanupExpiredApkDownloadFiles(
    cacheDir: File,
    protectedApkPaths: Set<String>,
    nowMillis: Long,
): Int {
    val candidates = listOf(cacheDir, File(cacheDir, UPDATE_APK_SHARE_CACHE_DIRECTORY)).flatMap { directory ->
        try {
            directory.listFiles().orEmpty().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    val expiredBeforeMillis = nowMillis - UPDATE_APK_CACHE_RETENTION_MILLIS
    var removedCount = 0
    candidates.forEach { file ->
        val isOwnedApk = file.isFile &&
            file.name.startsWith(UPDATE_APK_DOWNLOAD_FILE_PREFIX) &&
            file.name.endsWith(".apk")
        if (!isOwnedApk || file.absolutePath in protectedApkPaths || file.lastModified() >= expiredBeforeMillis) {
            return@forEach
        }
        val deleted = try {
            file.delete()
        } catch (_: Exception) {
            false
        }
        if (deleted) removedCount++
    }
    return removedCount
}
