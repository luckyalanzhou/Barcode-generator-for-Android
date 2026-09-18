package com.luckyalanzhou.barcodegenerator

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Google Material Symbols Outlined 原始 Path。 */
internal object MaterialActionIcons {
    val add: ImageVector by lazy { ImageVector.Builder("add", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(11f, 13f); horizontalLineTo(5f); verticalLineTo(11f); horizontalLineToRelative(6f)
        verticalLineTo(5f); horizontalLineToRelative(2f); verticalLineToRelative(6f); horizontalLineToRelative(6f)
        verticalLineToRelative(2f); horizontalLineTo(13f); verticalLineToRelative(6f); horizontalLineTo(11f); verticalLineTo(13f); close()
    }.build() }

    val search: ImageVector by lazy { ImageVector.Builder("search", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(19.6f, 21f); lineTo(13.3f, 14.7f); quadToRelative(-0.75f, 0.6f, -1.72f, 0.95f)
        reflectiveQuadTo(9.5f, 16f); quadTo(6.78f, 16f, 4.89f, 14.11f); quadTo(3f, 12.23f, 3f, 9.5f)
        quadTo(3f, 6.77f, 4.89f, 4.89f); reflectiveQuadTo(9.5f, 3f); reflectiveQuadToRelative(4.61f, 1.89f)
        reflectiveQuadTo(16f, 9.5f); quadToRelative(0f, 1.1f, -0.35f, 2.07f); reflectiveQuadTo(14.7f, 13.3f)
        lineTo(21f, 19.6f); lineTo(19.6f, 21f); close(); moveTo(9.5f, 14f); quadToRelative(1.88f, 0f, 3.19f, -1.31f)
        reflectiveQuadTo(14f, 9.5f); reflectiveQuadTo(12.69f, 6.31f); reflectiveQuadTo(9.5f, 5f)
        reflectiveQuadTo(6.31f, 6.31f); reflectiveQuadTo(5f, 9.5f); reflectiveQuadToRelative(1.31f, 3.19f); reflectiveQuadTo(9.5f, 14f); close()
    }.build() }

    val photoCamera: ImageVector by lazy { ImageVector.Builder("photo_camera", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(12f, 17.5f); quadToRelative(1.88f, 0f, 3.19f, -1.31f); reflectiveQuadTo(16.5f, 13f)
        reflectiveQuadTo(15.19f, 9.81f); reflectiveQuadTo(12f, 8.5f); reflectiveQuadTo(8.81f, 9.81f)
        reflectiveQuadTo(7.5f, 13f); reflectiveQuadToRelative(1.31f, 3.19f); reflectiveQuadTo(12f, 17.5f); close()
        moveToRelative(0f, -2f); quadToRelative(-1.05f, 0f, -1.77f, -0.72f); reflectiveQuadTo(9.5f, 13f)
        reflectiveQuadToRelative(0.73f, -1.78f); reflectiveQuadTo(12f, 10.5f); reflectiveQuadToRelative(1.78f, 0.72f)
        reflectiveQuadTo(14.5f, 13f); reflectiveQuadToRelative(-0.72f, 1.78f); reflectiveQuadTo(12f, 15.5f); close()
        moveTo(4f, 21f); quadTo(3.18f, 21f, 2.59f, 20.41f); reflectiveQuadTo(2f, 19f); verticalLineTo(7f)
        quadTo(2f, 6.18f, 2.59f, 5.59f); reflectiveQuadTo(4f, 5f); horizontalLineTo(7.15f); lineTo(9f, 3f)
        horizontalLineToRelative(6f); lineToRelative(1.85f, 2f); horizontalLineTo(20f); quadToRelative(0.83f, 0f, 1.41f, 0.59f)
        quadTo(22f, 6.18f, 22f, 7f); verticalLineTo(19f); quadToRelative(0f, 0.82f, -0.59f, 1.41f)
        reflectiveQuadTo(20f, 21f); horizontalLineTo(4f); close(); moveTo(4f, 19f); horizontalLineTo(20f)
        verticalLineTo(7f); horizontalLineTo(15.95f); lineTo(14.13f, 5f); horizontalLineTo(9.88f); lineTo(8.05f, 7f)
        horizontalLineTo(4f); verticalLineTo(19f); close(); moveToRelative(8f, -6f); close()
    }.build() }

