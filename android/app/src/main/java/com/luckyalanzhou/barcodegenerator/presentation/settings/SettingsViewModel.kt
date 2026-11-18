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
import com.luckyalanzhou.barcodegenerator.presentation.shared.recordSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class SettingsUiState(
    val style: StyleSettings = StyleSettings(),
    val scheme: String = "system",
    val liquidGlassEnabled: Boolean = true,
    val bottomTabLabelsVisible: Boolean = true,
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
    COLOR_SCHEME,
}

private data class PersistedSettingsState(
    val style: StyleSettings,
    val ocrMask: Int,
    val liquidGlassEnabled: Boolean,
    val bottomTabLabelsVisible: Boolean,
)

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
    private var liquidGlassChangedBeforeLoad: Boolean? = null
    private var bottomTabLabelsChangedBeforeLoad: Boolean? = null
    private val writeFailureNotices = Channel<Unit>(Channel.BUFFERED)
    val persistenceFailures = writeFailureNotices.receiveAsFlow()

    /** 设置的唯一内存所有者；外部只能读取快照，写入必须经过本 ViewModel。 */
    internal val style: StyleSettings
        get() = currentStyle.copy()

    suspend fun loadPersistedState() {
        try {
            logger.recordSafely("settings", "load start")
            val (style, ocrMask, liquidGlassEnabled, bottomTabLabelsVisible) = withContext(Dispatchers.IO) {
                settingsRepository.load()
                legacySettingsMigrator.migrateIfNeeded()
                PersistedSettingsState(
                    style = settingsRepository.loadStyle(),
                    ocrMask = settingsRepository.getOcrConfusionReplacementMask(),
                    liquidGlassEnabled = settingsRepository.getLiquidGlassEnabled(),
                    bottomTabLabelsVisible = settingsRepository.getBottomTabLabelsVisible(),
                )
            }
            // 回到调用方上下文后再发布状态，日志仅记录设置类型、不记录输入内容。
            initialize(style, ocrMask, liquidGlassEnabled, bottomTabLabelsVisible)
            logger.recordSafely(
                "settings",
                "load success scheme=${style.colorScheme}",
            )
        } catch (cancelled: CancellationException) {
            logger.recordSafely("settings", "load cancelled", primaryError = cancelled)
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.recordSafely("settings", "load interrupted", interrupted, interrupted)
            throw interrupted
        } catch (error: Exception) {
            logger.recordSafely("settings", "load failed", error, error)
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
        try {
            logger.recordSafely("settings", "$name start operation=$operation")
            return write().also { job -> job.invokeOnCompletion { error ->
                val stage = if (error == null) "success" else if (error is CancellationException) "cancelled" else "failed"
                if (error !is CancellationException) {
                    _uiState.update { it.copy(saveFailed = error != null) }
                    if (error != null) writeFailureNotices.trySend(Unit)
                }
                logger.recordSafely(
                    "settings",
                    "$name $stage operation=$operation",
                    error.takeUnless { it is CancellationException },
                    error,
                )
            } }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.recordSafely("settings", "$name interrupted operation=$operation", interrupted, interrupted)
            throw interrupted
        } catch (error: Exception) {
            logger.recordSafely("settings", "$name failed operation=$operation", error, error)
            throw error
        }
    }

    fun initialize(
        style: StyleSettings,
        ocrMask: Int,
        liquidGlassEnabled: Boolean = true,
        bottomTabLabelsVisible: Boolean = true,
    ) {
        if (initialized) return
        initialized = true
        val resolvedStyle = style.copy().apply {
            if (StyleField.SHOW_TEXT in styleFieldsChangedBeforeLoad) showText = currentStyle.showText
            if (StyleField.TEXT_POSITION in styleFieldsChangedBeforeLoad) textPosition = currentStyle.textPosition
            if (StyleField.TEXT_SIZE in styleFieldsChangedBeforeLoad) textSize = currentStyle.textSize
            if (StyleField.BAR_HEIGHT in styleFieldsChangedBeforeLoad) barHeight = currentStyle.barHeight
            if (StyleField.BAR_WIDTH in styleFieldsChangedBeforeLoad) barWidth = currentStyle.barWidth
            if (StyleField.MARGIN in styleFieldsChangedBeforeLoad) margin = currentStyle.margin
            if (StyleField.COLOR_SCHEME in styleFieldsChangedBeforeLoad) colorScheme = currentStyle.colorScheme
        }
        val shouldPersistEarlyStyle = styleFieldsChangedBeforeLoad.isNotEmpty()
        val earlyOcrMask = ocrMaskChangedBeforeLoad
        val earlyLiquidGlassEnabled = liquidGlassChangedBeforeLoad
        val earlyBottomTabLabelsVisible = bottomTabLabelsChangedBeforeLoad
        val resolvedLiquidGlassEnabled = earlyLiquidGlassEnabled ?: liquidGlassEnabled
        val resolvedBottomTabLabelsVisible = earlyBottomTabLabelsVisible ?: bottomTabLabelsVisible
        currentStyle = resolvedStyle
        styleFieldsChangedBeforeLoad.clear()
        ocrMaskChangedBeforeLoad = null
        liquidGlassChangedBeforeLoad = null
        bottomTabLabelsChangedBeforeLoad = null
        _uiState.value = SettingsUiState(
            style = currentStyle.copy(),
            scheme = currentStyle.colorScheme,
            liquidGlassEnabled = resolvedLiquidGlassEnabled,
            bottomTabLabelsVisible = resolvedBottomTabLabelsVisible,
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
        if (earlyLiquidGlassEnabled != null) {
            trackWrite("liquid_glass_save_after_initial_load") {
                settingsRepository.setLiquidGlassEnabled(earlyLiquidGlassEnabled)
            }
        }
        if (earlyBottomTabLabelsVisible != null) {
            trackWrite("bottom_tab_labels_save_after_initial_load") {
                settingsRepository.setBottomTabLabelsVisible(resolvedBottomTabLabelsVisible)
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

    fun setLiquidGlassEnabled(enabled: Boolean) {
        if (!initialized) {
            liquidGlassChangedBeforeLoad = enabled
            _uiState.update { it.copy(liquidGlassEnabled = enabled) }
            return
        }
        if (_uiState.value.liquidGlassEnabled == enabled) return
        _uiState.update { it.copy(liquidGlassEnabled = enabled) }
        trackWrite("liquid_glass_save") { settingsRepository.setLiquidGlassEnabled(enabled) }
    }

    fun setBottomTabLabelsVisible(visible: Boolean) {
        if (!initialized) {
            bottomTabLabelsChangedBeforeLoad = visible
            _uiState.update { it.copy(bottomTabLabelsVisible = visible) }
            return
        }
        if (_uiState.value.bottomTabLabelsVisible == visible) return
        _uiState.update { it.copy(bottomTabLabelsVisible = visible) }
        trackWrite("bottom_tab_labels_save") {
            settingsRepository.setBottomTabLabelsVisible(visible)
        }
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
        if (previous.colorScheme != next.colorScheme) styleFieldsChangedBeforeLoad += StyleField.COLOR_SCHEME
    }

    private fun publishStyleToUi() {
        val style = currentStyle
        _uiState.update {
            it.copy(
                style = style.copy(),
                scheme = style.colorScheme,
                textSize = style.textSize,
                barHeight = style.barHeight.toFloat(),
                barWidth = style.barWidth,
                margin = style.margin.toFloat(),
            )
        }
    }
}
