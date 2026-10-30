package com.luckyalanzhou.barcodegenerator.ui.component

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

/** 全局卡片基座只处理表面、圆角与轻阴影；玻璃边缘高光仅用于可交互控件。 */
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

/** 历史和设置页的静态分组通过填充色区分，不改变其他卡片样式。 */
@Composable
internal fun Modifier.groupedContentSurface(
    dark: Boolean,
    color: Color,
    shape: RoundedCornerShape,
): Modifier {
    return this.clip(shape).background(color)
}
