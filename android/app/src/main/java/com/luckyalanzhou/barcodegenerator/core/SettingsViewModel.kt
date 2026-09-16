package com.luckyalanzhou.barcodegenerator

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsUiState(
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
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var initialized = false
    private var currentStyle = StyleSettings()

    /** 设置对象的唯一内存所有者；页面通过 Activity 兼容访问器读取。 */
    internal val style: StyleSettings
        get() = currentStyle

    fun initialize(style: StyleSettings, ocrMask: Int) {
        if (initialized) return
        initialized = true
        currentStyle = style.copy()
        _uiState.value = SettingsUiState(
            scheme = style.colorScheme,
            showFormat = style.showFormat,
            ocrMask = ocrMask,
            textSize = style.textSize,
            barHeight = style.barHeight.toFloat(),
            barWidth = style.barWidth,
            margin = style.margin.toFloat(),
        )
    }

    fun setScheme(value: String) = _uiState.update { it.copy(scheme = value) }
    fun setShowFormat(value: Boolean) = _uiState.update { it.copy(showFormat = value) }
    fun setOcrMask(value: Int) = _uiState.update { it.copy(ocrMask = value) }
    fun setTextSize(value: Float) = _uiState.update { it.copy(textSize = value) }
    fun setBarHeight(value: Float) = _uiState.update { it.copy(barHeight = value) }
    fun setBarWidth(value: Float) = _uiState.update { it.copy(barWidth = value) }
    fun setMargin(value: Float) = _uiState.update { it.copy(margin = value) }
}
