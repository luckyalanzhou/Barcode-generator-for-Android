package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath


internal val DriveFileMoveIcon: ImageVector by lazy {
    ImageVector.Builder("drive_file_move", 24.dp, 24.dp, 24f, 24f).materialPath {
moveTo(4.31f, 19.5f); quadToRelative(-0.76f, 0f, -1.28f, -0.52f); reflectiveQuadTo(2.5f, 17.69f)
verticalLineTo(6.31f); quadTo(2.5f, 5.55f, 3.03f, 5.03f); reflectiveQuadTo(4.31f, 4.5f)
horizontalLineTo(9.8f); lineToRelative(2f, 2f); horizontalLineToRelative(7.89f)
quadToRelative(0.76f, 0f, 1.28f, 0.52f); reflectiveQuadTo(21.5f, 8.31f); verticalLineToRelative(9.38f)
quadToRelative(0f, 0.76f, -0.52f, 1.28f); reflectiveQuadTo(19.69f, 19.5f); horizontalLineTo(4.31f); close()
moveTo(12.8f, 13.75f); lineToRelative(-1.77f, 1.77f); lineToRelative(1.05f, 1.05f)
lineTo(15.65f, 13f); lineTo(12.08f, 9.43f); lineToRelative(-1.05f, 1.05f); lineToRelative(1.77f, 1.77f)
horizontalLineTo(8.35f); verticalLineToRelative(1.5f); horizontalLineTo(12.8f); close()
    }.build()
}
