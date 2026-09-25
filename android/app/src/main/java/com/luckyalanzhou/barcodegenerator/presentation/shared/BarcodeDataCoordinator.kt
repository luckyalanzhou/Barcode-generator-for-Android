package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.InterchangeBackup
import com.luckyalanzhou.barcodegenerator.domain.FavoritesImportConflictSummary
import java.io.OutputStream
import javax.inject.Inject

/** 各功能 ViewModel 共用的数据边界，集中管理 Repository、迁移和备份服务。 */
class BarcodeDataCoordinator @Inject constructor(
    internal val repository: BarcodeRepository,
    private val backupRepository: FavoritesBackupRepository,
    val persistence: BarcodePersistenceCoordinator,
) {
    suspend fun loadStartupGroupItemIds(groupId: Long) = repository.loadGroupItemIds(groupId)
    suspend fun loadItemsByIds(ids: List<Long>) = repository.loadItemsByIds(ids)

    suspend fun inspectFavoriteImport(backup: InterchangeBackup): FavoritesImportConflictSummary = backupRepository.inspectImport(backup)
    suspend fun importFavorites(backup: InterchangeBackup, overwriteConflicts: Boolean = false): Pair<Int, Int> {
        val result = backupRepository.import(backup, overwriteConflicts)
        return result
    }
    suspend fun exportFavorites(output: OutputStream) = backupRepository.export(output)
    fun restoreFavorites(bytes: ByteArray) = backupRepository.restore(bytes)

}
