package com.luckyalanzhou.barcodegenerator

import android.content.ContentResolver
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val FAVORITES_DIRECTORY = "favorites"
private const val MAX_BACKUP_INPUT_BYTES = 64 * 1024 * 1024
private const val MAX_BACKUP_FAVORITE_JSON_BYTES = 1 * 1024 * 1024
private const val MAX_BACKUP_UNCOMPRESSED_BYTES = 32 * 1024 * 1024
private const val MAX_BACKUP_ZIP_ENTRIES = 2_048

data class InterchangeFavorite(
    val id: String?,
    val name: String,
    val rootFolder: String,
    val subFolder: String,
    val type: String,
    val time: Long,
    val texts: List<String>
) {
    val folder: String get() = listOf(rootFolder, subFolder).filter { it.isNotBlank() }.joinToString("/")
}

data class InterchangeBackup(val favorites: List<InterchangeFavorite>, val folders: List<String>)

data class TransferEntities(
    val items: List<CodeItemEntity>,
    val groups: List<FavoriteGroupEntity>,
    val links: List<FavoriteGroupItemEntity>,
    val folders: List<FavoriteFolderEntity>
)

object FavoritesTransferManager {
    fun export(resolver: ContentResolver, uri: Uri, groups: List<FavoriteGroupEntity>, links: List<FavoriteGroupItemEntity>, items: List<CodeItemEntity>, folders: List<FavoriteFolderEntity>) {
        val itemById = items.associateBy { it.id }
        val linksByGroup = links.groupBy { it.groupId }
        resolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                // ZIP 只保留目录和每个收藏文件；不再写入包含全部收藏的聚合 JSON。
                val writtenDirectories = mutableSetOf<String>()
                ensureZipDirectories(zip, FAVORITES_DIRECTORY, writtenDirectories)
                folders.map { it.name }.filter { it.isNotBlank() }.forEach { folder ->
                    val parts = splitFolder(folder)
                    val path = listOf(FAVORITES_DIRECTORY, parts.first, parts.second).filter { it.isNotBlank() }.joinToString("/")
                    ensureZipDirectories(zip, path, writtenDirectories)
                }
                groups.forEach { group ->
                    val groupItems = linksByGroup[group.id].orEmpty().mapNotNull { itemById[it.itemId] }
                    val types = groupItems.map { toTransferType(it.format) }.distinct()
                    val parts = splitFolder(group.folder)
                    val favorite = InterchangeFavorite(
                        id = group.id.toString(), name = group.name, rootFolder = parts.first, subFolder = parts.second,
                        type = types.firstOrNull() ?: "code128", time = group.savedAt, texts = groupItems.map { it.text }
                    )
                    val path = favoriteZipPath(favorite)
                    val directory = path.substringBeforeLast('/')
                    ensureZipDirectories(zip, directory, writtenDirectories)
                    writeZipEntry(zip, path, favoriteJson(favorite).toString().toByteArray(Charsets.UTF_8))
                }
            }
        } ?: error("无法创建收藏备份文件")
    }

    fun restore(resolver: ContentResolver, uri: Uri): InterchangeBackup {
        val bytes = resolver.openInputStream(uri)?.use { readLimited(it, MAX_BACKUP_INPUT_BYTES) } ?: error("无法读取收藏备份文件")
        require(bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) { "仅支持逐收藏文件 ZIP 备份" }
        return extractBackupZip(bytes)
    }

    /** 逐收藏文件是 ZIP 备份唯一的数据源。 */
    private fun extractBackupZip(bytes: ByteArray): InterchangeBackup {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entries = 0
            var uncompressed = 0
            val favorites = mutableListOf<InterchangeFavorite>()
            val folders = linkedSetOf<String>()
            var hasFavoritesRoot = false
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= MAX_BACKUP_ZIP_ENTRIES) { "ZIP 备份包含过多文件" }
                if (entry.isDirectory && entry.name.startsWith("$FAVORITES_DIRECTORY/")) {
                    hasFavoritesRoot = true
                    entry.name.removePrefix("$FAVORITES_DIRECTORY/").trim('/').takeIf { it.isNotBlank() }?.let { folder ->
                        val parts = splitFolder(folder)
                        if (parts.first.isNotBlank()) folders += parts.first
                        if (folder.isNotBlank()) folders += folder
                    }
                } else if (!entry.isDirectory && entry.name.startsWith("$FAVORITES_DIRECTORY/") && entry.name.endsWith(".json")) {
                    hasFavoritesRoot = true
                    val content = readLimited(zip, MAX_BACKUP_FAVORITE_JSON_BYTES)
                    uncompressed += content.size
                    require(uncompressed <= MAX_BACKUP_UNCOMPRESSED_BYTES) { "收藏备份解压后内容超过 32 MB 限制" }
                    val favorite = parseFavoriteJson(content.toString(Charsets.UTF_8))
                    favorites += favorite
                    if (favorite.rootFolder.isNotBlank()) folders += favorite.rootFolder
                    if (favorite.folder.isNotBlank()) folders += favorite.folder
                }
                zip.closeEntry()
            }
            if (hasFavoritesRoot) {
                require(favorites.map { Triple(it.folder, it.name, it.texts) }.distinct().size == favorites.size) { "跨平台备份中包含重复收藏" }
                return InterchangeBackup(favorites, folders.toList())
            }
        }
        error("ZIP 备份中未找到收藏文件")
    }

    private fun parseFavoriteJson(json: String): InterchangeFavorite {
        val value = runCatching { JSONObject(json) }.getOrElse { error("收藏文件不是有效 JSON") }
        val legacyPath = value.optString("folder", "").trim()
        val rootFolder = value.optString("rootFolder", legacyPath.substringBefore('/')).trim()
        val subFolder = value.optString("subFolder", legacyPath.substringAfter('/', "")).trim()
        val name = value.optString("name").trim()
        val texts = value.optJSONArray("texts").toStrings().map { it.trim() }.filter { it.isNotEmpty() }
        require(name.isNotBlank()) { "收藏文件缺少文件名" }
        require(rootFolder.isBlank() || !rootFolder.contains('/')) { "一级文件夹格式无效" }
        require(subFolder.isBlank() || !subFolder.contains('/')) { "二级文件夹格式无效" }
        return InterchangeFavorite(value.optString("id").takeIf { it.isNotBlank() }, name, rootFolder, subFolder, toTransferType(value.optString("type", value.optString("barcodeType", "code128"))), value.optLong("time", System.currentTimeMillis()), texts)
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "备份文件超过 ${limit / 1024 / 1024} MB 限制" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun appendEntities(backup: InterchangeBackup, existingItems: List<CodeItemEntity>, existingGroups: List<FavoriteGroupEntity>, existingLinks: List<FavoriteGroupItemEntity>): TransferEntities {
        var nextItemId = (existingItems.maxOfOrNull { it.id } ?: 0L) + 1L
        var nextGroupId = (existingGroups.maxOfOrNull { it.id } ?: 0L) + 1L
        val existingGroupItems = existingGroups.associate { group -> group.id to existingLinks.filter { it.groupId == group.id }.mapNotNull { link -> existingItems.firstOrNull { it.id == link.itemId }?.text } }
        val existingKeys = existingGroups.map { group -> Triple(group.folder, group.name, existingGroupItems[group.id].orEmpty()) }.toMutableSet()
        val items = mutableListOf<CodeItemEntity>(); val groups = mutableListOf<FavoriteGroupEntity>(); val links = mutableListOf<FavoriteGroupItemEntity>()
        backup.favorites.forEach { favorite ->
            val key = Triple(favorite.folder, favorite.name, favorite.texts)
            if (!existingKeys.add(key)) return@forEach
            val group = FavoriteGroupEntity(nextGroupId++, favorite.folder, favorite.name, favorite.time)
            val groupItems = favorite.texts.map { text -> CodeItemEntity(nextItemId++, text, toAndroidFormat(favorite.type), favorite.time, true, favorite.folder, false) }
            groups += group; items += groupItems; links += groupItems.map { FavoriteGroupItemEntity(group.id, it.id) }
        }
        val folderNames = (backup.folders + backup.favorites.map { it.folder }).filter { it.isNotBlank() }.distinct()
        return TransferEntities(items, groups, links, folderNames.map(::FavoriteFolderEntity))
    }

    private fun splitFolder(folder: String): Pair<String, String> {
        val parts = folder.split('/').filter { it.isNotBlank() }
        require(parts.size <= 2) { "文件夹“$folder”超过两级，无法跨平台导出" }
        parts.forEach { require(it != "." && it != ".." && !it.contains('\\')) { "文件夹“$folder”格式无效" } }
        return (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
    }
    private fun favoriteJson(favorite: InterchangeFavorite) = JSONObject().apply {
        put("id", favorite.id); put("name", favorite.name); put("rootFolder", favorite.rootFolder); put("subFolder", favorite.subFolder)
        put("folder", favorite.folder); put("type", favorite.type); put("barcodeType", favorite.type); put("time", favorite.time); put("texts", JSONArray(favorite.texts))
    }
    internal fun favoriteZipPath(favorite: InterchangeFavorite): String {
        val id = favorite.id?.takeIf { it.isNotBlank() } ?: error("收藏缺少文件标识")
        return listOf(FAVORITES_DIRECTORY, favorite.rootFolder, favorite.subFolder, "$id.json")
            .filter { it.isNotBlank() }
            .joinToString("/")
    }
    private fun ensureZipDirectories(zip: ZipOutputStream, directory: String, written: MutableSet<String>) {
        var path = ""
        directory.split('/').filter { it.isNotBlank() }.forEach { part ->
            path = if (path.isBlank()) part else "$path/$part"
            if (written.add(path)) writeZipEntry(zip, "$path/", ByteArray(0))
        }
    }

    /** 使用标准 DEFLATED ZIP 条目，兼容 iOS 文件、Windows 资源管理器和 WinRAR。 */
    private fun writeZipEntry(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }
    private fun toTransferType(format: String): String = when (format.trim().lowercase()) {
        "qr", "qr code" -> "qr"; "code128", "code 128-b" -> "code128"; "code39", "code 39" -> "code39"; "ean13", "ean-13" -> "ean13"; "ean8", "ean-8" -> "ean8"; "upca", "upc-a" -> "upca"; "itf14", "itf-14", "itf" -> "itf14"; "codabar" -> "codabar"; else -> error("不支持的条码格式：$format")
    }
    private fun toAndroidFormat(type: String): String = when (toTransferType(type)) {
        "qr" -> "QR Code"; "code128" -> "Code 128-B"; "code39" -> "Code 39"; "ean13" -> "EAN-13"; "ean8" -> "EAN-8"; "upca" -> "UPC-A"; "itf14" -> "ITF-14"; else -> "Codabar"
    }
    private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
