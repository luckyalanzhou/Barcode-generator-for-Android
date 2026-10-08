package com.luckyalanzhou.barcodegenerator.presentation.favorites

/** Query identity prevents results for the previous text being presented as a new empty search. */
data class FavoriteSearchStatus(val query: String = "", val busy: Boolean = false, val failed: Boolean = false)
