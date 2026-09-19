package com.luckyalanzhou.barcodegenerator.domain

/**
 * 收藏备份的领域边界。Android 的 Uri/ContentResolver 只应存在于 UI 的系统文件选择桥接层。
 */
interface FavoritesBackupRepository {
    suspend fun export(): ByteArray

    fun restore(bytes: ByteArray): InterchangeBackup

    suspend fun import(backup: InterchangeBackup): Pair<Int, Int>
}
