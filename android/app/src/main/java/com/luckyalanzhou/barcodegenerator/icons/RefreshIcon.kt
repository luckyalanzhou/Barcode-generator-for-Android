package com.luckyalanzhou.barcodegenerator.icons

// Original: Google Material Symbols Outlined, refresh, FILL=0.
// https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/refresh.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath

internal val RefreshIcon: ImageVector by lazy {
    ImageVector.Builder("refresh", 24.dp, 24.dp, 24f, 24f).materialPath {
        moveTo(17.65f, 6.35f)
        curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
        curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
        reflectiveCurveTo(7.58f, 20f, 12f, 20f)
        curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
        horizontalLineToRelative(-2.08f)
        curveTo(16.85f, 16.33f, 14.6f, 18f, 12f, 18f)
        curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
        reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
        curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
        lineTo(13f, 11f)
        horizontalLineToRelative(7f)
        verticalLineTo(4f)
        lineToRelative(-2.35f, 2.35f)
        close()
    }.build()
}
