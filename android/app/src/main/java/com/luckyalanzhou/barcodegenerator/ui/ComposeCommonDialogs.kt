package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val onMetric = LocalDialogMetric.current
    val card = LocalBarcodeThemeColors.current.surface
    val cardSelected = LocalDialogSelectedElement.current?.value == "弹窗卡片"
    Box(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 400.dp)
            .globalCardSurface(dark, card, RoundedCornerShape(20.dp), 2.dp)
            .dialogMetricBounds("弹窗卡片", cardSelected, DialogElementVisual(LocalBarcodeThemeColors.current.primary.hexValue(), "继承内容", "容器", card.hexValue(), LocalBarcodeThemeColors.current.cardBorder.hexValue()))
            .clickable { onMetric("弹窗卡片") }
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
    val onMetric = LocalDialogMetric.current
    val colors = LocalBarcodeThemeColors.current
    val inspectOnly = LocalDialogInspectOnly.current
    val selected = LocalDialogSelectedElement.current?.value == "按钮：$text"
    val foreground = when {
        primary -> colors.onAccent
        destructive -> colors.destructive
        else -> colors.accent
    }
    val border = if (primary) foreground.copy(alpha = 0.62f) else colors.border
    val background = if (primary) colors.progress else colors.button
    Box(
        modifier = modifier
            .globalButtonChrome(RoundedCornerShape(12.dp), 1.dp)
            .dialogMetricBounds("按钮：$text", selected, DialogElementVisual(foreground.hexValue(), "15sp", "常规", background.hexValue(), border.hexValue()))
            .background(background, RoundedCornerShape(12.dp))
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable { onMetric("按钮：$text"); if (!inspectOnly) onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, fontSize = 15.sp, maxLines = 1, style = LocalTextStyle.current.copy(background = Color.Transparent))
    }
}

@Composable
internal fun ComposeDropdownDivider(dark: Boolean) {
    HorizontalDivider(thickness = 1.dp, color = LocalBarcodeThemeColors.current.divider)
}

internal fun MainActivity.showIos26NoticeDialogCompose(message: String, showMetrics: Boolean = false) {
    showComposeDialog(compact = true, metricsLabel = if (showMetrics) "提示弹窗" else null) { dismiss ->
        val dark = isDark()
        val onMetric = LocalDialogMetric.current
        ComposeGlassDialogCard(dark) {
            Text(message, modifier = Modifier.fillMaxWidth().clickable { onMetric("文本") }, color = LocalBarcodeThemeColors.current.primary, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) { DialogAction("确定", dark, dismiss) }
        }
    }
}

internal fun MainActivity.showSimulatedDialogCompose(title: String, message: String, negative: String?, neutral: String?, positive: String?, showMetrics: Boolean = true) {
    showComposeDialog(compact = false, metricsLabel = if (showMetrics) title else null) { dismiss ->
        val dark = isDark()
        val onMetric = LocalDialogMetric.current
        ComposeGlassDialogCard(dark) {
            Text(title, modifier = Modifier.dialogMetricTarget("标题", DialogElementVisual(LocalBarcodeThemeColors.current.primary.hexValue(), "18sp", "Medium")), color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            Text(message, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).dialogMetricTarget("副标题", DialogElementVisual(LocalBarcodeThemeColors.current.secondary.hexValue(), "15sp", "常规")), color = LocalBarcodeThemeColors.current.secondary, fontSize = 15.sp)
            val actions = listOfNotNull(negative, neutral, positive)
            if (actions.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                actions.forEachIndexed { index, action -> DialogAction(action, dark, { onMetric("按钮：$action") }, if (index == 0) Modifier else Modifier.padding(start = 8.dp)) }
            }
        }
    }
}

/** 带实际确认回调的 Compose 确认弹窗，供收藏编辑等业务继续复用原确认逻辑。 */
internal fun MainActivity.showComposeConfirmDialog(title: String, message: String, positive: String, onConfirm: () -> Unit) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(title, modifier = Modifier.dialogMetricTarget("标题", DialogElementVisual(LocalBarcodeThemeColors.current.primary.hexValue(), "18sp", "Medium")), color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            Text(message, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).dialogMetricTarget("副标题", DialogElementVisual(LocalBarcodeThemeColors.current.secondary.hexValue(), "15sp", "常规")), color = LocalBarcodeThemeColors.current.secondary, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction(positive, dark, { onConfirm(); dismiss() }, Modifier.padding(start = 8.dp))
            }
        }
    }
}
