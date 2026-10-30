package com.luckyalanzhou.barcodegenerator.data

import java.io.File
import java.util.concurrent.CancellationException

/** 限制可重新生成的磁盘条码缓存，并优先淘汰最近最少使用的图片。 */
internal object BarcodeImageCachePolicy {
    const val MAX_DISK_BYTES = 64L * 1024L * 1024L
    const val MAX_IMAGE_FILES = 512

    fun prune(
        directory: File,
        protectedFile: File,
        maxBytes: Long = MAX_DISK_BYTES,
        maxImageFiles: Int = MAX_IMAGE_FILES,
        activeTemporaryNames: Set<String> = emptySet(),
    ) {
        val files = try {
            directory.listFiles()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: run {
            // 无法读取目录时丢弃本次缓存，避免把未统计的旧文件继续叠加。
            deleteIfPossible(protectedFile)
            return
        }
        val images = files.filter { it.isFile && it.name.endsWith(".png") }
        // 无法确认当前图片的规范路径时，停止淘汰以免误删正在使用的缓存。
        val protected = try {
            protectedFile.canonicalFile
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }

        files
            .filter { it.isFile && it.name.startsWith(".") && it.name.endsWith(".tmp") && it.name !in activeTemporaryNames }
            .forEach(::deleteIfPossible)

        var totalBytes = images.sumOf { it.length() }
        var fileCount = images.size
        val oldestFirst = images
            .filterNot { file ->
                try {
                    file.canonicalFile == protected
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // 路径无法核实时保留该文件，避免误删可能受保护的条码图片。
                    true
                }
            }
            .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })

        for (file in oldestFirst) {
            if (totalBytes <= maxBytes && fileCount <= maxImageFiles) break
            val fileBytes = file.length()
            if (deleteIfPossible(file)) {
                totalBytes -= fileBytes
                fileCount--
            }
        }

        if (totalBytes > maxBytes || fileCount > maxImageFiles) {
            // 旧缓存无法腾出空间时，丢弃本次可重建文件，避免失败的淘汰让缓存持续增长。
            deleteIfPossible(protectedFile)
        }
    }

    private fun deleteIfPossible(file: File): Boolean = try {
        file.delete()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }
}
