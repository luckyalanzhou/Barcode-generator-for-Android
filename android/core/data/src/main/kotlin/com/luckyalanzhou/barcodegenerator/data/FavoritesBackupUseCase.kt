package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.InterchangeFavorite
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

import java.io.ByteArrayOutputStream

/** 收藏备份用例：协调 ZIP 格式与 Repository，UI 不再直接访问 DAO 或事务。 */
class FavoritesBackupUseCase(private val repository: BarcodeRepository) : FavoritesBackupRepository {
    override suspend fun export(): ByteArray {
        val entities = repository.loadSnapshot().toTransferEntities()
        return ByteArrayOutputStream().use { output ->
            FavoritesTransferManager.export(output, entities.groups, entities.links, entities.items, entities.folders)
            output.toByteArray()
        }
    }

    override fun restore(bytes: ByteArray): InterchangeBackup = FavoritesTransferManager.restore(bytes)

    override suspend fun inspectImport(backup: InterchangeBackup): FavoritesImportConflictSummary {
        val existing = repository.loadSnapshot()
        val existingFiles = existing.groups.map(::fileKey).toSet()
        return FavoritesImportConflictSummary(
            fileKeys = backup.favorites.map(::fileKey)
                .filter { it in existingFiles }.distinct(),
        )
    }

    override suspend fun import(backup: InterchangeBackup, overwriteConflicts: Boolean): Pair<Int, Int> {
        val existing = repository.loadSnapshot().toTransferEntities()
        val existingFileKeys = existing.groups.map(::fileKey).toSet()
        val incomingFileKeys = backup.favorites.map(::fileKey).toSet()
        val conflictingGroupIds = existing.groups
            .filter { fileKey(it) in incomingFileKeys }
            .map { it.id }
        val replacedGroupIds = if (overwriteConflicts) conflictingGroupIds.toSet() else emptySet()
        // Build the complete post-import snapshot without deleting durable data first.
        // Room applies the replacement and import together, so any constraint/write failure
        // rolls back and leaves every old favorite intact.
        val retainedExisting = if (replacedGroupIds.isEmpty()) existing else existing.copy(
            groups = existing.groups.filterNot { it.id in replacedGroupIds },
            links = existing.links.filterNot { it.groupId in replacedGroupIds },
        )

        val deduplicatedFavorites = backup.favorites
            .asReversed()
            .distinctBy(::fileKey)
            .asReversed()
        val effectiveBackup = if (overwriteConflicts) backup.copy(
            favorites = deduplicatedFavorites,
        ) else backup.copy(
            favorites = deduplicatedFavorites.filterNot { fileKey(it) in existingFileKeys },
        )
        val transfer = FavoritesTransferManager.appendEntities(
            effectiveBackup,
            retainedExisting.items,
            retainedExisting.groups,
            retainedExisting.links,
        )
        repository.commitFavoriteImport(transfer.toSnapshot(), replacedGroupIds)
        return transfer.items.size to transfer.groups.size
    }

    private fun fileKey(group: FavoriteGroupEntity): String =
        fileKey(group.folder, group.name)

    private fun fileKey(group: FavoriteGroup): String =
        fileKey(group.folder, group.name)

    private fun fileKey(favorite: InterchangeFavorite): String =
        fileKey(favorite.folder, favorite.name)

    private fun fileKey(folder: String, name: String): String =
        "${folder.trim().trim('/').let { if (it == "默认") "" else it }}\u0000${name.trim()}"
}
