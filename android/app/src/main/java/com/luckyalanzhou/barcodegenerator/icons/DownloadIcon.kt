package com.luckyalanzhou.barcodegenerator.icons

// Original: Google Material Symbols Outlined, download, FILL=0.
// https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/download.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50
// Path data: M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath

internal val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder("download", 24.dp, 24.dp, 24f, 24f).materialPath {
        moveTo(5f, 20f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        verticalLineToRelative(2f)
        close()
        moveTo(19f, 9f)
        horizontalLineToRelative(-4f)
        verticalLineTo(3f)
        horizontalLineTo(9f)
        verticalLineToRelative(6f)
        horizontalLineTo(5f)
        lineToRelative(7f, 7f)
        lineToRelative(7f, -7f)
        close()
    }.build()
}
