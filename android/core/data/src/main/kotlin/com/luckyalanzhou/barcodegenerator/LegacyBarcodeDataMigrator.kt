package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/** 把旧版条码数据从 SharedPreferences 迁移到 Room，并清理已消费的旧键。 */
class LegacyBarcodeDataMigrator(
    context: Context,
    private val barcodeRepository: BarcodeRepository,
) {
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun migrateIfNeeded() {
        if (runCatching { legacyPrefs.getBoolean(MIGRATION_KEY, false) }.getOrDefault(false)) return

        // 旧版本同一个 SharedPreferences 文件中混用了多种类型；单个坏键不能阻断启动。
        val legacy = LegacyBarcodeData(
            itemsJson = readString("items"),
            groupsJson = readString("favorite_groups"),
            folders = readStringSet("favorite_folders"),
        )
        // Room 写入失败时保留旧键，下一次启动仍可重试，避免静默丢失旧数据。
        barcodeRepository.migrateLegacyDataIfNeeded(legacy)
        legacyPrefs.edit()
            .putBoolean(MIGRATION_KEY, true)
            .remove("items")
            .remove("favorite_groups")
            .remove("favorite_folders")
            .remove("next_item_id")
            .remove("next_group_id")
            .apply()
    }

    private fun readString(key: String): String? = runCatching { legacyPrefs.getString(key, null) }
        .onFailure { Log.w(TAG, "Ignoring invalid legacy string: $key", it) }
        .getOrNull()

    private fun readStringSet(key: String): Set<String> = runCatching {
        legacyPrefs.getStringSet(key, emptySet()).orEmpty()
    }.onFailure { Log.w(TAG, "Ignoring invalid legacy string set: $key", it) }
        .getOrDefault(emptySet())

    private companion object {
        const val TAG = "LegacyBarcodeDataMigrator"
        const val PREFERENCES_NAME = "barcode_app"
        const val MIGRATION_KEY = "room_data_migrated"
    }
}
