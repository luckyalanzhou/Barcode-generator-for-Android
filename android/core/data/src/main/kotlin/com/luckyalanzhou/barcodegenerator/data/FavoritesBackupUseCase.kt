package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary

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
        val existingFolders = (existing.folders + existing.groups.map { it.folder }).filter { it.isNotBlank() }.toSet()
        val existingFiles = existing.groups.map { "${it.folder}\u0000${it.name}" }.toSet()
        return FavoritesImportConflictSummary(
            folderPaths = backup.folders.filter { it.isNotBlank() && it in existingFolders }.distinct(),
            fileKeys = backup.favorites.map { "${it.folder}\u0000${it.name}" }
                .filter { it in existingFiles }.distinct(),
        )
    }

    override suspend fun import(backup: InterchangeBackup, overwriteConflicts: Boolean): Pair<Int, Int> {
        var existing = repository.loadSnapshot().toTransferEntities()
        val existingFileKeys = existing.groups.map { "${it.folder}\u0000${it.name}" }.toSet()
        val incomingFileKeys = backup.favorites.map { "${it.folder}\u0000${it.name}" }.toSet()
        val conflictingGroupIds = existing.groups
            .filter { "${it.folder}\u0000${it.name}" in incomingFileKeys }
            .map { it.id }

        if (overwriteConflicts && conflictingGroupIds.isNotEmpty()) {
            repository.deleteFavoriteGroups(conflictingGroupIds)
            existing = repository.loadSnapshot().toTransferEntities()
        }

        val effectiveBackup = if (overwriteConflicts) backup else backup.copy(
            favorites = backup.favorites.filterNot { "${it.folder}\u0000${it.name}" in existingFileKeys },
        )
        val transfer = FavoritesTransferManager.appendEntities(effectiveBackup, existing.items, existing.groups, existing.links)
        repository.appendSnapshot(transfer.toSnapshot())
        return transfer.items.size to transfer.groups.size
    }
}