    val folder: ImageVector by lazy { ImageVector.Builder("folder", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(4f, 20f); quadTo(3.18f, 20f, 2.59f, 19.41f); reflectiveQuadTo(2f, 18f); verticalLineTo(6f)
        quadTo(2f, 5.18f, 2.59f, 4.59f); reflectiveQuadTo(4f, 4f); horizontalLineToRelative(6f); lineToRelative(2f, 2f)
        horizontalLineToRelative(8f); quadToRelative(0.83f, 0f, 1.41f, 0.59f); quadTo(22f, 7.18f, 22f, 8f)
        verticalLineTo(18f); quadToRelative(0f, 0.82f, -0.59f, 1.41f); reflectiveQuadTo(20f, 20f); horizontalLineTo(4f); close()
        moveTo(4f, 18f); horizontalLineTo(20f); verticalLineTo(8f); horizontalLineTo(11.18f); lineToRelative(-2f, -2f)
        horizontalLineTo(4f); verticalLineTo(18f); close(); moveToRelative(0f, 0f); verticalLineTo(6f); verticalLineTo(8f); verticalLineTo(18f); close()
    }.build() }

    val attachFile: ImageVector by lazy { ImageVector.Builder("attach_file", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(18f, 15.75f); quadToRelative(0f, 2.6f, -1.82f, 4.43f); reflectiveQuadTo(11.75f, 22f)
        reflectiveQuadTo(7.33f, 20.18f); reflectiveQuadTo(5.5f, 15.75f); verticalLineTo(6.5f)
        quadTo(5.5f, 4.63f, 6.81f, 3.31f); reflectiveQuadTo(10f, 2f); reflectiveQuadToRelative(3.19f, 1.31f)
        reflectiveQuadTo(14.5f, 6.5f); verticalLineToRelative(8.75f); quadToRelative(0f, 1.15f, -0.8f, 1.95f)
        reflectiveQuadTo(11.75f, 18f); reflectiveQuadTo(9.8f, 17.2f); reflectiveQuadTo(9f, 15.25f); verticalLineTo(6f)
        horizontalLineToRelative(2f); verticalLineToRelative(9.25f); quadToRelative(0f, 0.32f, 0.21f, 0.54f)
        reflectiveQuadTo(11.75f, 16f); reflectiveQuadToRelative(0.54f, -0.21f); reflectiveQuadTo(12.5f, 15.25f)
        verticalLineTo(6.5f); quadTo(12.48f, 5.45f, 11.76f, 4.72f); reflectiveQuadTo(10f, 4f)
        reflectiveQuadTo(8.23f, 4.72f); reflectiveQuadTo(7.5f, 6.5f); verticalLineToRelative(9.25f)
        quadToRelative(-0.02f, 1.77f, 1.22f, 3.01f); quadTo(9.98f, 20f, 11.75f, 20f)
        quadToRelative(1.75f, 0f, 2.98f, -1.24f); reflectiveQuadTo(16f, 15.75f); verticalLineTo(6f)
        horizontalLineToRelative(2f); verticalLineToRelative(9.75f); close()
    }.build() }

