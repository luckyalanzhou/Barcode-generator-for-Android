package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository

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

    override suspend fun import(backup: InterchangeBackup): Pair<Int, Int> {
        val existing = repository.loadSnapshot().toTransferEntities()
        val transfer = FavoritesTransferManager.appendEntities(backup, existing.items, existing.groups, existing.links)
        repository.appendSnapshot(transfer.toSnapshot())
        return transfer.items.size to transfer.groups.size
    }
}

