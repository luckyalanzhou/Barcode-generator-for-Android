package com.luckyalanzhou.barcodegenerator

import android.content.ContentResolver
import android.net.Uri

/** 收藏备份用例：协调 ZIP 格式与 Repository，UI 不再直接访问 DAO 或事务。 */
class FavoritesBackupUseCase(private val repository: BarcodeRepository) {
    suspend fun export(resolver: ContentResolver, uri: Uri) {
        val entities = repository.loadTransferEntities()
        FavoritesTransferManager.export(resolver, uri, entities.groups, entities.links, entities.items, entities.folders)
    }

    fun restore(resolver: ContentResolver, uri: Uri): InterchangeBackup =
        FavoritesTransferManager.restore(resolver, uri)

    suspend fun import(backup: InterchangeBackup): Pair<Int, Int> {
        val existing = repository.loadTransferEntities()
        val transfer = FavoritesTransferManager.appendEntities(backup, existing.items, existing.groups, existing.links)
        repository.appendTransfer(transfer)
        return transfer.items.size to transfer.groups.size
    }
}


