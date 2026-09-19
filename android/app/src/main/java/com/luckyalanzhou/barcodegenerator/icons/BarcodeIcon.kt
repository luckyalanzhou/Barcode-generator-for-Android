package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath


internal val BarcodeIcon: ImageVector by lazy {
    ImageVector.Builder("barcode", 24.dp, 24.dp, 24f, 24f).materialPath {
moveTo(1f, 19f)
verticalLineTo(5f)
horizontalLineTo(3f)
verticalLineTo(19f)
horizontalLineTo(1f)
close()
moveToRelative(3f, 0f)
verticalLineTo(5f)
horizontalLineTo(6f)
verticalLineTo(19f)
horizontalLineTo(4f)
close()
moveToRelative(3f, 0f)
verticalLineTo(5f)
horizontalLineTo(8f)
verticalLineTo(19f)
horizontalLineTo(7f)
close()
moveToRelative(3f, 0f)
verticalLineTo(5f)
horizontalLineToRelative(2f)
verticalLineTo(19f)
horizontalLineTo(10f)
close()
moveToRelative(3f, 0f)
verticalLineTo(5f)
horizontalLineToRelative(3f)
verticalLineTo(19f)
horizontalLineTo(13f)
close()
moveToRelative(4f, 0f)
verticalLineTo(5f)
horizontalLineToRelative(1f)
verticalLineTo(19f)
horizontalLineTo(17f)
close()
moveToRelative(3f, 0f)
verticalLineTo(5f)
horizontalLineToRelative(3f)
verticalLineTo(19f)
horizontalLineTo(20f)
close()
    }.build()
}