    val iosShare: ImageVector by lazy { ImageVector.Builder("ios_share", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(6f, 22f); quadTo(5.18f, 22f, 4.59f, 21.41f); reflectiveQuadTo(4f, 20f); verticalLineTo(10f)
        quadTo(4f, 9.17f, 4.59f, 8.59f); reflectiveQuadTo(6f, 8f); horizontalLineTo(9f); verticalLineToRelative(2f)
        horizontalLineTo(6f); verticalLineTo(20f); horizontalLineTo(18f); verticalLineTo(10f); horizontalLineTo(15f)
        verticalLineTo(8f); horizontalLineToRelative(3f); quadToRelative(0.82f, 0f, 1.41f, 0.59f); reflectiveQuadTo(20f, 10f)
        verticalLineTo(20f); quadToRelative(0f, 0.82f, -0.59f, 1.41f); reflectiveQuadTo(18f, 22f); horizontalLineTo(6f); close()
        moveToRelative(5f, -6f); verticalLineTo(4.82f); lineTo(9.4f, 6.43f); lineTo(8f, 5f); lineTo(12f, 1f)
        lineToRelative(4f, 4f); lineTo(14.6f, 6.43f); lineTo(13f, 4.82f); verticalLineTo(16f); horizontalLineTo(11f); close()
    }.build() }

    val contentCopy: ImageVector by lazy { ImageVector.Builder("content_copy", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(9f, 18f); quadTo(8.18f, 18f, 7.59f, 17.41f); reflectiveQuadTo(7f, 16f); verticalLineTo(4f)
        quadTo(7f, 3.17f, 7.59f, 2.59f); reflectiveQuadTo(9f, 2f); horizontalLineToRelative(9f); quadToRelative(0.82f, 0f, 1.41f, 0.59f)
        reflectiveQuadTo(20f, 4f); verticalLineTo(16f); quadToRelative(0f, 0.82f, -0.59f, 1.41f); reflectiveQuadTo(18f, 18f)
        horizontalLineTo(9f); close(); moveTo(9f, 16f); horizontalLineToRelative(9f); verticalLineTo(4f); horizontalLineTo(9f); verticalLineTo(16f); close()
        moveTo(5f, 22f); quadTo(4.18f, 22f, 3.59f, 21.41f); reflectiveQuadTo(3f, 20f); verticalLineTo(6f); horizontalLineTo(5f)
        verticalLineTo(20f); horizontalLineTo(16f); verticalLineToRelative(2f); horizontalLineTo(5f); close(); moveTo(9f, 16f); verticalLineTo(4f); verticalLineTo(16f); close()
    }.build() }

