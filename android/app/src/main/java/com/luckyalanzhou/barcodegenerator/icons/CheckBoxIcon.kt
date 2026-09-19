package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath


internal val CheckBoxIcon: ImageVector by lazy {
    ImageVector.Builder("check_box", 24.dp, 24.dp, 24f, 24f).materialPath {
moveTo(19f, 3f); horizontalLineTo(5f); quadTo(3.9f, 3f, 3f, 3.9f); quadTo(2f, 4.8f, 2f, 6f)
verticalLineTo(18f); quadTo(2f, 19.2f, 3f, 20.1f); quadTo(3.9f, 21f, 5f, 21f)
horizontalLineTo(19f); quadTo(20.2f, 21f, 21.1f, 20.1f); quadTo(22f, 19.2f, 22f, 18f)
verticalLineTo(6f); quadTo(22f, 4.8f, 21.1f, 3.9f); quadTo(20.2f, 3f, 19f, 3f); close()
moveTo(10f, 17f); lineTo(5f, 12f); lineTo(6.4f, 10.6f); lineTo(10f, 14.2f); lineTo(17.6f, 6.6f)
lineTo(19f, 8f); lineTo(10f, 17f); close()
    }.build()
}
