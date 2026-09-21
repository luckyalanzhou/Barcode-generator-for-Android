package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.MainActivity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    alignEndWithAnchor: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val containerSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val maxHeight = with(density) { (containerSize.height * 0.62f).toDp() }.coerceAtLeast(180.dp)
    val maxWidth = with(density) { (containerSize.width.toFloat() - 24.dp.toPx()).coerceAtLeast(1f).toDp() }
    val resolvedMenuWidth = menuWidth?.coerceAtMost(maxWidth)
    val widthModifier = if (menuWidth != null) Modifier.width(resolvedMenuWidth ?: maxWidth) else Modifier.widthIn(max = maxWidth)
    val horizontalOffset = if (alignEndWithAnchor && anchorWidth != null && resolvedMenuWidth != null) anchorWidth - resolvedMenuWidth else 0.dp
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(horizontalOffset, 0.dp),
        modifier = modifier.then(widthModifier).heightIn(max = maxHeight),
        shape = shape,
        containerColor = containerColor ?: LocalBarcodeThemeColors.current.surfaceOverlay,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
    ) { content() }
}

@Composable
internal fun ComposeGlassDialogCard(dark: Boolean, horizontalPadding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val card = LocalBarcodeThemeColors.current.surface
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
    val colors = LocalBarcodeThemeColors.current
    val foreground = when {
        primary -> colors.onAccent
        destructive -> colors.destructive
        else -> colors.accent
    }
    val border = if (primary) foreground.copy(alpha = 0.62f) else colors.border
    val background = if (primary) colors.progress else colors.button
    Box(
        modifier = modifier
            .globalButtonChrome(RoundedCornerShape(12.dp), 0.5.dp, border)
            .background(background, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontSize = 15.sp, maxLines = 1, style = LocalTextStyle.current.copy(background = Color.Transparent))
    }
}

@Composable
internal fun ComposeDropdownDivider(dark: Boolean) {
    HorizontalDivider(thickness = if (dark) 0.5.dp else 1.dp, color = LocalBarcodeThemeColors.current.divider)
}

internal fun MainActivity.showIos26NoticeDialogCompose(message: String) {
    showComposeDialog(compact = true) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(message, modifier = Modifier.fillMaxWidth(), color = LocalBarcodeThemeColors.current.primary, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) { DialogAction("确定", dark, dismiss) }
        }
    }
}

/** 带实际确认回调的 Compose 确认弹窗，供收藏编辑等业务继续复用原确认逻辑。 */
internal fun MainActivity.showComposeConfirmDialog(title: String, message: String, positive: String, onConfirm: () -> Unit) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(title, color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            Text(message, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = LocalBarcodeThemeColors.current.secondary, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction(positive, dark, { onConfirm(); dismiss() }, Modifier.padding(start = 8.dp))
            }
        }
    }
}
