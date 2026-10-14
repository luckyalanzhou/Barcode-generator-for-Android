package com.luckyalanzhou.barcodegenerator.icons

// Original: Google Material Symbols Outlined, close_small, FILL=1.
// https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/close_small.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,1,0,50

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath

internal val CloseSmallIcon: ImageVector by lazy {
    ImageVector.Builder("close_small", 24.dp, 24.dp, 24f, 24f).materialPath {
        moveTo(8.38f, 17.02f)
        lineTo(6.98f, 15.63f)
        lineTo(10.59f, 12f)
        lineTo(6.98f, 8.4f)
        lineTo(8.38f, 7f)
        lineTo(12f, 10.62f)
        lineTo(15.59f, 7f)
        lineTo(17f, 8.4f)
        lineTo(13.38f, 12f)
        lineTo(17f, 15.63f)
        lineToRelative(-1.41f, 1.4f)
        lineTo(12f, 13.41f)
        lineTo(8.38f, 17.02f)
        close()
    }.build()
}
