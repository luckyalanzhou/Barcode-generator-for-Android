package com.luckyalanzhou.barcodegenerator.data

import java.io.File

/** Bounds the regenerable on-disk barcode cache and evicts the least-recently-used images first. */
internal object BarcodeImageCachePolicy {
    const val MAX_DISK_BYTES = 64L * 1024L * 1024L
    const val MAX_IMAGE_FILES = 512

    fun prune(
        directory: File,
        protectedFile: File,
        maxBytes: Long = MAX_DISK_BYTES,
        maxImageFiles: Int = MAX_IMAGE_FILES,
    ) {
        val images = directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".png") }
        val protected = runCatching { protectedFile.canonicalFile }.getOrNull()

        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(".") && it.name.endsWith(".tmp") }
            .forEach(File::delete)

        var totalBytes = images.sumOf { it.length() }
        var fileCount = images.size
        val oldestFirst = images
            .filterNot { file -> runCatching { file.canonicalFile == protected }.getOrDefault(false) }
            .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })

        for (file in oldestFirst) {
            if (totalBytes <= maxBytes && fileCount <= maxImageFiles) break
            val fileBytes = file.length()
            if (file.delete()) {
                totalBytes -= fileBytes
                fileCount--
            }
        }
    }
}
