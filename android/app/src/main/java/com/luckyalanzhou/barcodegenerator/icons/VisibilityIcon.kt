package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath


internal val VisibilityIcon: ImageVector by lazy {
    ImageVector.Builder("visibility", 24.dp, 24.dp, 24f, 24f).materialPath {
        moveTo(12f, 4.5f)
        quadTo(7f, 4.5f, 3.5f, 12f)
        quadTo(7f, 19.5f, 12f, 19.5f)
        quadTo(17f, 19.5f, 20.5f, 12f)
        quadTo(17f, 4.5f, 12f, 4.5f)
        close()
        moveTo(12f, 17f)
        quadTo(9.925f, 17f, 8.462f, 15.537f)
        quadTo(7f, 14.075f, 7f, 12f)
        quadTo(7f, 9.925f, 8.462f, 8.462f)
        quadTo(9.925f, 7f, 12f, 7f)
        quadTo(14.075f, 7f, 15.537f, 8.462f)
        quadTo(17f, 9.925f, 17f, 12f)
        quadTo(17f, 14.075f, 15.537f, 15.537f)
        quadTo(14.075f, 17f, 12f, 17f)
        close()
        moveTo(12f, 15f)
        quadTo(13.25f, 15f, 14.125f, 14.125f)
        quadTo(15f, 13.25f, 15f, 12f)
        quadTo(15f, 10.75f, 14.125f, 9.875f)
        quadTo(13.25f, 9f, 12f, 9f)
        quadTo(10.75f, 9f, 9.875f, 9.875f)
        quadTo(9f, 10.75f, 9f, 12f)
        quadTo(9f, 13.25f, 9.875f, 14.125f)
        quadTo(10.75f, 15f, 12f, 15f)
        close()
    }.build()
}
