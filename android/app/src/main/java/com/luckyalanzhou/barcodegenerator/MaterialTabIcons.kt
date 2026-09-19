package com.luckyalanzhou.barcodegenerator

import androidx.compose.ui.graphics.vector.ImageVector
import com.luckyalanzhou.barcodegenerator.icons.*

/** Compatibility facade; actual tab icon Paths live in individual icon files. */
internal object MaterialTabIcons {
    val settings: ImageVector get() = SettingsIcon
    val history: ImageVector get() = HistoryIcon
    val favorite: ImageVector get() = FavoriteIcon
    val barcode: ImageVector get() = BarcodeIcon
    val arrowUpward: ImageVector get() = ArrowUpwardIcon
    val arrowDownward: ImageVector get() = ArrowDownwardIcon
    val delete: ImageVector get() = DeleteIcon
}
