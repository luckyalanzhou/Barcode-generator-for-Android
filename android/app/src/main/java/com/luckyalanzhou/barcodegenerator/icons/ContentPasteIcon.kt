package com.luckyalanzhou.barcodegenerator.icons

// Google Material Symbols Outlined, content_paste, opsz=24, wght=400, FILL=0, GRAD=0.
// Apache-2.0. Original path preserved:
// https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/content_paste.kt?var=opsz,wght,FILL,GRAD@24,400,0,0

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val ContentPasteIcon: ImageVector by lazy {
    ImageVector.Builder("content_paste", 24.dp, 24.dp, 24f, 24f).apply {
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
            moveTo(5f, 21f)
            quadTo(4.18f, 21f, 3.59f, 20.41f)
            reflectiveQuadTo(3f, 19f)
            verticalLineTo(5f)
            quadTo(3f, 4.17f, 3.59f, 3.59f)
            reflectiveQuadTo(5f, 3f)
            horizontalLineTo(9.18f)
            quadTo(9.45f, 2.13f, 10.25f, 1.56f)
            reflectiveQuadTo(12f, 1f)
            quadToRelative(1f, 0f, 1.79f, 0.56f)
            reflectiveQuadTo(14.85f, 3f)
            horizontalLineTo(19f)
            quadToRelative(0.83f, 0f, 1.41f, 0.59f)
            reflectiveQuadTo(21f, 5f)
            verticalLineTo(19f)
            quadToRelative(0f, 0.82f, -0.59f, 1.41f)
            reflectiveQuadTo(19f, 21f)
            horizontalLineTo(5f)
            close()
            moveTo(5f, 19f)
            horizontalLineTo(19f)
            verticalLineTo(5f)
            horizontalLineTo(17f)
            verticalLineTo(8f)
            horizontalLineTo(7f)
            verticalLineTo(5f)
            horizontalLineTo(5f)
            verticalLineTo(19f)
            close()
            moveTo(12.71f, 4.71f)
            quadTo(13f, 4.42f, 13f, 4f)
            quadTo(13f, 3.57f, 12.71f, 3.29f)
            reflectiveQuadTo(12f, 3f)
            reflectiveQuadTo(11.29f, 3.29f)
            reflectiveQuadTo(11f, 4f)
            quadToRelative(0f, 0.42f, 0.29f, 0.71f)
            reflectiveQuadTo(12f, 5f)
            reflectiveQuadTo(12.71f, 4.71f)
            close()
        }
    }.build()
}
