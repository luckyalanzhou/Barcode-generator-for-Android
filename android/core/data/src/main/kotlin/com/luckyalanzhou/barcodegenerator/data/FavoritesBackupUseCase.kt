package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportPlanner

import java.io.ByteArrayOutputStream

/** 收藏备份用例：协调 ZIP 格式与 Repository，UI 不再直接访问 DAO 或事务。 */
class FavoritesBackupUseCase(
    private val repository: BarcodeRepository,
    private val importPlanner: FavoritesImportPlanner = FavoritesImportPlanner(),
) : FavoritesBackupRepository {
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
        return importPlanner.inspectConflicts(existing.groups, backup.favorites)
    }

    override suspend fun import(backup: InterchangeBackup, overwriteConflicts: Boolean): Pair<Int, Int> {
        val existingSnapshot = repository.loadSnapshot()
        val existing = existingSnapshot.toTransferEntities()
        val plan = importPlanner.plan(existingSnapshot.groups, backup.favorites, overwriteConflicts)
        val replacedGroupIds = plan.replacedGroupIds
        // Build the complete post-import snapshot without deleting durable data first.
        // Room applies the replacement and import together, so any constraint/write failure
        // rolls back and leaves every old favorite intact.
        val retainedExisting = if (replacedGroupIds.isEmpty()) existing else existing.copy(
            groups = existing.groups.filterNot { it.id in replacedGroupIds },
            links = existing.links.filterNot { it.groupId in replacedGroupIds },
        )

        val effectiveBackup = backup.copy(favorites = plan.favoritesToImport)
        val transfer = FavoritesTransferManager.appendEntities(
            effectiveBackup,
            retainedExisting.items,
            retainedExisting.groups,
            retainedExisting.links,
        )
        repository.commitFavoriteImport(transfer.toSnapshot(), replacedGroupIds)
        return transfer.items.size to transfer.groups.size
    }
}
