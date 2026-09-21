package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.InterchangeFavorite
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.util.zip.ZipInputStream
import java.util.zip.CRC32

private const val FAVORITES_DIRECTORY = "favorites"
private const val MAX_BACKUP_INPUT_BYTES = 64 * 1024 * 1024
private const val MAX_BACKUP_FAVORITE_JSON_BYTES = 1 * 1024 * 1024
private const val MAX_BACKUP_UNCOMPRESSED_BYTES = 32 * 1024 * 1024
private const val MAX_BACKUP_ZIP_ENTRIES = 2_048

object FavoritesTransferManager {
    fun export(output: OutputStream, groups: List<FavoriteGroupEntity>, links: List<FavoriteGroupItemEntity>, items: List<CodeItemEntity>, folders: List<FavoriteFolderEntity>) {
        val bytes = ByteArrayOutputStream().use { buffer ->
            val itemById = items.associateBy { it.id }
            val linksByGroup = links.groupBy { it.groupId }
            PortableZipWriter(buffer).use { zip ->
                // ZIP 只保留目录和每个收藏文件；不再写入包含全部收藏的聚合 JSON。
                val writtenDirectories = mutableSetOf<String>()
                val writtenFavoritePaths = mutableSetOf<String>()
                ensureZipDirectories(zip, FAVORITES_DIRECTORY, writtenDirectories)
                folders.map { it.name }.filter { it.isNotBlank() }.forEach { folder ->
                    val parts = splitFolder(folder)
                    val path = listOf(FAVORITES_DIRECTORY, parts.first, parts.second).filter { it.isNotBlank() }.joinToString("/")
                    ensureZipDirectories(zip, path, writtenDirectories)
                }
                groups.forEach { group ->
                    val groupItems = linksByGroup[group.id].orEmpty().mapNotNull { itemById[it.itemId] }
                        .filter { it.text.isNotBlank() }
                    require(groupItems.isNotEmpty()) { "收藏“${group.name}”没有有效内容，无法导出" }
                    val types = groupItems.map { toTransferType(it.format) }.distinct()
                    val parts = splitFolder(group.folder)
                    val favorite = InterchangeFavorite(
                        id = group.id.toString(), name = group.name, rootFolder = parts.first, subFolder = parts.second,
                        type = types.firstOrNull() ?: "code128", time = group.savedAt, texts = groupItems.map { it.text }
                    )
                    val path = favoriteZipPath(favorite, writtenFavoritePaths)
                    val directory = path.substringBeforeLast('/')
                    ensureZipDirectories(zip, directory, writtenDirectories)
                    writeZipEntry(zip, path, favoriteJson(favorite).toString().toByteArray(Charsets.UTF_8))
                }
            }
            buffer.toByteArray()
        }
        // 导出完成后使用同一套标准 ZIP/JSON 解析器回读，确保其他端可以解压和导入。
        restore(bytes)
        output.write(bytes)
    }

