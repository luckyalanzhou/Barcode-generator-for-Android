package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.luckyalanzhou.barcodegenerator.domain.BarcodeSnapshot
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroupItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * User-owned favorite files stored below an ACTION_OPEN_DOCUMENT_TREE directory.
 * Room remains the runtime index; these files are the uninstall-safe mirror.
 */
class ExternalFavoritesStore(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    companion object {
        private const val MAGIC = "BGEN-FAVORITE"
        private const val VERSION = 1
        private const val EXTENSION = ".bcode"
        private const val MARKER = ".barcode-generator-root"
    }

    fun configuredRootUri(): Uri? = settingsStore.getFavoritesRootUri()?.let(Uri::parse)

    fun mirror(snapshot: BarcodeSnapshot) {
        val root = configuredRootUri()?.let { DocumentFile.fromTreeUri(context, it) } ?: return
        if (!root.isDirectory || !root.canWrite()) return
        runCatching {
            val managedRoot = root.findFile(MARKER) != null
            if (!managedRoot) root.createFile("text/plain", MARKER)
            if (managedRoot) deleteManagedFiles(root)
            snapshot.groups.forEach { group ->
                val folder = ensureFolder(root, group.folder)
                val itemsById = snapshot.items.associateBy { it.id }
                val fileName = safeFileName(group.name) + EXTENSION
                folder.findFile(fileName)?.delete()
                val target = folder.createFile("application/octet-stream", fileName) ?: return@forEach
                context.contentResolver.openOutputStream(target.uri)?.use { output ->
                    DataOutputStream(BufferedOutputStream(output)).use { writeGroup(it, group, itemsById) }
                }
            }
        }
    }

    fun readSnapshot(): BarcodeSnapshot? {
        val root = configuredRootUri()?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        if (!root.isDirectory) return null
        val groups = mutableListOf<FavoriteGroup>()
        val items = mutableListOf<CodeItem>()
        val links = mutableListOf<FavoriteGroupItem>()
        val folders = mutableSetOf<String>()
        scan(root, "", groups, items, links, folders)
        return if (groups.isEmpty()) null else BarcodeSnapshot(items, groups, links, folders.toList())
    }

    private fun scan(
        directory: DocumentFile,
        folderPath: String,
        groups: MutableList<FavoriteGroup>,
        items: MutableList<CodeItem>,
        links: MutableList<FavoriteGroupItem>,
        folders: MutableSet<String>,
    ) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                val path = listOf(folderPath, file.name.orEmpty()).filter { it.isNotBlank() }.joinToString("/")
                folders += path
                scan(file, path, groups, items, links, folders)
            } else if (file.isFile && file.name?.endsWith(EXTENSION) == true && file.name != MARKER) {
                runCatching { readGroup(file, folderPath) }.onSuccess { (group, groupItems) ->
                    groups += group
                    folders += folderPath
                    items += groupItems
                    links += groupItems.map { FavoriteGroupItem(group.id, it.id) }
                }
            }
        }
    }

    private fun writeGroup(output: DataOutputStream, group: FavoriteGroup, items: Map<Long, CodeItem>) {
        output.writeUTF(MAGIC)
        output.writeInt(VERSION)
        output.writeLong(group.id)
        output.writeUTF(group.name)
        output.writeUTF(group.folder)
        output.writeLong(group.savedAt)
        val groupItems = group.itemIds.mapNotNull(items::get)
        output.writeInt(groupItems.size)
        groupItems.forEach { item ->
            output.writeLong(item.id)
            output.writeUTF(item.text)
            output.writeUTF(item.format)
            output.writeLong(item.createdAt)
        }
    }

    private fun readGroup(file: DocumentFile, folderPath: String): Pair<FavoriteGroup, List<CodeItem>> {
        val items = mutableListOf<CodeItem>()
        val group: FavoriteGroup
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            DataInputStream(BufferedInputStream(input)).use { source ->
                check(source.readUTF() == MAGIC) { "Unsupported favorite file" }
                check(source.readInt() == VERSION) { "Unsupported favorite version" }
                val id = source.readLong()
                val name = source.readUTF().ifBlank { file.name.orEmpty().removeSuffix(EXTENSION) }
                val storedFolder = source.readUTF().ifBlank { folderPath }
                val savedAt = source.readLong()
                repeat(source.readInt().coerceIn(0, 1000)) {
                    items += CodeItem(source.readLong(), source.readUTF(), source.readUTF(), source.readLong(), true, storedFolder, false)
                }
                group = FavoriteGroup(id, storedFolder, name, savedAt, items.map { it.id }.toMutableList())
            }
        } ?: error("Cannot read favorite file")
        return group to items
    }

    private fun ensureFolder(root: DocumentFile, path: String): DocumentFile {
        var current = root
        path.split('/').filter { it.isNotBlank() }.forEach { segment ->
            val name = safeSegment(segment)
            current = current.findFile(name)?.takeIf { it.isDirectory } ?: current.createDirectory(name)
                ?: error("Cannot create favorite folder: $name")
        }
        return current
    }

    private fun deleteManagedFiles(directory: DocumentFile) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) deleteManagedFiles(file)
            else if (file.name?.endsWith(EXTENSION) == true) file.delete()
        }
    }

    private fun safeFileName(value: String): String = safeSegment(value).ifBlank { "未命名收藏" }

    private fun safeSegment(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .trimEnd('.')
        .ifBlank { "未命名" }
}
