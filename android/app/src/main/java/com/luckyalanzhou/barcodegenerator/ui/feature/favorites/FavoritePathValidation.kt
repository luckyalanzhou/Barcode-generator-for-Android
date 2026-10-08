package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

internal fun isValidFavoriteFolderPath(value: String): Boolean {
    val parts = value.split('/')
    return parts.size in 1..2 && parts.all { part ->
        part.isNotBlank() && part != "." && part != ".." && !part.contains('\\')
    }
}