    fun restore(bytes: ByteArray): InterchangeBackup {
        require(bytes.size <= MAX_BACKUP_INPUT_BYTES) { "备份文件超过 64 MB 限制" }
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
                val relativePath = favoriteRelativePath(entry.name)
                if (relativePath != null && entry.isDirectory) {
                    hasFavoritesRoot = true
                    relativePath.trim('/').takeIf { it.isNotBlank() }?.let { folder ->
                        val parts = splitFolder(folder)
                        if (parts.first.isNotBlank()) folders += parts.first
                        if (folder.isNotBlank()) folders += folder
                    }
                } else if (relativePath != null && !entry.isDirectory && relativePath.endsWith(".json", ignoreCase = true)) {
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
                // 每个 ZIP JSON 文件都代表一个独立收藏。相同文件夹、名称和正文
                // 仍可能对应不同的收藏记录，不能在解析阶段按内容丢弃或拒绝。
                return InterchangeBackup(favorites, folders.toList())
            }
        }
        error("ZIP 备份中未找到收藏文件")
    }

    /**
     * 跨平台 ZIP 常由文件管理器包装一层同名根目录，例如
     * `barcode-generator-backup-xxx/favorites/...`；导入时只取其中的 favorites 子树。
     */
    internal fun favoriteRelativePath(entryName: String): String? {
        val normalized = entryName.replace('\\', '/').trimStart('/')
        val root = "$FAVORITES_DIRECTORY/"
        return when {
            normalized == FAVORITES_DIRECTORY -> ""
            normalized.startsWith(root) -> normalized.removePrefix(root)
            else -> normalized.substringAfter("/$root", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
        }
    }

    private fun parseFavoriteJson(json: String): InterchangeFavorite {
        val value = runCatching { JSONObject(json) }.getOrElse { error("收藏文件不是有效 JSON") }
        val legacyPath = value.optString("folder", "").trim()
        val rootFolder = value.optString("rootFolder", legacyPath.substringBefore('/')).trim()
        val subFolder = value.optString("subFolder", legacyPath.substringAfter('/', "")).trim()
        val name = value.optString("name").trim()
        // 收藏条码正文保留原始空格；仅过滤完全空白的无效条目。
        val texts = value.optJSONArray("texts").toStrings().filter { it.isNotBlank() }
        require(name.isNotBlank()) { "收藏文件缺少文件名" }
        require(texts.isNotEmpty()) { "收藏文件“$name”没有有效内容，无法导入" }
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
        require(backup.favorites.all { favorite -> favorite.texts.any { it.isNotBlank() } }) {
            "收藏文件存在空内容，无法导入"
        }
        var nextItemId = (existingItems.maxOfOrNull { it.id } ?: 0L) + 1L
        var nextGroupId = (existingGroups.maxOfOrNull { it.id } ?: 0L) + 1L
        val itemsById = existingItems.associateBy { it.id }
        val existingLinksByGroup = existingLinks.groupBy { it.groupId }
        // 收藏文件的身份只由文件夹路径和文件名决定；条码正文、格式和正文数量
        // 只用于确认文件有内容，绝不能参与判重。
        val existingKeys = existingGroups.map { FavoriteImportKey(normalizeFolder(it.folder), it.name.trim()) }.toSet()
        val items = mutableListOf<CodeItemEntity>(); val groups = mutableListOf<FavoriteGroupEntity>(); val links = mutableListOf<FavoriteGroupItemEntity>()
        backup.favorites.forEach { favorite ->
            val key = FavoriteImportKey(normalizeFolder(favorite.folder), favorite.name.trim())
            // 只与导入前已经存在的数据去重；备份内部即使存在同内容但不同 ID 的收藏，也必须全部保留。
            // 这样不会因为文件名/正文相同而静默丢失合法收藏。
            if (existingKeys.contains(key)) return@forEach
            val group = FavoriteGroupEntity(nextGroupId++, favorite.folder, favorite.name, favorite.time)
            val groupItems = favorite.texts.map { text -> CodeItemEntity(nextItemId++, text, toAndroidFormat(favorite.type), favorite.time, true, favorite.folder, false) }
            groups += group; items += groupItems; links += groupItems.map { FavoriteGroupItemEntity(group.id, it.id) }
        }
        val folderNames = (backup.folders + backup.favorites.map { it.folder }).filter { it.isNotBlank() }.distinct()
        return TransferEntities(items, groups, links, folderNames.map(::FavoriteFolderEntity))
    }

    private data class FavoriteImportKey(
        val folder: String,
        val name: String,
    )

    private fun normalizeFolder(folder: String): String =
        folder.trim().trim('/').let { if (it == "默认") "" else it }

    private fun splitFolder(folder: String): Pair<String, String> {
        val parts = folder.split('/').filter { it.isNotBlank() }
        require(parts.size <= 2) { "文件夹“$folder”超过两级，无法跨平台导出" }
        parts.forEach { require(it != "." && it != ".." && !it.contains(Char(92))) { "文件夹“$folder”格式无效" } }
        return (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
    }
    private fun favoriteJson(favorite: InterchangeFavorite) = JSONObject().apply {
        put("id", favorite.id); put("name", favorite.name); put("rootFolder", favorite.rootFolder); put("subFolder", favorite.subFolder)
        put("folder", favorite.folder); put("type", favorite.type); put("barcodeType", favorite.type); put("time", favorite.time); put("texts", JSONArray(favorite.texts))
    }
    fun favoriteZipPath(favorite: InterchangeFavorite): String = favoriteZipPath(favorite, mutableSetOf())

    private fun favoriteZipPath(favorite: InterchangeFavorite, usedPaths: MutableSet<String>): String {
        val baseName = safeFileName(favorite.name)
        val directory = listOf(FAVORITES_DIRECTORY, favorite.rootFolder, favorite.subFolder)
            .filter { it.isNotBlank() }
            .joinToString("/")
        var fileName = "$baseName.json"
        var candidate = listOf(directory, fileName).filter { it.isNotBlank() }.joinToString("/")
        if (!usedPaths.add(candidate)) {
            val id = favorite.id?.takeIf { it.isNotBlank() } ?: "duplicate"
            fileName = "$baseName-$id.json"
            candidate = listOf(directory, fileName).filter { it.isNotBlank() }.joinToString("/")
            var suffix = 2
            while (!usedPaths.add(candidate)) {
                fileName = "$baseName-$id-$suffix.json"
                candidate = listOf(directory, fileName).filter { it.isNotBlank() }.joinToString("/")
                suffix++
            }
        }
        return candidate
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .trimEnd('.')
        .ifBlank { "未命名收藏" }
    private fun ensureZipDirectories(zip: PortableZipWriter, directory: String, written: MutableSet<String>) {
        var path = ""
        directory.split('/').filter { it.isNotBlank() }.forEach { part ->
            path = if (path.isBlank()) part else "$path/$part"
            if (written.add(path)) writeZipEntry(zip, "$path/", ByteArray(0))
        }
    }

    private fun writeZipEntry(zip: PortableZipWriter, path: String, bytes: ByteArray) = zip.writeEntry(path, bytes)
    private fun toTransferType(format: String): String = when (format.trim().lowercase()) {
        "qr", "qr code" -> "qr"; "code128", "code 128-b" -> "code128"; "code39", "code 39" -> "code39"; "ean13", "ean-13" -> "ean13"; "ean8", "ean-8" -> "ean8"; "upca", "upc-a" -> "upca"; "itf14", "itf-14", "itf" -> "itf14"; "codabar" -> "codabar"; else -> error("不支持的条码格式：$format")
    }
    private fun toAndroidFormat(type: String): String = when (toTransferType(type)) {
        "qr" -> "QR Code"; "code128" -> "Code 128-B"; "code39" -> "Code 39"; "ean13" -> "EAN-13"; "ean8" -> "EAN-8"; "upca" -> "UPC-A"; "itf14" -> "ITF-14"; else -> "Codabar"
    }
    private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
}

/** 手写标准 ZIP 头和中央目录，避免 Android 历史 ZipOutputStream 偏移兼容问题。 */
private class PortableZipWriter(private val output: OutputStream) : Closeable {
    private data class Entry(val name: ByteArray, val crc: Long, val size: Long, val offset: Long, val directory: Boolean)
    private val entries = mutableListOf<Entry>()
    private var offset = 0L
    private var closed = false

    fun writeEntry(path: String, bytes: ByteArray) {
        check(!closed) { "ZIP writer is closed" }
        val name = path.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(bytes) }.value
        val entryOffset = offset
        writeU32(0x04034b50L); writeU16(20); writeU16(0x0800); writeU16(0)
        writeU16(0); writeU16(0); writeU32(crc); writeU32(bytes.size.toLong()); writeU32(bytes.size.toLong())
        writeU16(name.size); writeU16(0); writeBytes(name); writeBytes(bytes)
        entries += Entry(name, crc, bytes.size.toLong(), entryOffset, path.endsWith('/'))
    }

    override fun close() {
        if (closed) return
        closed = true
        val centralOffset = offset
        entries.forEach { entry ->
            writeU32(0x02014b50L); writeU16(20); writeU16(20); writeU16(0x0800); writeU16(0)
            writeU16(0); writeU16(0); writeU32(entry.crc); writeU32(entry.size); writeU32(entry.size)
            writeU16(entry.name.size); writeU16(0); writeU16(0); writeU16(0); writeU16(0)
            writeU32(if (entry.directory) 0x10L else 0L); writeU32(entry.offset); writeBytes(entry.name)
        }
        val centralSize = offset - centralOffset
        writeU32(0x06054b50L); writeU16(0); writeU16(0); writeU16(entries.size); writeU16(entries.size)
        writeU32(centralSize); writeU32(centralOffset); writeU16(0)
        output.flush()
    }

    private fun writeU16(value: Int) = writeU16(value.toLong())
    private fun writeU16(value: Long) {
        output.write((value and 0xff).toInt()); output.write(((value ushr 8) and 0xff).toInt()); offset += 2
    }
    private fun writeU32(value: Long) {
        output.write((value and 0xff).toInt()); output.write(((value ushr 8) and 0xff).toInt())
        output.write(((value ushr 16) and 0xff).toInt()); output.write(((value ushr 24) and 0xff).toInt()); offset += 4
    }
    private fun writeBytes(bytes: ByteArray) { output.write(bytes); offset += bytes.size }
}


