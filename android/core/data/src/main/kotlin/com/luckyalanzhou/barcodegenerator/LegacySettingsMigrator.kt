package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.graphics.Color

/** 旧版 SharedPreferences 到 DataStore 的一次性迁移器，属于 Data 层兼容职责。 */
class LegacySettingsMigrator(
    context: Context,
    private val settingsStore: SettingsStore,
) {
    private val legacyPrefs by lazy { context.getSharedPreferences("barcode_app", Context.MODE_PRIVATE) }

    suspend fun migrateIfNeeded(): StyleSettings? {
        if (settingsStore.get(SettingsStore.SETTINGS_MIGRATED, false)) return null
        val style = StyleSettings(
            barColor = legacyPrefs.getInt("style_bar_color", Color.BLACK),
            bgColor = legacyPrefs.getInt("style_bg_color", Color.WHITE),
            showText = legacyPrefs.getBoolean("style_show_text", true),
            textPosition = legacyPrefs.getString("style_text_position", "bottom") ?: "bottom",
            textSize = legacyPrefs.getFloat("style_text_size", 14f),
            barHeight = legacyPrefs.getInt("style_bar_height", 55),
            barWidth = legacyPrefs.getFloat("style_bar_width", 220f),
            margin = legacyPrefs.getInt("style_margin", 4),
            showFormat = legacyPrefs.getBoolean("style_show_format", false),
            colorScheme = legacyPrefs.getString("style_color_scheme", "system") ?: "system",
        )
        settingsStore.saveStyle(style).join()
        legacyPrefs.getString("last_update_error", "")?.takeIf { it.isNotBlank() }?.let {
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
}
