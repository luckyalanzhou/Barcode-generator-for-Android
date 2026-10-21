package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.LegacyBarcodeData
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/** 把旧版条码数据从 SharedPreferences 迁移到 Room，并清理已消费的旧键。 */
class LegacyBarcodeDataMigrator(
    context: Context,
    private val barcodeRepository: BarcodeRepository,
) : BarcodeDataMigration {
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun migrateIfNeeded() {
        if (runCatching { legacyPrefs.getBoolean(MIGRATION_KEY, false) }.getOrDefault(false)) return

        val legacy = runCatching {
            LegacyBarcodeData(
                itemsJson = readString("items"),
                groupsJson = readString("favorite_groups"),
                folders = readStringSet("favorite_folders"),
            )
        }.onFailure { Log.e(TAG, "Unable to read legacy barcode data; keeping the original preferences", it) }
            .getOrNull() ?: return

        val migrated = runCatching { barcodeRepository.migrateLegacyDataIfNeeded(legacy) }
            .onFailure { Log.e(TAG, "Legacy barcode migration failed; keeping the original preferences", it) }
            .getOrDefault(false)
        if (!migrated) return

        val committed = legacyPrefs.edit()
            .putBoolean(MIGRATION_KEY, true)
            .remove("items")
            .remove("favorite_groups")
            .remove("favorite_folders")
            .remove("next_item_id")
            .remove("next_group_id")
            .commit()
        if (!committed) Log.e(TAG, "Could not commit legacy barcode preference cleanup")
    }

    private fun readString(key: String): String? {
        if (!legacyPrefs.contains(key)) return null
        return runCatching { legacyPrefs.getString(key, null) }
            .onFailure { Log.w(TAG, "Invalid legacy string: $key", it) }
            .getOrElse { throw IllegalStateException("Invalid legacy preference: $key", it) }
    }

    private fun readStringSet(key: String): Set<String> {
        if (!legacyPrefs.contains(key)) return emptySet()
        return runCatching { legacyPrefs.getStringSet(key, emptySet()).orEmpty() }
            .onFailure { Log.w(TAG, "Invalid legacy string set: $key", it) }
            .getOrElse { throw IllegalStateException("Invalid legacy preference: $key", it) }
    }

    private companion object {
        const val TAG = "LegacyBarcodeDataMigrator"
        const val PREFERENCES_NAME = "barcode_app"
        const val MIGRATION_KEY = "room_data_migrated"
    }
}
