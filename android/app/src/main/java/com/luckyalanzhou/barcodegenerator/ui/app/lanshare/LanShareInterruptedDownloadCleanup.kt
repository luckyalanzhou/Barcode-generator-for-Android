package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import java.io.File

private val previewCacheTemporaryFilePattern = Regex("""^\.[0-9a-f]{64}\.-?\d+\.part$""")

/** 进程启动时只清理 LAN 下载器遗留在缓存根目录的未完成文件。 */
internal fun cleanupInterruptedLanShareDownloads(cacheDirectory: File, modifiedBeforeMillis: Long): Int =
    cleanupMatchingLanShareFiles(cacheDirectory, modifiedBeforeMillis) { name ->
        (name.startsWith("lan-download-") || name.startsWith(".lan-download-")) && name.endsWith(".part")
    }

/** 持久分享目录中的未完成上传不会展示给用户，只保留在进程中断后等待清理。 */
internal fun cleanupInterruptedLanShareUploads(sharedFilesDirectory: File, modifiedBeforeMillis: Long): Int =
    cleanupMatchingLanShareFiles(sharedFilesDirectory, modifiedBeforeMillis) { name ->
        name.startsWith(".lan-upload-") && name.endsWith(".part")
    }

/** 清理服务端实际缓存目录中的中断预览 JPEG，保留仍在生成的文件。 */
internal fun cleanupInterruptedLanSharePreviews(
    cacheDirectory: File,
    modifiedBeforeMillis: Long,
    previewCacheDirectoryName: String,
): Int = cleanupMatchingLanShareFiles(
    File(cacheDirectory, previewCacheDirectoryName),
    modifiedBeforeMillis,
) { name ->
        previewCacheTemporaryFilePattern.matches(name)
    }

private fun cleanupMatchingLanShareFiles(
    directory: File,
    modifiedBeforeMillis: Long,
    matches: (String) -> Boolean,
): Int {
    val entries = try {
        directory.listFiles().orEmpty()
    } catch (_: Exception) {
        return 0
    }
    var deletedCount = 0
    entries.forEach { file ->
        if (!file.isFile || file.lastModified() > modifiedBeforeMillis || !matches(file.name)) return@forEach
        try {
            if (file.delete()) deletedCount++
        } catch (_: Exception) {
            // 缓存回收是尽力而为，不应阻止应用启动。
        }
    }
    return deletedCount
}
