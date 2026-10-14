package com.luckyalanzhou.barcodegenerator.ui.feature.results

import com.luckyalanzhou.barcodegenerator.domain.CodeItem

/** Display-only input projected by the app route from presentation state. */
internal data class ResultsContentState(
    val items: List<CodeItem>,
    val isRestoring: Boolean,
    val restoreFailed: Boolean,
    val hasSavedFavoriteFile: Boolean,
)
