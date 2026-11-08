package com.luckyalanzhou.barcodegenerator.icons

// Google Material Symbols Outlined, unfold_more, opsz=24, wght=400, FILL=0, GRAD=0.
// Apache-2.0. 原始 path 不作形变：
// https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/unfold_more.kt?var=opsz,wght,FILL,GRAD@24,400,0,0

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val UnfoldMoreIcon: ImageVector by lazy {
    ImageVector.Builder("unfold_more", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(12f, 21f)
            lineTo(7.5f, 16.5f)
            lineTo(8.95f, 15.05f)
            lineTo(12f, 18.1f)
            lineToRelative(3.05f, -3.05f)
            lineTo(16.5f, 16.5f)
            lineTo(12f, 21f)
            close()
            moveTo(8.95f, 9.05f)
            lineTo(7.5f, 7.6f)
            lineTo(12f, 3.1f)
            lineToRelative(4.5f, 4.5f)
            lineTo(15.05f, 9.05f)
            lineTo(12f, 6f)
            lineTo(8.95f, 9.05f)
            close()
        }
    }.build()
}
