package com.luckyalanzhou.barcodegenerator.ui.feature.results

import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/** 应用路由从 Presentation 状态映射得到、仅供界面显示的输入数据。 */
internal data class ResultsContentState(
    val items: List<CodeItem>,
    val isRestoring: Boolean,
    val restoreFailed: Boolean,
    val hasSavedFavoriteFile: Boolean,
)
