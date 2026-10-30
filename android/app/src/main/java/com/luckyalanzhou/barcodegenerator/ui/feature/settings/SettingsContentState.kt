package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import com.luckyalanzhou.barcodegenerator.domain.StyleSettings

/** 应用路由从设置状态映射得到、仅供界面显示的输入数据。 */
internal data class SettingsContentState(
    val style: StyleSettings,
    val ocrMask: Int,
)
