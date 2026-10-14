package com.luckyalanzhou.barcodegenerator.icons

// Original: Google Material Symbols Outlined, autorenew, FILL=0.
// https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/autorenew.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50
// Original path data: M4.9 15.725q-.5-.9-.7-1.812t-.2-1.863q0-3.275 2.363-5.637T12 4.05h1.075l-2-2 .975-.975 3.725 3.725-3.725 3.725-1-1 1.975-1.975h-1.025q-2.675 0-4.587 1.913T5.5 12.05q0 .725 .138 1.375t.337 1.225l-1.075 1.075ZM11.9 23 8.175 19.275l3.725-3.725 .975 .975-2 2h1.125q2.675 0 4.588-1.912T18.5 12.025q0-.725-.125-1.375t-.375-1.225l1.075-1.075q.5 .9 .713 1.813T20 12.025q0 3.275-2.362 5.638T12 20.025h-1.125l2 2-.975 .975Z

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.materialPath

internal val UpgradeIcon: ImageVector by lazy {
    ImageVector.Builder("autorenew", 24.dp, 24.dp, 24f, 24f).materialPath {
        moveTo(4.9f, 15.725f)
        quadToRelative(-0.5f, -0.9f, -0.7f, -1.812f)
        reflectiveQuadToRelative(-0.2f, -1.863f)
        quadToRelative(0f, -3.275f, 2.363f, -5.637f)
        reflectiveQuadTo(12f, 4.05f)
        horizontalLineToRelative(1.075f)
        lineToRelative(-2f, -2f)
        lineToRelative(0.975f, -0.975f)
        lineToRelative(3.725f, 3.725f)
        lineToRelative(-3.725f, 3.725f)
        lineToRelative(-1f, -1f)
        lineToRelative(1.975f, -1.975f)
        horizontalLineToRelative(-1.025f)
        quadToRelative(-2.675f, 0f, -4.587f, 1.913f)
        reflectiveQuadTo(5.5f, 12.05f)
        quadToRelative(0f, 0.725f, 0.138f, 1.375f)
        reflectiveQuadToRelative(0.337f, 1.225f)
        lineToRelative(-1.075f, 1.075f)
        close()
        moveTo(11.9f, 23f)
        lineTo(8.175f, 19.275f)
        lineToRelative(3.725f, -3.725f)
        lineToRelative(0.975f, 0.975f)
        lineToRelative(-2f, 2f)
        horizontalLineTo(12f)
        quadToRelative(2.675f, 0f, 4.588f, -1.912f)
        reflectiveQuadTo(18.5f, 12.025f)
        quadToRelative(0f, -0.725f, -0.125f, -1.375f)
        reflectiveQuadToRelative(-0.375f, -1.225f)
        lineToRelative(1.075f, -1.075f)
        quadToRelative(0.5f, 0.9f, 0.713f, 1.813f)
        reflectiveQuadTo(20f, 12.025f)
        quadToRelative(0f, 3.275f, -2.362f, 5.638f)
        reflectiveQuadTo(12f, 20.025f)
        horizontalLineToRelative(-1.125f)
        lineToRelative(2f, 2f)
        lineToRelative(-0.975f, 0.975f)
        close()
    }.build()
}
