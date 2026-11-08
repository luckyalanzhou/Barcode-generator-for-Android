package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import com.luckyalanzhou.barcodegenerator.ui.component.boundsOnScreen

/** Keeps current row geometry without making scroll-driven position changes observable Compose state. */
internal class FavoriteRowCoordinates {
    var row: LayoutCoordinates? = null
    var title: LayoutCoordinates? = null

    fun rowBounds(): Rect = row
        ?.takeIf { it.isAttached }
        ?.boundsOnScreen()
        ?: Rect.Zero

    fun titleBounds(): Rect? = title
        ?.takeIf { it.isAttached }
        ?.boundsOnScreen()
        ?.takeIf { it != Rect.Zero }

    fun clear() {
        row = null
        title = null
    }
}
