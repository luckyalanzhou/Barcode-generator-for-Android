package com.luckyalanzhou.barcodegenerator.presentation.settings

import com.luckyalanzhou.barcodegenerator.*

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.StyleSettings
import com.luckyalanzhou.barcodegenerator.domain.SettingsMigration

data class SettingsUiState(
    val style: StyleSettings = StyleSettings(),
    val scheme: String = "system",
    val showFormat: Boolean = false,
    val ocrMask: Int = 0,
    val textSize: Float = 14f,
    val barHeight: Float = 55f,
    val barWidth: Float = 220f,
    val margin: Float = 4f,
)

/** 设置页状态与设置持久化之间的边界。 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val legacySettingsMigrator: SettingsMigration,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var initialized = false
    private var currentStyle = StyleSettings()

    /** 设置的唯一内存所有者；外部只能读取快照，写入必须经过本 ViewModel。 */
    internal val style: StyleSettings
        get() = currentStyle.copy()

    suspend fun loadPersistedState() {
        settingsRepository.load()
        legacySettingsMigrator.migrateIfNeeded()
        initialize(settingsRepository.loadStyle(), settingsRepository.getOcrConfusionReplacementMask())
    }

    fun save(): Job = settingsRepository.saveStyle(style)

    fun setOcrMaskPersisted(mask: Int): Job {
        setOcrMask(mask)
        return settingsRepository.setOcrConfusionReplacementMask(mask)
    }

    fun getOcrMask(): Int = settingsRepository.getOcrConfusionReplacementMask()

    fun recordUpdateError(message: String): Job = settingsRepository.setUpdateError(message)

    fun initialize(style: StyleSettings, ocrMask: Int) {
        if (initialized) return
        initialized = true
        currentStyle = style.copy()
        _uiState.value = SettingsUiState(
            style = currentStyle.copy(),
            scheme = style.colorScheme,
            showFormat = style.showFormat,
            ocrMask = ocrMask,
            textSize = style.textSize,
            barHeight = style.barHeight.toFloat(),
            barWidth = style.barWidth,
            margin = style.margin.toFloat(),
        )
    }

    /** 用完整快照更新设置，避免 UI 逐字段修改可变 StyleSettings。 */
    fun updateStyle(style: StyleSettings) {
        currentStyle = style.copy()
        publishStyleToUi()
    }

    fun setScheme(value: String) = updateStyleValue { it.copy(colorScheme = value) }
    fun setShowFormat(value: Boolean) = updateStyleValue { it.copy(showFormat = value) }
    fun setOcrMask(value: Int) = _uiState.update { it.copy(ocrMask = value) }
    fun setTextSize(value: Float) = updateStyleValue { it.copy(textSize = value) }
    fun setBarHeight(value: Float) = updateStyleValue { it.copy(barHeight = value.toInt()) }
    fun setBarWidth(value: Float) = updateStyleValue { it.copy(barWidth = value) }
    fun setMargin(value: Float) = updateStyleValue { it.copy(margin = value.toInt()) }

    private fun updateStyleValue(transform: (StyleSettings) -> StyleSettings) {
        currentStyle = transform(currentStyle).copy()
        publishStyleToUi()
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
