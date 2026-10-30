package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.domain.SettingsMigration
import com.luckyalanzhou.barcodegenerator.domain.awaitSettingsCommit

import android.content.Context
import android.util.Log
import java.util.concurrent.CancellationException

/** 旧版 SharedPreferences 到 DataStore 的一次性迁移器，属于 Data 层兼容职责。 */
class LegacySettingsMigrator(
    context: Context,
    private val settingsStore: SettingsStore,
) : SettingsMigration {
    private val legacyPrefs by lazy { context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE) }

    override suspend fun migrateIfNeeded(): StyleSettings? {
        if (settingsStore.get(SettingsStore.SETTINGS_MIGRATED, false)) return null
        val style = StyleSettings(
            showText = readBoolean("style_show_text", true),
            textPosition = readString("style_text_position", "bottom"),
            textSize = readFloat("style_text_size", 14f),
            barHeight = readInt("style_bar_height", 55),
            barWidth = readFloat("style_bar_width", 220f),
            margin = readInt("style_margin", 4),
            colorScheme = readString("style_color_scheme", "system"),
        )
        settingsStore.saveStyle(style).awaitSettingsCommit()
        readString("last_update_error", "").takeIf { it.isNotBlank() }?.let {
            settingsStore.setUpdateError(it).awaitSettingsCommit()
        }
        settingsStore.markMigrated().awaitSettingsCommit()
        legacyPrefs.edit()
            .remove("style_bar_color").remove("style_bg_color").remove("style_show_text")
            .remove("style_text_position").remove("style_text_size").remove("style_bar_height")
            .remove("style_bar_width").remove("style_margin").remove("style_show_format")
            .remove("style_transparent_background").remove("style_color_scheme")
            .remove("last_update_error").apply()
        return style
    }

    private fun readInt(key: String, default: Int): Int =
        readLegacyValue(key, "int", default) { legacyPrefs.getInt(key, default) }

    private fun readFloat(key: String, default: Float): Float =
        readLegacyValue(key, "float", default) { legacyPrefs.getFloat(key, default) }

    private fun readBoolean(key: String, default: Boolean): Boolean =
        readLegacyValue(key, "boolean", default) { legacyPrefs.getBoolean(key, default) }

    private fun readString(key: String, default: String): String =
        readLegacyValue(key, "string", default) { legacyPrefs.getString(key, default) ?: default }

    private inline fun <T> readLegacyValue(
        key: String,
        type: String,
        default: T,
        read: () -> T,
    ): T = try {
        read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        Log.w(TAG, "Ignoring invalid legacy $type: $key", exception)
        default
    }

    private companion object { const val TAG = "LegacySettingsMigrator" }
}
