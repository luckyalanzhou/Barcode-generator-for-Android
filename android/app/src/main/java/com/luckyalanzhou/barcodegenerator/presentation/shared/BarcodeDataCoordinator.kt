package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import javax.inject.Inject

/** BarcodeViewModel 的数据边界，集中管理 Repository、迁移和备份服务。 */
class BarcodeDataCoordinator @Inject constructor(
    internal val repository: BarcodeRepository,
    private val backupRepository: FavoritesBackupRepository,
    legacyBarcodeDataMigrator: BarcodeDataMigration,
) {
    val persistence = BarcodePersistenceCoordinator(repository, legacyBarcodeDataMigrator)

    suspend fun loadStartupGroupItemIds(groupId: Long) = repository.loadGroupItemIds(groupId)
    suspend fun loadItemsByIds(ids: List<Long>) = repository.loadItemsByIds(ids)

    suspend fun inspectFavoriteImport(backup: InterchangeBackup): FavoritesImportConflictSummary = backupRepository.inspectImport(backup)
    suspend fun importFavorites(backup: InterchangeBackup, overwriteConflicts: Boolean = false): Pair<Int, Int> {
        val result = backupRepository.import(backup, overwriteConflicts)
        return result
    }
    suspend fun exportFavorites() = backupRepository.export()
    fun restoreFavorites(bytes: ByteArray) = backupRepository.restore(bytes)

}
