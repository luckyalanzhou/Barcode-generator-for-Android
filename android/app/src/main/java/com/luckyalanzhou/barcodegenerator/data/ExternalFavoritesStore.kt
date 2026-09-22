package com.luckyalanzhou.barcodegenerator.data

import android.content.Context
import android.content.ContentValues
import android.os.Build
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
    data class FavoriteDocument(
        val group: FavoriteGroup,
        val items: List<CodeItem>,
        /** File-system modification time in milliseconds since epoch. */
        val modifiedAt: Long,
    )

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

    /** Creates the fixed shared-storage root on first app startup. */
    fun ensureManagedRoot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasSharedRootMarker()) return
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        insertSharedMarker(resolver, collection, SHARED_ROOT)
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
        val itemsById = snapshot.items.associateBy { it.id }
        val groupsWithContent = snapshot.groups.mapNotNull { group ->
            val groupItems = group.itemIds.mapNotNull(itemsById::get).filter { it.text.isNotBlank() }
            group.takeIf { groupItems.isNotEmpty() }?.let { it to groupItems }
        }
        val groupsWithoutContent = snapshot.groups.filter { group ->
            group.itemIds.none { itemId -> itemsById[itemId]?.text?.isNotBlank() == true }
        }
        val validKeys = groupsWithContent.map { (group, _) -> sharedFileKey(group) }.toSet()
        val protectedEmptyKeys = groupsWithoutContent.map(::sharedFileKey).toSet()
        val existingFiles = mutableMapOf<Pair<String, String>, MutableList<Uri>>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf("$SHARED_ROOT%", "%$EXTENSION"),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val key = cursor.getString(pathIndex) to cursor.getString(nameIndex)
                existingFiles.getOrPut(key) { mutableListOf() } +=
                    Uri.withAppendedPath(collection, cursor.getLong(idIndex).toString())
            }
            existingFiles.forEach { (key, uris) ->
                if (key !in validKeys && key !in protectedEmptyKeys) {
                    uris.forEach { resolver.delete(it, null, null) }
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
        groupsWithContent.forEach { (group, groupItems) ->
            val relativePath = sharedFolderPath(group.folder)
            val fileName = safeFileName(group.name) + EXTENSION
            val key = relativePath to fileName
            val existing = existingFiles[key].orEmpty()
            val target = existing.firstOrNull() ?: resolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }) ?: error("Cannot create shared favorite: $fileName")
            existing.drop(1).forEach { resolver.delete(it, null, null) }
            resolver.openOutputStream(target, "wt")?.use { output ->
                DataOutputStream(BufferedOutputStream(output)).use { destination ->
                    writeGroup(destination, group.copy(itemIds = groupItems.map { it.id }.toMutableList()), groupItems.associateBy { it.id })
                }
            } ?: error("Cannot open shared favorite: $fileName")
        }
    }

    private fun sharedFolderPath(folder: String): String {
        val safeFolder = folder.split('/').filter { it.isNotBlank() }.joinToString("/") { safeSegment(it) }
        return SHARED_ROOT + safeFolder.takeIf { it.isNotBlank() }?.plus('/') .orEmpty()
    }

    private fun sharedFileKey(group: FavoriteGroup): Pair<String, String> =
        sharedFolderPath(group.folder) to (safeFileName(group.name) + EXTENSION)

    private fun mirrorLegacy(snapshot: BarcodeSnapshot) {
        val root = configuredRootUri()?.let { DocumentFile.fromTreeUri(context, it) } ?: return
        if (!root.isDirectory || !root.canWrite()) return
        val managedRoot = root.findFile(MARKER) != null
        if (!managedRoot) root.createFile("text/plain", MARKER)
            ?: error("Cannot create legacy favorites marker")
        val itemsById = snapshot.items.associateBy { it.id }
        val groupsWithContent = snapshot.groups.mapNotNull { group ->
            val groupItems = group.itemIds.mapNotNull(itemsById::get).filter { it.text.isNotBlank() }
            group.takeIf { groupItems.isNotEmpty() }?.let { it to groupItems }
        }
        val groupsWithoutContent = snapshot.groups.filter { group ->
            group.itemIds.none { itemId -> itemsById[itemId]?.text?.isNotBlank() == true }
        }
        val validKeys = groupsWithContent.map { legacyFileKey(it.first) }.toSet()
        val protectedEmptyKeys = groupsWithoutContent.map(::legacyFileKey).toSet()
        if (managedRoot) pruneLegacyFiles(root, "", validKeys, protectedEmptyKeys)
        groupsWithContent.forEach { (group, groupItems) ->
            val folder = ensureFolder(root, group.folder)
            val fileName = safeFileName(group.name) + EXTENSION
            folder.findFile(fileName)?.delete()
            val target = folder.createFile("application/octet-stream", fileName)
                ?: error("Cannot create legacy favorite: $fileName")
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                DataOutputStream(BufferedOutputStream(output)).use { destination ->
                    writeGroup(destination, group.copy(itemIds = groupItems.map { it.id }.toMutableList()), groupItems.associateBy { it.id })
                }
            } ?: error("Cannot open legacy favorite: $fileName")
        }
    }

    private fun legacyFileKey(group: FavoriteGroup): Pair<String, String> =
        group.folder.split('/').filter { it.isNotBlank() }.joinToString("/") { safeSegment(it) } to
            (safeFileName(group.name) + EXTENSION)

    private fun pruneLegacyFiles(
        directory: DocumentFile,
        folderPath: String,
        validKeys: Set<Pair<String, String>>,
        protectedEmptyKeys: Set<Pair<String, String>>,
    ) {
        directory.listFiles().forEach { file ->
            if (file.isDirectory) {
                val childPath = listOf(folderPath, safeSegment(file.name.orEmpty())).filter { it.isNotBlank() }.joinToString("/")
                pruneLegacyFiles(file, childPath, validKeys, protectedEmptyKeys)
            } else if (file.name?.endsWith(EXTENSION) == true) {
                val key = folderPath to file.name.orEmpty()
                if (key !in validKeys && key !in protectedEmptyKeys) file.delete()
            }
        }
    }

    private fun insertSharedMarker(
        resolver: android.content.ContentResolver,
        collection: Uri,
        relativePath: String,
    ) {
        val exists = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(relativePath, MARKER),
            null,
        )?.use { it.moveToFirst() } == true
        if (exists) return
        resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, MARKER)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }) ?: error("Cannot create shared favorites marker")
    }


    fun readSnapshot(): BarcodeSnapshot? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            readSharedSnapshot()?.let { return it }
        }
        return readLegacySnapshot()
    }

    /** Reads only the selected favorite document instead of scanning every favorite file. */
    fun readFavorite(group: FavoriteGroup): FavoriteDocument? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) readSharedFavorite(group)
        else readLegacyFavorite(group)
    }.getOrNull()

    private fun readSharedFavorite(group: FavoriteGroup): FavoriteDocument? {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val folderPath = group.folder.split('/').filter { it.isNotBlank() }
            .joinToString("/") { safeSegment(it) }
        val relativePath = if (folderPath.isBlank()) SHARED_ROOT else "$SHARED_ROOT$folderPath/"
        val fileName = safeFileName(group.name) + EXTENSION
        return resolver.query(
            collection,
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DATE_MODIFIED,
            ),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(relativePath, fileName),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val (loadedGroup, loadedItems) = readGroup(
                MediaStoreFile(
                    Uri.withAppendedPath(collection, cursor.getLong(idIndex).toString()),
                    cursor.getString(nameIndex),
                    cursor.getString(pathIndex),
                ),
                group.folder,
            )
            val modifiedAtSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
            FavoriteDocument(loadedGroup, loadedItems, modifiedAtSeconds * 1_000L)
        }
    }

    private fun readLegacyFavorite(group: FavoriteGroup): FavoriteDocument? {
        val root = configuredRootUri()?.let { DocumentFile.fromTreeUri(context, it) } ?: return null
        if (!root.isDirectory) return null
        var directory = root
        group.folder.split('/').filter { it.isNotBlank() }.forEach { segment ->
            directory = directory.findFile(safeSegment(segment))?.takeIf { it.isDirectory } ?: return null
        }
        val file = directory.findFile(safeFileName(group.name) + EXTENSION) ?: return null
        val (loadedGroup, loadedItems) = readGroup(file, group.folder)
        return FavoriteDocument(loadedGroup, loadedItems, file.lastModified())
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

    private fun hasSharedRootMarker(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(SHARED_ROOT, MARKER),
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

    private fun safeFileName(value: String): String = safeSegment(value).ifBlank { "未命名收藏" }

    private fun safeSegment(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .trimEnd('.')
        .ifBlank { "未命名" }

    private data class MediaStoreFile(val uri: Uri, val name: String, val relativePath: String)
}
