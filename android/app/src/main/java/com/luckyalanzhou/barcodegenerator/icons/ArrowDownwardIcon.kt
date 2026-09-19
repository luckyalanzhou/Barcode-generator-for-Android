package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath


internal val ArrowDownwardIcon: ImageVector by lazy {
    ImageVector.Builder("arrow_downward", 24.dp, 24.dp, 24f, 24f).materialPath {
moveTo(11.25f, 4.5f)
verticalLineTo(16.63f)
lineToRelative(-5.7f, -5.7f)
lineTo(4.5f, 12f)
lineTo(12f, 19.5f)
lineTo(19.5f, 12f)
lineToRelative(-1.05f, -1.07f)
lineToRelative(-5.7f, 5.7f)
verticalLineTo(4.5f)
horizontalLineToRelative(-1.5f)
close()
    }.build()
}
