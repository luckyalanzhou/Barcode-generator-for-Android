package com.luckyalanzhou.barcodegenerator.icons

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.luckyalanzhou.barcodegenerator.materialPath

/** Material Symbols settings icon with FILL=1, used while the Settings tab is selected. */
internal val SettingsFilledIcon: ImageVector by lazy {
    ImageVector.Builder("settings_filled", 24.dp, 24.dp, 24f, 24f).materialPath {
        moveTo(9.25f, 22f)
        lineTo(8.85f, 18.8f)
        quadTo(8.53f, 18.68f, 8.24f, 18.5f)
        reflectiveQuadTo(7.68f, 18.13f)
        lineTo(4.7f, 19.38f)
        lineTo(1.95f, 14.63f)
        lineTo(4.53f, 12.68f)
        quadTo(4.5f, 12.5f, 4.5f, 12.34f)
        quadToRelative(0f, -0.16f, 0f, -0.34f)
        reflectiveQuadToRelative(0f, -0.34f)
        reflectiveQuadTo(4.53f, 11.33f)
        lineTo(1.95f, 9.38f)
        lineTo(4.7f, 4.63f)
        lineTo(7.68f, 5.88f)
        quadTo(7.95f, 5.68f, 8.25f, 5.5f)
        reflectiveQuadTo(8.85f, 5.2f)
        lineTo(9.25f, 2f)
        horizontalLineToRelative(5.5f)
        lineToRelative(0.4f, 3.2f)
        quadTo(15.48f, 5.33f, 15.76f, 5.5f)
        reflectiveQuadToRelative(0.56f, 0.38f)
        lineTo(19.3f, 4.63f)
        lineToRelative(2.75f, 4.75f)
        lineToRelative(-2.57f, 1.95f)
        quadToRelative(0.02f, 0.18f, 0.02f, 0.34f)
        reflectiveQuadToRelative(0f, 0.34f)
        reflectiveQuadToRelative(0f, 0.34f)
        reflectiveQuadToRelative(-0.05f, 0.34f)
        lineToRelative(2.57f, 1.95f)
        lineToRelative(-2.75f, 4.75f)
        lineTo(16.33f, 18.13f)
        quadToRelative(-0.27f, 0.2f, -0.57f, 0.38f)
        reflectiveQuadToRelative(-0.6f, 0.3f)
        lineTo(14.75f, 22f)
        horizontalLineTo(9.25f)
        close()

        moveToRelative(2.8f, -6.5f)
        quadToRelative(1.45f, -0.2f, 2.47f, -1.03f)
        reflectiveQuadTo(15.55f, 12f)
        reflectiveQuadTo(14.53f, 9.52f)
        quadToRelative(-1.47f, -1.02f, -2.48f, -1.02f)
        reflectiveQuadTo(8.55f, 12f)
        quadToRelative(0f, 1.46f, 1.01f, 2.47f)
        reflectiveQuadToRelative(2.49f, 1.03f)
        close()
    }.build()
}
