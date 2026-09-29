package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Lucide clock icon used by the History tab. */
internal val HistoryIcon: ImageVector by lazy {
    ImageVector.Builder("history", 24.dp, 24.dp, 24f, 24f).path(
        fill = null,
        fillAlpha = 1f,
        stroke = SolidColor(Color.Black),
        strokeAlpha = 1f,
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        strokeLineMiter = 4f,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(12f, 2f)
        curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
        curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
        curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
        curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
        close()

        moveTo(12f, 6f)
        verticalLineTo(12f)
        lineTo(16f, 14f)
    }.build()
}
