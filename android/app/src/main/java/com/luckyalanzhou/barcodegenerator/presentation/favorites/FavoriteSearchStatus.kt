package com.luckyalanzhou.barcodegenerator.presentation.favorites

/** 用查询标识区分新旧搜索，避免把上一条查询的结果误显示为当前空结果。 */
data class FavoriteSearchStatus(
    val query: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
    val paginationFailed: Boolean = false,
)
