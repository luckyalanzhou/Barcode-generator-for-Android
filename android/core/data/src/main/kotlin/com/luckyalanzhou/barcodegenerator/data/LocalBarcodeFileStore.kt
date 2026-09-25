package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** 应用私有文件数据源；Room 仍然是条码索引和查询的唯一数据源。 */
class LocalBarcodeFileStore(context: Context) {
    private val root = File(context.cacheDir, "barcode-data")
    private val imageDirectory = File(root, "images")
    private val legacyImageDirectory = File(File(context.filesDir, "barcode-data"), "images")

    init {
        removeLegacyImageCache()
    }

    // 只缓存最近使用的图片，避免收藏数量增长时 Bitmap 无上限占用内存。
    private val imageMemoryCache = object : LruCache<String, Bitmap>(64 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.rowBytes * value.height / 1024).coerceAtLeast(1)
    }

    @Synchronized
    fun readImage(key: String): Bitmap? {
        if (!isValidKey(key)) return null
        imageMemoryCache.get(key)?.let {
            touchImage(key)
            return it
        }
        return BitmapFactory.decodeFile(File(imageDirectory, "$key.png").absolutePath)?.also {
            touchImage(key)
            imageMemoryCache.put(key, it)
        }
    }

    @Synchronized
    fun writeImage(key: String, bitmap: Bitmap) {
        if (!isValidKey(key)) return
        imageMemoryCache.put(key, bitmap)
        imageDirectory.mkdirs()
        val target = File(imageDirectory, "$key.png")
        val temporary = File(imageDirectory, ".$key.tmp")
        val compressed = runCatching {
            temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.getOrDefault(false)
        if (!compressed || temporary.length() > BarcodeImageCachePolicy.MAX_DISK_BYTES) {
            temporary.delete()
            return
        }
        val stored = temporary.renameTo(target) ||
            (!target.exists() || target.delete()) && temporary.renameTo(target)
        if (!stored) {
            temporary.delete()
            return
        }
        BarcodeImageCachePolicy.prune(imageDirectory, target)
    }

    private fun isValidKey(key: String): Boolean = key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' }

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
