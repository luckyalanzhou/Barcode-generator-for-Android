package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.domain.OcrCorrectionMask
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.domain.UPDATE_FAILURE_SUMMARY
import com.luckyalanzhou.barcodegenerator.domain.awaitSettingsCommit

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

internal val Context.settingsDataStore by preferencesDataStore(name = "barcode_settings")

/**
 * DataStore 设置实现：统一读取和保存条码样式、外观选项、OCR 与更新状态。
 * 所有异步写入按顺序排队，防止快速拖动滑块时旧值晚到并覆盖最新设置。
 */
class SettingsStore(private val context: Context) : SettingsRepository {
    companion object {
        internal val SHOW_TEXT = booleanPreferencesKey("style_show_text")
        internal val TEXT_POSITION = stringPreferencesKey("style_text_position")
        internal val TEXT_SIZE = floatPreferencesKey("style_text_size")
        internal val BAR_HEIGHT = intPreferencesKey("style_bar_height")
        internal val BAR_WIDTH = floatPreferencesKey("style_bar_width")
        internal val MARGIN = intPreferencesKey("style_margin")
        internal val COLOR_SCHEME = stringPreferencesKey("style_color_scheme")
        internal val OCR_CONFUSION_REPLACEMENT_MASK = intPreferencesKey("ocr_confusion_replacement_mask")
        internal val LAST_UPDATE_ERROR = stringPreferencesKey("last_update_error")
        internal val SETTINGS_MIGRATED = booleanPreferencesKey("settings_datastore_migrated")
    }

    // 所有设置写入串行执行，避免滑块连续拖动时旧快照晚到并覆盖最新值。
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeQueue = SettingsWriteQueue(scope)
    @Volatile private var cachedValues: Preferences = emptyPreferences()
    @Volatile private var ocrConfusionReplacementMask = 0

    override suspend fun load() {
        val storedValues = context.settingsDataStore.data.first()
        val removedFormatKey = booleanPreferencesKey("style_show_format")
        val reduceMotionKey = booleanPreferencesKey("appearance_reduce_motion")
        val enhanceContrastKey = booleanPreferencesKey("appearance_enhance_contrast")
        val loadedValues = if (storedValues.contains(removedFormatKey) ||
            storedValues.contains(reduceMotionKey) || storedValues.contains(enhanceContrastKey)
        ) {
            context.settingsDataStore.edit { preferences ->
                preferences.remove(removedFormatKey)
                preferences.remove(reduceMotionKey)
                preferences.remove(enhanceContrastKey)
            }
        } else storedValues
        val persistedError = loadedValues[LAST_UPDATE_ERROR]
        if (!persistedError.isNullOrBlank() && persistedError != UPDATE_FAILURE_SUMMARY) {
            write { it[LAST_UPDATE_ERROR] = UPDATE_FAILURE_SUMMARY }.awaitSettingsCommit()
        } else {
            cachedValues = loadedValues
        }
        ocrConfusionReplacementMask = cachedValues[OCR_CONFUSION_REPLACEMENT_MASK] ?: 0
    }

    internal fun <T> get(key: Preferences.Key<T>, default: T): T = cachedValues[key] ?: default

    /**
     * 读取完整条码样式快照。样式字段必须与 [saveStyle] 对称，避免应用重启后只恢复尺寸类设置。
     */
    override fun loadStyle(): StyleSettings = decodeStyleSettings(cachedValues)

    override fun setUpdateError(error: String): Job = write { it[LAST_UPDATE_ERROR] = UPDATE_FAILURE_SUMMARY }

    override fun getOcrConfusionReplacementMask(): Int = ocrConfusionReplacementMask

    override fun setOcrConfusionReplacementMask(mask: Int): Job {
        return write { it[OCR_CONFUSION_REPLACEMENT_MASK] = mask }
    }

    override fun saveStyle(style: StyleSettings): Job = write { it.writeStyleSettings(style) }

    fun markMigrated(): Job = write { it[SETTINGS_MIGRATED] = true }

    private fun write(block: (MutablePreferences) -> Unit): Job = writeQueue.enqueue {
        val committed = context.settingsDataStore.edit { preferences -> block(preferences) }
        cachedValues = committed
        ocrConfusionReplacementMask = committed[OCR_CONFUSION_REPLACEMENT_MASK] ?: 0
    }
}

internal fun decodeStyleSettings(values: Preferences): StyleSettings = with(SettingsStore) {
    StyleSettings(
        showText = values[SHOW_TEXT] ?: true,
        textPosition = values[TEXT_POSITION] ?: "bottom",
        textSize = (values[TEXT_SIZE] ?: 14f).coerceIn(10f, 24f),
        barHeight = (values[BAR_HEIGHT] ?: 55).coerceIn(30, 80),
        barWidth = (values[BAR_WIDTH] ?: 220f).coerceIn(120f, 300f),
        margin = (values[MARGIN] ?: 4).coerceIn(0, 10),
        colorScheme = values[COLOR_SCHEME] ?: "system",
    )
}

internal fun MutablePreferences.writeStyleSettings(style: StyleSettings) = with(SettingsStore) {
    // 清理早期版本遗留的用户条码颜色；新版本颜色完全由外观模式决定。
    remove(intPreferencesKey("style_bar_color"))
    remove(intPreferencesKey("style_bg_color"))
    this@writeStyleSettings[SHOW_TEXT] = style.showText
    this@writeStyleSettings[TEXT_POSITION] = style.textPosition
    this@writeStyleSettings[TEXT_SIZE] = style.textSize
    this@writeStyleSettings[BAR_HEIGHT] = style.barHeight
    this@writeStyleSettings[BAR_WIDTH] = style.barWidth
    this@writeStyleSettings[MARGIN] = style.margin
    this@writeStyleSettings[COLOR_SCHEME] = style.colorScheme
    remove(booleanPreferencesKey("style_show_format"))
    remove(booleanPreferencesKey("appearance_reduce_transparency"))
    remove(booleanPreferencesKey("appearance_reduce_motion"))
    remove(booleanPreferencesKey("appearance_enhance_contrast"))
    Unit
}
