package com.luckyalanzhou.barcodegenerator.presentation.favorites

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 保存搜索框显示的原始文字及其标准化查询形式。 */
internal class FavoriteSearchQueryState {
    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    val normalizedQuery: String
        get() = mutableQuery.value.trim().lowercase(Locale.ROOT)

    /** 仅当仓库查询键发生变化时返回 true。 */
    fun update(value: String): Boolean {
        val previousNormalizedQuery = normalizedQuery
        mutableQuery.value = value
        return normalizedQuery != previousNormalizedQuery
    }
}
