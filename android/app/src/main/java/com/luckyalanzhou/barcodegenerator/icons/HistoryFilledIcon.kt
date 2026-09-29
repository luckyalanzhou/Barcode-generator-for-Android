package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Filled Tabler clock used while the History tab is selected. */
internal val HistoryFilledIcon: ImageVector by lazy {
    ImageVector.Builder("history_filled", 24.dp, 24.dp, 24f, 24f).path(
        fill = SolidColor(Color.Black),
        fillAlpha = 1f,
        stroke = null,
        strokeAlpha = 1f,
        strokeLineWidth = 1f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Bevel,
        strokeLineMiter = 4f,
        pathFillType = PathFillType.EvenOdd,
    ) {
        // Outer clock face.
        moveTo(12f, 2f)
        curveTo(17.5228f, 2f, 22f, 6.4772f, 22f, 12f)
        curveTo(22f, 17.5228f, 17.5228f, 22f, 12f, 22f)
        curveTo(6.4772f, 22f, 2f, 17.5228f, 2f, 12f)
        curveTo(2f, 6.4772f, 6.4772f, 2f, 12f, 2f)
        close()

        // Clock hands are a transparent cutout, matching the filled SVG silhouette.
        moveTo(12f, 6f)
        arcTo(1f, 1f, 0f, false, false, 11.007f, 6.883f)
        lineTo(11f, 7f)
        verticalLineTo(12f)
        lineTo(11.009f, 12.131f)
        arcTo(1f, 1f, 0f, false, false, 11.206f, 12.608f)
        lineTo(11.293f, 12.708f)
        lineTo(14.293f, 15.708f)
        lineTo(14.387f, 15.79f)
        arcTo(1f, 1f, 0f, false, false, 15.613f, 15.79f)
        lineTo(15.707f, 15.707f)
        lineTo(15.79f, 15.613f)
        arcTo(1f, 1f, 0f, false, false, 15.79f, 14.387f)
        lineTo(15.707f, 14.293f)
        lineTo(13f, 11.585f)
        verticalLineTo(7f)
        lineTo(12.993f, 6.883f)
        arcTo(1f, 1f, 0f, false, false, 12f, 6f)
        close()
    }.build()
}
