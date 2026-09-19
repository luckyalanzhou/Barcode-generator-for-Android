package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.graphics.Color
import android.util.Log

/** 旧版 SharedPreferences 到 DataStore 的一次性迁移器，属于 Data 层兼容职责。 */
class LegacySettingsMigrator(
    context: Context,
    private val settingsStore: SettingsStore,
) {
    private val legacyPrefs by lazy { context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE) }

    suspend fun migrateIfNeeded(): StyleSettings? {
        if (settingsStore.get(SettingsStore.SETTINGS_MIGRATED, false)) return null
        val style = StyleSettings(
            barColor = readInt("style_bar_color", Color.BLACK),
            bgColor = readInt("style_bg_color", Color.WHITE),
            showText = readBoolean("style_show_text", true),
            textPosition = readString("style_text_position", "bottom"),
            textSize = readFloat("style_text_size", 14f),
            barHeight = readInt("style_bar_height", 55),
            barWidth = readFloat("style_bar_width", 220f),
            margin = readInt("style_margin", 4),
            showFormat = readBoolean("style_show_format", false),
            colorScheme = readString("style_color_scheme", "system"),
        )
        settingsStore.saveStyle(style).join()
        readString("last_update_error", "").takeIf { it.isNotBlank() }?.let {
            settingsStore.setUpdateError(it).join()
        }
        settingsStore.markMigrated().join()
        legacyPrefs.edit()
            .remove("style_bar_color").remove("style_bg_color").remove("style_show_text")
            .remove("style_text_position").remove("style_text_size").remove("style_bar_height")
            .remove("style_bar_width").remove("style_margin").remove("style_show_format")
            .remove("style_transparent_background").remove("style_color_scheme")
            .remove("last_update_error").apply()
        return style
    }

    private fun readInt(key: String, default: Int): Int = runCatching { legacyPrefs.getInt(key, default) }
        .onFailure { Log.w(TAG, "Ignoring invalid legacy int: $key", it) }.getOrDefault(default)

    private fun readFloat(key: String, default: Float): Float = runCatching { legacyPrefs.getFloat(key, default) }
        .onFailure { Log.w(TAG, "Ignoring invalid legacy float: $key", it) }.getOrDefault(default)

    private fun readBoolean(key: String, default: Boolean): Boolean = runCatching { legacyPrefs.getBoolean(key, default) }
        .onFailure { Log.w(TAG, "Ignoring invalid legacy boolean: $key", it) }.getOrDefault(default)

    private fun readString(key: String, default: String): String = runCatching {
        legacyPrefs.getString(key, default) ?: default
    }.onFailure { Log.w(TAG, "Ignoring invalid legacy string: $key", it) }.getOrDefault(default)

    private companion object { const val TAG = "LegacySettingsMigrator" }
}
