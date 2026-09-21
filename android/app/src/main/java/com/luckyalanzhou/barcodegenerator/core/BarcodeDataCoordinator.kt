package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import javax.inject.Inject
import com.luckyalanzhou.barcodegenerator.data.ExternalFavoritesStore
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup

/** BarcodeViewModel 的数据边界，集中管理 Repository、迁移和备份服务。 */
class BarcodeDataCoordinator @Inject constructor(
    internal val repository: BarcodeRepository,
    private val backupRepository: FavoritesBackupRepository,
    legacyBarcodeDataMigrator: BarcodeDataMigration,
    externalFavoritesStore: ExternalFavoritesStore,
) {
    val persistence = BarcodePersistenceCoordinator(repository, legacyBarcodeDataMigrator, externalFavoritesStore)

    suspend fun loadStartupGroupItemIds(groupId: Long) = repository.loadGroupItemIds(groupId)
    suspend fun loadItemsByIds(ids: List<Long>) = repository.loadItemsByIds(ids)
    suspend fun repairFromExternalFavorites() = persistence.repairFromExternalFavorites()
    suspend fun repairExternalFavorite(group: FavoriteGroup) = persistence.repairExternalFavorite(group)

    suspend fun inspectFavoriteImport(backup: InterchangeBackup): FavoritesImportConflictSummary = backupRepository.inspectImport(backup)
    suspend fun importFavorites(backup: InterchangeBackup, overwriteConflicts: Boolean = false) = backupRepository.import(backup, overwriteConflicts)
    suspend fun exportFavorites() = backupRepository.export()
    fun restoreFavorites(bytes: ByteArray) = backupRepository.restore(bytes)

    suspend fun syncExternalFavorites() {
        // The persistence coordinator owns the same serialized write boundary.
        persistence.syncExternalFavorites()
    }
}
