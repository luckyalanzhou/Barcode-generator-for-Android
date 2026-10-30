package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CancellationException

/** 磁盘图片只是可重建缓存；普通文件系统异常不能阻断条码渲染，取消和 fatal Error 仍向上传播。 */
internal inline fun runRegenerableCacheWrite(write: () -> Unit) {
    try {
        write()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // 缓存写入或淘汰失败时继续使用刚生成的内存图片。
    }
}

/** 普通磁盘读取异常按缓存未命中处理；取消和 fatal Error 继续向上传播。 */
internal inline fun <T> runRegenerableCacheRead(read: () -> T?): T? {
    try {
        return read()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        return null
    }
}

internal inline fun writeBitmapCacheFile(
    temporary: File,
    compress: (OutputStream) -> Boolean
): Boolean {
    var completed = false
    var primaryFailure: Throwable? = null
    return try {
        temporary.outputStream().use(compress).also { completed = it }
    } catch (cancellation: CancellationException) {
        primaryFailure = cancellation
        throw cancellation
    } catch (failure: Exception) {
        primaryFailure = failure
        false
    } catch (failure: Error) {
        primaryFailure = failure
        throw failure
    } finally {
        if (!completed) {
            try {
                temporary.delete()
            } catch (cleanupFailure: Throwable) {
                preserveCacheWriteFailurePriority(primaryFailure, cleanupFailure)
            }
        }
    }
}

/** 保留致命主故障；清理阶段出现更高优先级的致命错误或取消时保留两侧原因。 */
private fun preserveCacheWriteFailurePriority(primaryFailure: Throwable?, cleanupFailure: Throwable) {
    if (primaryFailure === cleanupFailure) return
    if (primaryFailure is Error) {
        primaryFailure.addSuppressed(cleanupFailure)
        return
    }
    if (cleanupFailure is Error || cleanupFailure is CancellationException) {
        primaryFailure?.let(cleanupFailure::addSuppressed)
        throw cleanupFailure
    }
    primaryFailure?.addSuppressed(cleanupFailure)
}

/** 应用私有文件数据源；Room 仍然是条码索引和查询的唯一数据源。 */
class LocalBarcodeFileStore(context: Context) {
    private val root = File(context.cacheDir, "barcode-data")
    private val imageDirectory = File(root, "images")
    private val legacyImageDirectory = File(File(context.filesDir, "barcode-data"), "images")
    private val imageLocks = Array(64) { Any() }
    private val maintenance = BarcodeCacheMaintenance()
    private val activeTemporaryNames = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val legacyImageCleanupLock = Any()
    @Volatile private var legacyImageCleanupComplete = false

    // 只缓存最近使用的图片，避免收藏数量增长时 Bitmap 无上限占用内存。
    private val imageMemoryCache = object : LruCache<String, Bitmap>(64 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.rowBytes * value.height / 1024).coerceAtLeast(1)
    }

    fun readImage(key: String): Bitmap? {
        if (!isValidKey(key)) return null
        removeLegacyImageCacheOnce()
        return synchronized(imageLocks[(key.hashCode() and Int.MAX_VALUE) % imageLocks.size]) {
            // 内存命中不再做文件存在性检查和时间戳写入。
            imageMemoryCache.get(key) ?: runRegenerableCacheRead {
                BitmapFactory.decodeFile(File(imageDirectory, "$key.png").absolutePath)?.also {
                    touchImage(key)
                    imageMemoryCache.put(key, it)
                }
            }
        }
    }

    fun writeImage(key: String, bitmap: Bitmap) {
        if (!isValidKey(key)) return
        removeLegacyImageCacheOnce()
        synchronized(imageLocks[(key.hashCode() and Int.MAX_VALUE) % imageLocks.size]) {
            imageMemoryCache.put(key, bitmap)
            val target = File(imageDirectory, "$key.png")
            val temporary = File(imageDirectory, ".$key.tmp")
            activeTemporaryNames.add(temporary.name)
            try {
                runRegenerableCacheWrite {
                    imageDirectory.mkdirs()
                    val compressed = writeBitmapCacheFile(temporary) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    if (!compressed || temporary.length() > BarcodeImageCachePolicy.MAX_DISK_BYTES) {
                        temporary.delete()
                        return@runRegenerableCacheWrite
                    }
                    val stored = temporary.renameTo(target) ||
                        (!target.exists() || target.delete()) && temporary.renameTo(target)
                    if (!stored) {
                        temporary.delete()
                        return@runRegenerableCacheWrite
                    }
                    maintenance.onStored(imageDirectory, target, activeTemporaryNames)
                }
            } finally {
                activeTemporaryNames.remove(temporary.name)
            }
        }
    }

    private fun isValidKey(key: String): Boolean = key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' }

    /** 避免单例缓存被 Activity 主线程首次创建时同步扫描旧版缓存目录。 */
    private fun removeLegacyImageCacheOnce() {
        if (legacyImageCleanupComplete) return
        synchronized(legacyImageCleanupLock) {
            if (legacyImageCleanupComplete) return
            try {
                removeLegacyImageCache()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // 旧图像属于可再生成缓存，清理失败不能阻断当前条码读写。
            }
            legacyImageCleanupComplete = true
        }
    }

    private fun removeLegacyImageCache() {
        val legacyRoot = legacyImageDirectory.parentFile?.canonicalFile ?: return
        val filesRoot = runCatching { legacyImageDirectory.parentFile?.parentFile?.canonicalFile }.getOrNull() ?: return
        if (legacyRoot.name != "barcode-data" || legacyRoot.parentFile != filesRoot) return
        val directory = runCatching { legacyImageDirectory.canonicalFile }.getOrNull() ?: return
        if (directory.parentFile != legacyRoot) return

        legacyImageDirectory.listFiles().orEmpty().forEach { file ->
            if (file.isFile && runCatching { file.canonicalFile.parentFile == directory }.getOrDefault(false)) {
                file.delete()
            }
        }
        legacyImageDirectory.delete()
        legacyRoot.delete()
    }

    private fun touchImage(key: String) {
        File(imageDirectory, "$key.png").takeIf(File::isFile)?.setLastModified(System.currentTimeMillis())
    }
}
