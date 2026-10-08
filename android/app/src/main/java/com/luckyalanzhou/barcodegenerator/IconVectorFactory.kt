package com.luckyalanzhou.barcodegenerator

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path

internal fun ImageVector.Builder.materialPath(block: PathBuilder.() -> Unit) = path(
    fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null, strokeAlpha = 1f,
    strokeLineWidth = 1f, strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Bevel,
    strokeLineMiter = 1f, pathFillType = PathFillType.NonZero, pathBuilder = block,
)
