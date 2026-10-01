package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath

internal val UpgradeIcon: ImageVector by lazy {
    ImageVector.Builder("upgrade", 24.dp, 24.dp, 24f, 24f).materialPath {
        moveTo(7f, 20f)
        verticalLineTo(18f)
        horizontalLineTo(17f)
        verticalLineToRelative(2f)
        horizontalLineTo(7f)
        close()
        moveToRelative(4f, -4f)
        verticalLineTo(7.82f)
        lineTo(8.4f, 10.4f)
        lineTo(7f, 9f)
        lineTo(12f, 4f)
        lineToRelative(5f, 5f)
        lineToRelative(-1.4f, 1.4f)
        lineTo(13f, 7.82f)
        verticalLineTo(16f)
        horizontalLineTo(11f)
        close()
    }.build()
}
