package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.data.LegacyBarcodeDataMigrator
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import javax.inject.Inject

/** BarcodeViewModel 的数据边界，集中管理 Repository、迁移和备份服务。 */
class BarcodeDataCoordinator @Inject constructor(
    internal val repository: BarcodeRepository,
    private val backupRepository: FavoritesBackupRepository,
    legacyBarcodeDataMigrator: LegacyBarcodeDataMigrator,
) {
    val persistence = BarcodePersistenceCoordinator(repository, legacyBarcodeDataMigrator)

    suspend fun loadStartupGroupItemIds(groupId: Long) = repository.loadGroupItemIds(groupId)
    suspend fun loadItemsByIds(ids: List<Long>) = repository.loadItemsByIds(ids)

    suspend fun importFavorites(backup: InterchangeBackup) = backupRepository.import(backup)
    suspend fun exportFavorites() = backupRepository.export()
    fun restoreFavorites(bytes: ByteArray) = backupRepository.restore(bytes)
}
