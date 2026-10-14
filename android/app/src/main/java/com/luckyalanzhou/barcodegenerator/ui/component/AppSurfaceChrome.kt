package com.luckyalanzhou.barcodegenerator.ui.component

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/** 全局卡片基座：统一圆角、边缘分离度和极轻阴影，页面保留自己的表面颜色。 */
@Composable
internal fun Modifier.globalCardSurface(
    dark: Boolean,
    color: Color,
    shape: RoundedCornerShape = RoundedCornerShape(LocalAppDimensions.current.cardCornerRadius),
    elevation: Dp = 3.dp,
): Modifier = this
    .shadow(minOf(elevation, 1.dp), shape, clip = true)
    .clip(shape)
    .background(color)
    .border(
        if (dark) 0.7.dp else 1.dp,
        LocalAppColorScheme.current.borders.card,
        shape,
    )

/** Static history/settings groups: separate by fill, without changing other card variants. */
@Composable
internal fun Modifier.groupedContentSurface(
    dark: Boolean,
    color: Color,
    shape: RoundedCornerShape,
): Modifier {
    val outline = LocalAppColorScheme.current.borders.card
    return this.clip(shape).background(color).then(
        if (dark) Modifier else Modifier.border(0.5.dp, outline.copy(alpha = outline.alpha * .4f), shape),
    )
}
