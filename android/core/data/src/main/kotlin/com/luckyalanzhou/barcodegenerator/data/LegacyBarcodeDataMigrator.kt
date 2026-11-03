package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.LegacyBarcodeData
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 把旧版条码数据从 SharedPreferences 迁移到 Room，并清理已消费的旧键。 */
class LegacyBarcodeDataMigrator(
    context: Context,
    private val barcodeRepository: BarcodeRepository,
) : BarcodeDataMigration {
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun migrateIfNeeded() {
        val failure = withContext(Dispatchers.IO) {
            try {
                migrateOnIo()
                null
            } catch (error: Throwable) {
                error
            }
        }
        // 作为结果跨 dispatcher 传递后再抛出，保留仓储异常与取消异常的原对象。
        failure?.let { throw it }
    }

    private suspend fun migrateOnIo() {
        val migrationHandled = try {
            legacyPrefs.getBoolean(MIGRATION_KEY, false) || legacyPrefs.getBoolean(MIGRATION_SKIPPED_KEY, false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Unable to read legacy barcode migration state", error)
            false
        }
        if (migrationHandled) return

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
        } catch (error: JSONException) {
            Log.e(TAG, "Legacy barcode data is invalid; keeping the original preferences", error)
            // 格式错误不等于 Room 已有数据跳过；保留重试机会，避免修正旧值后仍永久跳过。
            return
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Legacy barcode data is invalid; keeping the original preferences", error)
            return
        } catch (error: Exception) {
            Log.e(TAG, "Legacy barcode migration failed; keeping the original preferences", error)
            throw error
        }
        if (!migrated) {
            // Room 已有数据时保留旧值供恢复，但只跳过一次，避免用户清空 Room 后旧记录复活。
            val skipped = legacyPrefs.edit().putBoolean(MIGRATION_SKIPPED_KEY, true).commit()
            if (!skipped) {
                val error = IllegalStateException("Could not commit skipped legacy barcode migration state")
                Log.e(TAG, error.message, error)
                throw error
            }
            return
        }

        val committed = legacyPrefs.edit()
            .putBoolean(MIGRATION_KEY, true)
            .remove("items")
            .remove("favorite_groups")
            .remove("favorite_folders")
            .remove("next_item_id")
            .remove("next_group_id")
            .commit()
        if (!committed) {
            val error = IllegalStateException("Could not commit legacy barcode preference cleanup")
            Log.e(TAG, error.message, error)
            throw error
        }
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
        const val MIGRATION_SKIPPED_KEY = "room_data_migration_skipped"
    }
}
