package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath


internal val ContentCopyIcon: ImageVector by lazy {
    ImageVector.Builder("content_copy", 24.dp, 24.dp, 24f, 24f).materialPath {
moveTo(9f, 18f); quadTo(8.18f, 18f, 7.59f, 17.41f); reflectiveQuadTo(7f, 16f); verticalLineTo(4f)
quadTo(7f, 3.17f, 7.59f, 2.59f); reflectiveQuadTo(9f, 2f); horizontalLineToRelative(9f); quadToRelative(0.82f, 0f, 1.41f, 0.59f)
reflectiveQuadTo(20f, 4f); verticalLineTo(16f); quadToRelative(0f, 0.82f, -0.59f, 1.41f); reflectiveQuadTo(18f, 18f)
horizontalLineTo(9f); close(); moveTo(9f, 16f); horizontalLineToRelative(9f); verticalLineTo(4f); horizontalLineTo(9f); verticalLineTo(16f); close()
moveTo(5f, 22f); quadTo(4.18f, 22f, 3.59f, 21.41f); reflectiveQuadTo(3f, 20f); verticalLineTo(6f); horizontalLineTo(5f)
verticalLineTo(20f); horizontalLineTo(16f); verticalLineToRelative(2f); horizontalLineTo(5f); close(); moveTo(9f, 16f); verticalLineTo(4f); verticalLineTo(16f); close()
    }.build()
}
