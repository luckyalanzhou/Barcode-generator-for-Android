package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.LegacyBarcodeData
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.CancellationException

/** 把旧版条码数据从 SharedPreferences 迁移到 Room，并清理已消费的旧键。 */
class LegacyBarcodeDataMigrator(
    context: Context,
    private val barcodeRepository: BarcodeRepository,
) : BarcodeDataMigration {
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun migrateIfNeeded() {
        val migrationComplete = try {
            legacyPrefs.getBoolean(MIGRATION_KEY, false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Unable to read legacy barcode migration state", error)
            false
        }
        if (migrationComplete) return

        val legacy = try {
            LegacyBarcodeData(
                itemsJson = readString("items"),
                groupsJson = readString("favorite_groups"),
                folders = readStringSet("favorite_folders"),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Unable to read legacy barcode data; keeping the original preferences", error)
            return
        }

        val migrated = try {
            barcodeRepository.migrateLegacyDataIfNeeded(legacy)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Legacy barcode migration failed; keeping the original preferences", error)
            false
        }
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
        return try {
            legacyPrefs.getString(key, null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Invalid legacy string: $key", error)
            throw IllegalStateException("Invalid legacy preference: $key", error)
        }
    }

    private fun readStringSet(key: String): Set<String> {
        if (!legacyPrefs.contains(key)) return emptySet()
        return try {
            legacyPrefs.getStringSet(key, emptySet()).orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Invalid legacy string set: $key", error)
            throw IllegalStateException("Invalid legacy preference: $key", error)
        }
    }

    private companion object {
        const val TAG = "LegacyBarcodeDataMigrator"
        const val PREFERENCES_NAME = "barcode_app"
        const val MIGRATION_KEY = "room_data_migrated"
    }
}
