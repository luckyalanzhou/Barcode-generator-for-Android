package com.luckyalanzhou.barcodegenerator.ui.feature.generate

/** 生成输入组件所需的展示数据，由 app composition 层从 Presentation 状态映射。 */
internal data class GenerateInputError(
    val rowIndex: Int,
    val message: String,
    val attempt: Long = 0,
)
