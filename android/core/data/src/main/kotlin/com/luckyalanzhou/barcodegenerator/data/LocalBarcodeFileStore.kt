package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** 应用私有文件数据源；Room 仍然是条码索引和查询的唯一数据源。 */
class LocalBarcodeFileStore(context: Context) {
    private val root = File(context.filesDir, "barcode-data")
    private val historyRoot = File(root, "history")
    private val favoritesRoot = File(root, "favorites")

    fun readImage(key: String): Bitmap? = BitmapFactory.decodeFile(File(root, "images/$key.png").absolutePath)

    @Synchronized
    fun writeImage(key: String, bitmap: Bitmap) {
        val directory = File(root, "images")
        directory.mkdirs()
        val target = File(directory, "$key.png")
        val temporary = File(directory, ".$key.tmp")
        temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!temporary.renameTo(target)) { target.delete(); check(temporary.renameTo(target)) }
    }

    fun imageKey(item: CodeItem, width: Int, height: Int, textSize: Float, showFormat: Boolean, dark: Boolean): String {
        val raw = listOf(item.text, item.format, width, height, textSize, showFormat, dark).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    fun rebuildHistory(items: List<CodeItem>) {
        historyRoot.deleteRecursively()
        val historyItems = items.filter { it.inHistory }
        if (historyItems.isEmpty()) return
        historyRoot.mkdirs()
        historyItems.groupBy { it.createdAt }.forEach { (createdAt, batch) ->
            val payload = JSONObject().apply {
                put("createdAt", createdAt)
                put("items", JSONArray().apply { batch.forEach { put(itemJson(it)) } })
            }
            writeAtomically(File(historyRoot, "${createdAt}_${batch.first().id}.json"), payload.toString())
        }
    }

    @Synchronized
    fun rebuildFavorites(groups: List<FavoriteGroup>, items: List<CodeItem>) {
        favoritesRoot.deleteRecursively()
        if (groups.isEmpty()) return
        val itemById = items.associateBy { it.id }
        groups.forEach { group ->
            val groupItems = group.itemIds.mapNotNull(itemById::get)
            if (groupItems.isEmpty()) return@forEach
            val parts = splitFolder(group.folder)
            val directory = listOf(parts.first, parts.second).filter { it.isNotBlank() }
                .fold(favoritesRoot) { parent, name -> File(parent, safeSegment(name)) }
            directory.mkdirs()
            val favorite = JSONObject().apply {
                put("id", group.id.toString())
                put("name", group.name)
                put("rootFolder", parts.first)
                put("subFolder", parts.second)
                put("folder", group.folder)
                put("type", groupItems.first().format)
                put("barcodeType", groupItems.first().format)
                put("time", group.savedAt)
                put("texts", JSONArray().apply { groupItems.forEach { put(it.text) } })
            }
            writeAtomically(File(directory, "${safeSegment(group.name)}.json"), favorite.toString())
        }
    }

    private fun itemJson(item: CodeItem) = JSONObject().apply {
        put("id", item.id)
        put("text", item.text)
        put("format", item.format)
        put("createdAt", item.createdAt)
        put("favorite", item.favorite)
    }

    private fun splitFolder(folder: String): Pair<String, String> {
        val parts = folder.split('/').filter { it.isNotBlank() }.take(2)
        return (parts.getOrNull(0) ?: "默认") to (parts.getOrNull(1) ?: "")
    }

    private fun safeSegment(value: String): String = value.trim()
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trimEnd('.', ' ')
        .ifBlank { "未命名" }
        .take(120)

    private fun writeAtomically(target: File, content: String) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, ".${target.name}.tmp")
        temporary.writeText(content, Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            target.delete()
            check(temporary.renameTo(target)) { "无法保存条码文件：${target.name}" }
        }
    }
}
