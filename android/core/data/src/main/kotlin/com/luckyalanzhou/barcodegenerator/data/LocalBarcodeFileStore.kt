package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.security.MessageDigest

/** 应用私有文件数据源；Room 仍然是条码索引和查询的唯一数据源。 */
class LocalBarcodeFileStore(context: Context) {
    private val root = File(context.filesDir, "barcode-data")
    // 只缓存最近使用的图片，避免收藏数量增长时 Bitmap 无上限占用内存。
    private val imageMemoryCache = object : LruCache<String, Bitmap>(64 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.rowBytes * value.height / 1024).coerceAtLeast(1)
    }

    @Synchronized
    fun readImage(key: String): Bitmap? {
        imageMemoryCache.get(key)?.let { return it }
        return BitmapFactory.decodeFile(File(root, "images/$key.png").absolutePath)?.also {
            imageMemoryCache.put(key, it)
        }
    }

    @Synchronized
    fun writeImage(key: String, bitmap: Bitmap) {
        val directory = File(root, "images")
        directory.mkdirs()
        val target = File(directory, "$key.png")
        val temporary = File(directory, ".$key.tmp")
        temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!temporary.renameTo(target)) { target.delete(); check(temporary.renameTo(target)) }
        imageMemoryCache.put(key, bitmap)
    }

    fun imageKey(item: CodeItem, width: Int, height: Int, textSize: Float, showFormat: Boolean, dark: Boolean): String {
        val raw = listOf(item.text, item.format, width, height, textSize, showFormat, dark).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

}
