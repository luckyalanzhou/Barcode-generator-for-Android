package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath


internal val CreateNewFolderIcon: ImageVector by lazy {
    ImageVector.Builder("create_new_folder", 24.dp, 24.dp, 24f, 24f).materialPath {
moveTo(14f, 16f); horizontalLineToRelative(2f); verticalLineTo(14f); horizontalLineToRelative(2f); verticalLineTo(12f)
horizontalLineTo(16f); verticalLineTo(10f); horizontalLineTo(14f); verticalLineToRelative(2f); horizontalLineTo(12f)
verticalLineToRelative(2f); horizontalLineToRelative(2f); verticalLineToRelative(2f); close()
moveTo(4f, 20f); quadTo(3.18f, 20f, 2.59f, 19.41f); reflectiveQuadTo(2f, 18f); verticalLineTo(6f)
quadTo(2f, 5.18f, 2.59f, 4.59f); reflectiveQuadTo(4f, 4f); horizontalLineToRelative(6f); lineToRelative(2f, 2f)
horizontalLineToRelative(8f); quadToRelative(0.83f, 0f, 1.41f, 0.59f); quadTo(22f, 7.18f, 22f, 8f)
verticalLineTo(18f); quadTo(22f, 18.82f, 21.41f, 19.41f); reflectiveQuadTo(20f, 20f); horizontalLineTo(4f); close()
moveTo(4f, 18f); horizontalLineTo(20f); verticalLineTo(8f); horizontalLineTo(11.18f); lineToRelative(-2f, -2f)
horizontalLineTo(4f); verticalLineTo(18f); close(); moveToRelative(0f, 0f); verticalLineTo(6f); verticalLineTo(8f); verticalLineTo(18f); close()
    }.build()
}