    val qrCode2: ImageVector by lazy { ImageVector.Builder("qr_code_2", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(13f, 21f); verticalLineTo(19f); horizontalLineToRelative(2f); verticalLineToRelative(2f); horizontalLineTo(13f); close()
        moveTo(11f, 19f); verticalLineTo(14f); horizontalLineToRelative(2f); verticalLineToRelative(5f); horizontalLineTo(11f); close()
        moveToRelative(8f, -3f); verticalLineTo(12f); horizontalLineToRelative(2f); verticalLineToRelative(4f); horizontalLineTo(19f); close()
        moveTo(17f, 12f); verticalLineTo(10f); horizontalLineToRelative(2f); verticalLineTo(12f); horizontalLineTo(17f); close()
        moveTo(5f, 14f); verticalLineTo(12f); horizontalLineTo(7f); verticalLineToRelative(2f); horizontalLineTo(5f); close()
        moveTo(3f, 12f); verticalLineTo(10f); horizontalLineTo(5f); verticalLineTo(12f); horizontalLineTo(3f); close()
        moveTo(12f, 5f); verticalLineTo(3f); horizontalLineToRelative(2f); verticalLineTo(5f); horizontalLineTo(12f); close()
        moveTo(4.5f, 7.5f); horizontalLineToRelative(3f); verticalLineToRelative(-3f); horizontalLineToRelative(-3f); verticalLineToRelative(3f); close()
        moveTo(3f, 9f); verticalLineTo(3f); horizontalLineTo(9f); verticalLineTo(9f); horizontalLineTo(3f); close()
        moveTo(4.5f, 19.5f); horizontalLineToRelative(3f); verticalLineToRelative(-3f); horizontalLineToRelative(-3f); verticalLineToRelative(3f); close()
        moveTo(3f, 21f); verticalLineTo(15f); horizontalLineTo(9f); verticalLineToRelative(6f); horizontalLineTo(3f); close()
        moveTo(16.5f, 7.5f); horizontalLineToRelative(3f); verticalLineToRelative(-3f); horizontalLineToRelative(-3f); verticalLineToRelative(3f); close()
        moveTo(15f, 9f); verticalLineTo(3f); horizontalLineToRelative(6f); verticalLineTo(9f); horizontalLineTo(15f); close()
        moveToRelative(2f, 12f); verticalLineTo(18f); horizontalLineTo(15f); verticalLineTo(16f); horizontalLineToRelative(4f); verticalLineToRelative(3f)
        horizontalLineToRelative(2f); verticalLineToRelative(2f); horizontalLineTo(17f); close(); moveTo(13f, 14f); verticalLineTo(12f)
        horizontalLineToRelative(4f); verticalLineTo(14f); horizontalLineTo(13f); close(); moveTo(9f, 14f); verticalLineTo(12f)
        horizontalLineTo(7f); verticalLineTo(10f); horizontalLineToRelative(6f); verticalLineToRelative(2f); horizontalLineTo(11f)
        verticalLineToRelative(2f); horizontalLineTo(9f); close(); moveTo(10f, 9f); verticalLineTo(5f); horizontalLineToRelative(2f)
        verticalLineTo(7f); horizontalLineToRelative(2f); verticalLineTo(9f); horizontalLineTo(10f); close(); moveTo(5.25f, 6.75f)
        verticalLineTo(5.25f); horizontalLineToRelative(1.5f); verticalLineToRelative(1.5f); horizontalLineTo(5.25f); close()
        moveToRelative(0f, 12f); verticalLineToRelative(-1.5f); horizontalLineToRelative(1.5f); verticalLineToRelative(1.5f); horizontalLineTo(5.25f); close()
        moveToRelative(12f, -12f); verticalLineTo(5.25f); horizontalLineToRelative(1.5f); verticalLineToRelative(1.5f); horizontalLineToRelative(-1.5f); close()
    }.build() }

    val edit: ImageVector by lazy { ImageVector.Builder("edit", 24.dp, 24.dp, 24f, 24f).path {
        moveTo(5f, 19f); horizontalLineTo(6.43f); lineTo(16.2f, 9.23f); lineTo(14.78f, 7.8f); lineTo(5f, 17.58f); verticalLineTo(19f); close()
        moveTo(3f, 21f); verticalLineTo(16.75f); lineTo(16.2f, 3.57f); quadTo(16.5f, 3.3f, 16.86f, 3.15f)
        reflectiveQuadTo(17.63f, 3f); quadToRelative(0.4f, 0f, 0.78f, 0.15f); reflectiveQuadTo(19.05f, 3.6f)
        lineTo(20.43f, 5f); quadToRelative(0.3f, 0.27f, 0.44f, 0.65f); reflectiveQuadTo(21f, 6.4f)
        quadToRelative(0f, 0.4f, -0.14f, 0.76f); reflectiveQuadTo(20.43f, 7.82f); lineTo(7.25f, 21f); horizontalLineTo(3f); close()
        moveTo(19f, 6.4f); lineTo(17.6f, 5f); lineTo(19f, 6.4f); close(); moveTo(15.48f, 8.52f); lineTo(14.78f, 7.8f)
        lineTo(16.2f, 9.23f); lineTo(15.48f, 8.52f); close()
    }.build() }

    private fun ImageVector.Builder.path(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) = path(
        fill = SolidColor(Color.Black), fillAlpha = 1f, stroke = null, strokeAlpha = 1f, strokeLineWidth = 1f,
        strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Bevel, strokeLineMiter = 1f,
        pathFillType = PathFillType.NonZero, pathBuilder = block,
    )
}
