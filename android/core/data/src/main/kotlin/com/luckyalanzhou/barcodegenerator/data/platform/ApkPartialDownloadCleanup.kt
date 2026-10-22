package com.luckyalanzhou.barcodegenerator.data.platform

import java.io.File

internal const val UPDATE_APK_DOWNLOAD_FILE_PREFIX = "barcode-generator-update-"
internal const val UPDATE_APK_PARTIAL_FILE_SUFFIX = ".apk.part"

/** 仅清理上次进程中断遗留的更新临时包，跳过当前进程仍在写入的文件。 */
internal fun cleanupOrphanedApkPartFiles(cacheDir: File, activeFiles: Set<File>): Int {
    val candidates = cacheDir.listFiles() ?: return 0
    var removedCount = 0
    candidates.forEach { file ->
        val isOwnedPartial = file.name.startsWith(UPDATE_APK_DOWNLOAD_FILE_PREFIX) &&
            file.name.endsWith(UPDATE_APK_PARTIAL_FILE_SUFFIX)
        if (isOwnedPartial && file !in activeFiles && file.delete()) {
            removedCount++
        }
    }
    return removedCount
}
