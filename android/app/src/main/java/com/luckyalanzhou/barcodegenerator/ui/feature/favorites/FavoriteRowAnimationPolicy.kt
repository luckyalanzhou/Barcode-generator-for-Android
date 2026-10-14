package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

/** Existing lazy rows must not replay expansion when recomposed after scrolling. */
internal fun favoriteRowInitiallyVisible(key: String, enteringKeys: Set<String>): Boolean =
    key !in enteringKeys
