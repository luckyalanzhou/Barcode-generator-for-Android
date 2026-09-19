package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath


internal val AddIcon: ImageVector by lazy {
    ImageVector.Builder("add", 24.dp, 24.dp, 24f, 24f).materialPath {
moveTo(11f, 13f); horizontalLineTo(5f); verticalLineTo(11f); horizontalLineToRelative(6f)
verticalLineTo(5f); horizontalLineToRelative(2f); verticalLineToRelative(6f); horizontalLineToRelative(6f)
verticalLineToRelative(2f); horizontalLineTo(13f); verticalLineToRelative(6f); horizontalLineTo(11f); verticalLineTo(13f); close()
    }.build()
}
