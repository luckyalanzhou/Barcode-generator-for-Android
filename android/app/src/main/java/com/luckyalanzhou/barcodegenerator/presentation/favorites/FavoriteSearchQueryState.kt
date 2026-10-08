package com.luckyalanzhou.barcodegenerator.presentation.favorites

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the raw text shown by the search field and its canonical query form. */
internal class FavoriteSearchQueryState {
    private val mutableQuery = MutableStateFlow("")
    val query: StateFlow<String> = mutableQuery.asStateFlow()

    val normalizedQuery: String
        get() = mutableQuery.value.trim().lowercase(Locale.ROOT)

    /** Returns true only when the repository search key changes. */
    fun update(value: String): Boolean {
        val previousNormalizedQuery = normalizedQuery
        mutableQuery.value = value
        return normalizedQuery != previousNormalizedQuery
    }
}
