package com.luckyalanzhou.barcodegenerator.domain

import java.io.OutputStream

/**
 * 收藏备份的领域边界。Android 的 Uri/ContentResolver 只应存在于 UI 的系统文件选择桥接层。
 */
interface FavoritesBackupRepository {
    suspend fun export(output: OutputStream)

    fun restore(bytes: ByteArray): InterchangeBackup

    suspend fun inspectImport(backup: InterchangeBackup): FavoritesImportConflictSummary

    suspend fun import(backup: InterchangeBackup, overwriteConflicts: Boolean = false): Pair<Int, Int>
}
