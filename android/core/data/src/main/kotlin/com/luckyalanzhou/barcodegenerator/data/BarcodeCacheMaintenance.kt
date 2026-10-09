package com.luckyalanzhou.barcodegenerator.data

import java.io.File

/** 只在初始化或达到上限时扫描目录；普通图片写入按文件大小增量维护缓存预算。 */
internal class BarcodeCacheMaintenance(
    private val maxBytes: Long = BarcodeImageCachePolicy.MAX_DISK_BYTES,
    private val maxFiles: Int = BarcodeImageCachePolicy.MAX_IMAGE_FILES,
) {
    private val sizes = mutableMapOf<String, Long>()
    private var initialized = false
    private var totalBytes = 0L
    internal var directoryScans = 0
        private set

    @Synchronized
    fun onStored(directory: File, protectedFile: File, activeTemporaryNames: Set<String>) {
        if (!initialized) {
            BarcodeImageCachePolicy.prune(directory, protectedFile, maxBytes, maxFiles, activeTemporaryNames)
            rescan(directory)
            initialized = true
        } else {
            val size = protectedFile.length()
            totalBytes += size - (sizes.put(protectedFile.name, size) ?: 0L)
            if (totalBytes > maxBytes || sizes.size > maxFiles) {
                BarcodeImageCachePolicy.prune(directory, protectedFile, maxBytes, maxFiles, activeTemporaryNames)
                rescan(directory)
            }
        }
    }

    private fun rescan(directory: File) {
        directoryScans++
        sizes.clear()
        directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".png") }
            .forEach { sizes[it.name] = it.length() }
        totalBytes = sizes.values.sum()
    }
}
