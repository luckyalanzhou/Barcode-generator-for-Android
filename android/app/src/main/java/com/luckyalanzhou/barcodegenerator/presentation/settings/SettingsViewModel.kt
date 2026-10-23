package com.luckyalanzhou.barcodegenerator.presentation.settings


import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.domain.SettingsMigration
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class SettingsUiState(
    val style: StyleSettings = StyleSettings(),
    val scheme: String = "system",
    val showFormat: Boolean = false,
    val ocrMask: Int = 0,
    val textSize: Float = 14f,
    val barHeight: Float = 55f,
    val barWidth: Float = 220f,
    val margin: Float = 4f,
    val saveFailed: Boolean = false,
)

private enum class StyleField {
    SHOW_TEXT,
    TEXT_POSITION,
    TEXT_SIZE,
    BAR_HEIGHT,
    BAR_WIDTH,
    MARGIN,
    SHOW_FORMAT,
    COLOR_SCHEME,
    REDUCE_MOTION,
    ENHANCE_CONTRAST,
}

/** 设置页状态与设置持久化之间的边界。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val legacySettingsMigrator: SettingsMigration,
    private val logger: AppLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var initialized = false
    private var currentStyle = StyleSettings()
    private val styleFieldsChangedBeforeLoad = mutableSetOf<StyleField>()
    private var ocrMaskChangedBeforeLoad: Int? = null
    private val writeFailureNotices = Channel<Unit>(Channel.BUFFERED)
    val persistenceFailures = writeFailureNotices.receiveAsFlow()

    /** 设置的唯一内存所有者；外部只能读取快照，写入必须经过本 ViewModel。 */
    internal val style: StyleSettings
        get() = currentStyle.copy()

    suspend fun loadPersistedState() {
        logger.record("settings", "load start", null)
        try {
            val (style, ocrMask) = withContext(Dispatchers.IO) {
                settingsRepository.load()
                legacySettingsMigrator.migrateIfNeeded()
                settingsRepository.loadStyle() to settingsRepository.getOcrConfusionReplacementMask()
            }
            // 回到调用方上下文后再发布状态，日志仅记录设置类型、不记录输入内容。
            initialize(style, ocrMask)
            logger.record("settings", "load success scheme=${style.colorScheme} reduceMotion=${style.reduceMotion} enhanceContrast=${style.enhanceContrast}", null)
        } catch (cancelled: CancellationException) {
            logger.record("settings", "load cancelled", null)
            throw cancelled
        } catch (error: Exception) {
            logger.record("settings", "load failed", error)
            throw error
        }
    }

    fun save(): Job = trackWrite("save") { settingsRepository.saveStyle(style) }

    fun setOcrMaskPersisted(mask: Int): Job {
        setOcrMask(mask)
        return trackWrite("ocr_mask_save") { settingsRepository.setOcrConfusionReplacementMask(mask) }
    }

    /** OCR 立即使用用户刚选择的值，不等待后台 DataStore 写入完成。 */
    fun getOcrMask(): Int = _uiState.value.ocrMask

    fun recordUpdateError(message: String): Job = trackWrite("update_error_save") { settingsRepository.setUpdateError(message) }

    /** Job 完成才报告持久化结果，不把“开始保存”误记为“保存成功”。 */
    private fun trackWrite(name: String, write: () -> Job): Job {
        val operation = System.nanoTime()
        logger.record("settings", "$name start operation=$operation", null)
        try {
            return write().also { job -> job.invokeOnCompletion { error ->
                val stage = if (error == null) "success" else if (error is CancellationException) "cancelled" else "failed"
                logger.record("settings", "$name $stage operation=$operation", error.takeUnless { it is CancellationException })
                if (error !is CancellationException) {
                    _uiState.update { it.copy(saveFailed = error != null) }
                    if (error != null) writeFailureNotices.trySend(Unit)
                }
            } }
        } catch (error: Exception) {
            logger.record("settings", "$name failed operation=$operation", error)
            throw error
        }
    }

    fun initialize(style: StyleSettings, ocrMask: Int) {
        if (initialized) return
        initialized = true
        val resolvedStyle = style.copy().apply {
            if (StyleField.SHOW_TEXT in styleFieldsChangedBeforeLoad) showText = currentStyle.showText
            if (StyleField.TEXT_POSITION in styleFieldsChangedBeforeLoad) textPosition = currentStyle.textPosition
            if (StyleField.TEXT_SIZE in styleFieldsChangedBeforeLoad) textSize = currentStyle.textSize
            if (StyleField.BAR_HEIGHT in styleFieldsChangedBeforeLoad) barHeight = currentStyle.barHeight
            if (StyleField.BAR_WIDTH in styleFieldsChangedBeforeLoad) barWidth = currentStyle.barWidth
            if (StyleField.MARGIN in styleFieldsChangedBeforeLoad) margin = currentStyle.margin
            if (StyleField.SHOW_FORMAT in styleFieldsChangedBeforeLoad) showFormat = currentStyle.showFormat
            if (StyleField.COLOR_SCHEME in styleFieldsChangedBeforeLoad) colorScheme = currentStyle.colorScheme
            if (StyleField.REDUCE_MOTION in styleFieldsChangedBeforeLoad) reduceMotion = currentStyle.reduceMotion
            if (StyleField.ENHANCE_CONTRAST in styleFieldsChangedBeforeLoad) enhanceContrast = currentStyle.enhanceContrast
        }
        val shouldPersistEarlyStyle = styleFieldsChangedBeforeLoad.isNotEmpty()
        val earlyOcrMask = ocrMaskChangedBeforeLoad
        currentStyle = resolvedStyle
        styleFieldsChangedBeforeLoad.clear()
        ocrMaskChangedBeforeLoad = null
        _uiState.value = SettingsUiState(
            style = currentStyle.copy(),
            scheme = currentStyle.colorScheme,
            showFormat = currentStyle.showFormat,
            ocrMask = earlyOcrMask ?: ocrMask,
            textSize = currentStyle.textSize,
            barHeight = currentStyle.barHeight.toFloat(),
            barWidth = currentStyle.barWidth,
            margin = currentStyle.margin.toFloat(),
        )
        if (shouldPersistEarlyStyle) save()
        if (earlyOcrMask != null) {
            trackWrite("ocr_mask_save_after_initial_load") {
                settingsRepository.setOcrConfusionReplacementMask(earlyOcrMask)
            }
        }
    }

    /** 用完整快照更新设置，避免 UI 逐字段修改可变 StyleSettings。 */
    fun updateStyle(style: StyleSettings) {
        if (!initialized) recordPreloadStyleChanges(currentStyle, style)
        currentStyle = style.copy()
        publishStyleToUi()
    }

    fun setOcrMask(value: Int) {
        if (!initialized) ocrMaskChangedBeforeLoad = value
        _uiState.update { it.copy(ocrMask = value) }
    }
    fun setTextSize(value: Float) = updateStyleValue { it.copy(textSize = value) }
    fun setBarHeight(value: Float) = updateStyleValue { it.copy(barHeight = value.toInt()) }
    fun setBarWidth(value: Float) = updateStyleValue { it.copy(barWidth = value) }
    fun setMargin(value: Float) = updateStyleValue { it.copy(margin = value.toInt()) }

    private fun updateStyleValue(transform: (StyleSettings) -> StyleSettings) {
        updateStyle(transform(currentStyle))
    }

    private fun recordPreloadStyleChanges(previous: StyleSettings, next: StyleSettings) {
        if (previous.showText != next.showText) styleFieldsChangedBeforeLoad += StyleField.SHOW_TEXT
        if (previous.textPosition != next.textPosition) styleFieldsChangedBeforeLoad += StyleField.TEXT_POSITION
        if (previous.textSize != next.textSize) styleFieldsChangedBeforeLoad += StyleField.TEXT_SIZE
        if (previous.barHeight != next.barHeight) styleFieldsChangedBeforeLoad += StyleField.BAR_HEIGHT
        if (previous.barWidth != next.barWidth) styleFieldsChangedBeforeLoad += StyleField.BAR_WIDTH
        if (previous.margin != next.margin) styleFieldsChangedBeforeLoad += StyleField.MARGIN
        if (previous.showFormat != next.showFormat) styleFieldsChangedBeforeLoad += StyleField.SHOW_FORMAT
        if (previous.colorScheme != next.colorScheme) styleFieldsChangedBeforeLoad += StyleField.COLOR_SCHEME
        if (previous.reduceMotion != next.reduceMotion) styleFieldsChangedBeforeLoad += StyleField.REDUCE_MOTION
        if (previous.enhanceContrast != next.enhanceContrast) styleFieldsChangedBeforeLoad += StyleField.ENHANCE_CONTRAST
    }

    private fun publishStyleToUi() {
        val style = currentStyle
        _uiState.update {
            it.copy(
                style = style.copy(),
                scheme = style.colorScheme,
                showFormat = style.showFormat,
                textSize = style.textSize,
                barHeight = style.barHeight.toFloat(),
                barWidth = style.barWidth,
                margin = style.margin.toFloat(),
            )
        }
    }
}
