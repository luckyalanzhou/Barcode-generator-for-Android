package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.component.globalButtonChrome
import com.luckyalanzhou.barcodegenerator.ui.component.globalCardSurface
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 所有下拉菜单统一使用锚点宽度、最大高度和滚动容器，避免超出屏幕。 */
@Composable
internal fun AnchoredDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    dark: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color? = null,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 1.dp,
    menuWidth: Dp? = null,
    anchorWidth: Dp? = null,
    anchorHeight: Dp? = null,
    alignEndWithAnchor: Boolean = false,
    cornerReveal: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val maxHeight = with(density) { (containerSize.height * 0.62f).toDp() }.coerceAtLeast(180.dp)
    val maxWidth = with(density) { (containerSize.width.toFloat() - 24.dp.toPx()).coerceAtLeast(1f).toDp() }
    val resolvedMenuWidth = menuWidth?.coerceAtMost(maxWidth)
    val widthModifier = if (menuWidth != null) Modifier.width(resolvedMenuWidth ?: maxWidth) else Modifier.widthIn(max = maxWidth)
    val horizontalOffset = if (alignEndWithAnchor && anchorWidth != null && resolvedMenuWidth != null) anchorWidth - resolvedMenuWidth else 0.dp
    if (cornerReveal) {
        CornerDropdownMenu(expanded, onDismissRequest,
            modifier.then(widthModifier).heightIn(max = maxHeight), shape,
            containerColor ?: LocalAppColorScheme.current.surfaces.overlay,
            anchorHeight, content)
        return
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(horizontalOffset, 0.dp),
        modifier = modifier.then(widthModifier).heightIn(max = maxHeight),
        shape = shape,
        containerColor = containerColor ?: LocalAppColorScheme.current.surfaces.overlay,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) { content() }
}

@Composable
internal fun ComposeGlassDialogCard(dark: Boolean, horizontalPadding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val card = LocalAppColorScheme.current.surfaces.surface
    Box(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 400.dp)
            .globalCardSurface(dark, card, RoundedCornerShape(20.dp), 2.dp)
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
    ) { Column(content = content) }
}

@Composable
internal fun DialogAction(
    text: String,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
) {
    val colors = LocalAppColorScheme.current
    val foreground = when {
        primary -> colors.text.onAccent
        destructive -> colors.text.destructive
        else -> colors.text.primary
    }
    val background = if (primary) colors.controls.accent else colors.controls.button
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .iosPressFeedback(interactionSource)
            .globalButtonChrome(RoundedCornerShape(12.dp))
            .background(background, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontSize = 15.sp, maxLines = 1, style = LocalTextStyle.current.copy(background = Color.Transparent))
    }
}

@Composable
internal fun ComposeDropdownDivider(dark: Boolean) {
    HorizontalDivider(thickness = if (dark) 0.5.dp else 1.dp, color = LocalAppColorScheme.current.borders.divider)
}
