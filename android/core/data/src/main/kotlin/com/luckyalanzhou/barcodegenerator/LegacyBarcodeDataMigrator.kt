package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.content.SharedPreferences

/** 把旧版条码数据从 SharedPreferences 迁移到 Room，并清理已消费的旧键。 */
class LegacyBarcodeDataMigrator(
    context: Context,
    private val barcodeRepository: BarcodeRepository,
) {
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    suspend fun migrateIfNeeded() {
        if (legacyPrefs.getBoolean(MIGRATION_KEY, false)) return

        barcodeRepository.migrateLegacyDataIfNeeded(
            LegacyBarcodeData(
                itemsJson = legacyPrefs.getString("items", null),
                groupsJson = legacyPrefs.getString("favorite_groups", null),
                folders = legacyPrefs.getStringSet("favorite_folders", emptySet()).orEmpty(),
            )
        )
        legacyPrefs.edit()
            .putBoolean(MIGRATION_KEY, true)
            .remove("items")
            .remove("favorite_groups")
            .remove("favorite_folders")
            .remove("next_item_id")
            .remove("next_group_id")
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "barcode_app"
        const val MIGRATION_KEY = "room_data_migrated"
    }
}
