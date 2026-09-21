package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
 * Room remains the runtime index; this is the uninstall-safe mirror in shared storage.
 * New data is stored automatically under Documents/Barcode Generator/Favorites.
 * The old SAF URI is retained only as a read fallback for existing installations.
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
        private const val SHARED_ROOT = "Documents/Barcode Generator/Favorites/"
    }

    fun configuredRootUri(): Uri? = settingsStore.getFavoritesRootUri()?.let(Uri::parse)

    suspend fun isSyncPending(): Boolean = settingsStore.isExternalFavoritesSyncPending()

    suspend fun markSyncPending(pending: Boolean) {
        settingsStore.setExternalFavoritesSyncPending(pending).join()
    }

    suspend fun isRestoreRequired(): Boolean = settingsStore.isFavoritesRestoreRequired()

    suspend fun markRestoreRequired(required: Boolean) {
        settingsStore.setFavoritesRestoreRequired(required).join()
    }

    fun mirror(snapshot: BarcodeSnapshot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mirrorShared(snapshot)
            return
        }
        mirrorLegacy(snapshot)
    }

    private fun mirrorShared(snapshot: BarcodeSnapshot) {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("$SHARED_ROOT%"),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex)
                if (name == MARKER || name.endsWith(EXTENSION)) {
                    resolver.delete(Uri.withAppendedPath(collection, cursor.getLong(idIndex).toString()), null, null)
                }
            }
        } ?: error("Cannot query shared favorites directory")
        val folders = (snapshot.folders + snapshot.groups.map { it.folder })
            .filter { it.isNotBlank() }
            .distinct()
        insertSharedMarker(resolver, collection, SHARED_ROOT)
        folders.forEach { folder ->
            val relativePath = SHARED_ROOT + folder.split('/').filter { it.isNotBlank() }
                .joinToString("/") { safeSegment(it) } + "/"
            insertSharedMarker(resolver, collection, relativePath)
        }
        val itemsById = snapshot.items.associateBy { it.id }
        snapshot.groups.forEach { group ->
            val folder = group.folder.split('/').filter { it.isNotBlank() }.joinToString("/") { safeSegment(it) }
            val relativePath = SHARED_ROOT + folder.takeIf { it.isNotBlank() }?.plus('/') .orEmpty()
            val fileName = safeFileName(group.name) + EXTENSION
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val target = resolver.insert(collection, values) ?: error("Cannot create shared favorite: $fileName")
            resolver.openOutputStream(target)?.use { output ->
                DataOutputStream(BufferedOutputStream(output)).use { writeGroup(it, group, itemsById) }
            } ?: error("Cannot open shared favorite: $fileName")
        }
    }

    private fun insertSharedMarker(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
    ) {
        resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MARKER)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }) ?: error("Cannot create shared favorites marker")
    }

    private fun mirrorLegacy(snapshot: BarcodeSnapshot) {
        val root = configuredRootUri()?.let { DocumentFile.fromTreeUri(context, it) } ?: return
        if (!root.isDirectory || !root.canWrite()) return
        val managedRoot = root.findFile(MARKER) != null
        if (!managedRoot) root.createFile("text/plain", MARKER)
            ?: error("Cannot create legacy favorites marker")
        if (managedRoot) deleteManagedFiles(root)
        snapshot.groups.forEach { group ->
            val folder = ensureFolder(root, group.folder)
            val itemsById = snapshot.items.associateBy { it.id }
            val fileName = safeFileName(group.name) + EXTENSION
            folder.findFile(fileName)?.delete()
            val target = folder.createFile("application/octet-stream", fileName)
                ?: error("Cannot create legacy favorite: $fileName")
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                DataOutputStream(BufferedOutputStream(output)).use { writeGroup(it, group, itemsById) }
            } ?: error("Cannot open legacy favorite: $fileName")
        }
    }

    fun readSnapshot(): BarcodeSnapshot? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readSharedSnapshot()?.let { return it }
        }
        return readLegacySnapshot()
    }

    /** Reads only the selected favorite document instead of scanning every favorite file. */
    fun readFavorite(group: FavoriteGroup): Pair<FavoriteGroup, List<CodeItem>>? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) readSharedFavorite(group)
        else readLegacyFavorite(group)
    }.getOrNull()

    private fun readSharedFavorite(group: FavoriteGroup): Pair<FavoriteGroup, List<CodeItem>>? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folderPath = group.folder.split('/').filter { it.isNotBlank() }
            .joinToString("/") { safeSegment(it) }
        val relativePath = if (folderPath.isBlank()) SHARED_ROOT else "$SHARED_ROOT$folderPath/"
        val fileName = safeFileName(group.name) + EXTENSION
        return resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(relativePath, fileName),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            readGroup(
                MediaStoreFile(
                    Uri.withAppendedPath(collection, cursor.getLong(idIndex).toString()),
                    cursor.getString(nameIndex),
                    cursor.getString(pathIndex),
                ),
                group.folder,
            )
        }
    }

    private fun readLegacyFavorite(group: FavoriteGroup): Pair<FavoriteGroup, List<CodeItem>>? {
        val root = configuredRootUri()?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        if (!root.isDirectory) return null
        var directory = root
        group.folder.split('/').filter { it.isNotBlank() }.forEach { segment ->
            directory = directory.findFile(safeSegment(segment))?.takeIf { it.isDirectory } ?: return null
        }
        val file = directory.findFile(safeFileName(group.name) + EXTENSION) ?: return null
        return readGroup(file, group.folder)
    }

    fun ensureSharedMirror(snapshot: BarcodeSnapshot) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && snapshot.groups.isNotEmpty() && !hasSharedManagedFiles()) {
            mirrorShared(snapshot)
        }
    }

    private fun hasSharedManagedFiles(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$SHARED_ROOT%", "%$EXTENSION"),
            null,
        )?.use { it.moveToFirst() } == true
    }

    private fun readSharedSnapshot(): BarcodeSnapshot? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val groups = mutableListOf<FavoriteGroup>()
        val items = mutableListOf<CodeItem>()
        val links = mutableListOf<FavoriteGroupItem>()
        val folders = mutableSetOf<String>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$SHARED_ROOT%", "%$EXTENSION"),
            "${MediaStore.MediaColumns.RELATIVE_PATH} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val file = MediaStoreFile(
                    Uri.withAppendedPath(collection, cursor.getLong(idIndex).toString()),
                    cursor.getString(nameIndex),
                    cursor.getString(pathIndex),
                )
                val folder = file.relativePath.removePrefix(SHARED_ROOT).trimEnd('/')
                runCatching { readGroup(file, folder) }.onSuccess { (group, groupItems) ->
                    groups += group
                    items += groupItems
                    links += groupItems.map { FavoriteGroupItem(group.id, it.id) }
                    if (folder.isNotBlank()) folders += folder
                }
            }
        }
        return if (groups.isEmpty()) null else BarcodeSnapshot(items, groups, links, folders.toList())
    }

    private fun readLegacySnapshot(): BarcodeSnapshot? {
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

    private fun readGroup(file: MediaStoreFile, folderPath: String): Pair<FavoriteGroup, List<CodeItem>> {
        val items = mutableListOf<CodeItem>()
        val group: FavoriteGroup
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            DataInputStream(BufferedInputStream(input)).use { source ->
                check(source.readUTF() == MAGIC) { "Unsupported favorite file" }
                check(source.readInt() == VERSION) { "Unsupported favorite version" }
                val id = source.readLong()
                val name = source.readUTF().ifBlank { file.name.removeSuffix(EXTENSION) }
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

    private data class MediaStoreFile(val uri: Uri, val name: String, val relativePath: String)
}
