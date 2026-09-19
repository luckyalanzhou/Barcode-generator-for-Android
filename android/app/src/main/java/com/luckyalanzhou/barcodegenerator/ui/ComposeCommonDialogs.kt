package com.luckyalanzhou.barcodegenerator

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

private val LocalDialogMetric = compositionLocalOf<(String) -> Unit> { {} }
private val LocalDialogSelectedElement = compositionLocalOf<MutableState<String>?> { null }

private fun Modifier.dialogMetricBounds(selected: Boolean): Modifier = if (!selected) {
    this
} else {
    drawWithContent {
        drawContent()
        val stroke = 2.dp.toPx()
        val marker = 5.dp.toPx()
        val red = Color.Red
        // 直角布局边界：绘制在内容层之上，不受按钮圆角背景裁切影响。
        drawRect(red, style = Stroke(width = stroke))
        // 四角短标记模拟开发者选项中的布局边界定位线。
        drawLine(red, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(marker, 0f), strokeWidth = stroke)
        drawLine(red, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, marker), strokeWidth = stroke)
        drawLine(red, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - marker, 0f), strokeWidth = stroke)
        drawLine(red, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, marker), strokeWidth = stroke)
        drawLine(red, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(marker, size.height), strokeWidth = stroke)
        drawLine(red, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - marker), strokeWidth = stroke)
        drawLine(red, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - marker, size.height), strokeWidth = stroke)
        drawLine(red, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - marker), strokeWidth = stroke)
    }
}

@Composable
private fun Modifier.dialogMetricTarget(label: String): Modifier {
    val selected = LocalDialogSelectedElement.current?.value == label
    val onMetric = LocalDialogMetric.current
    return dialogMetricBounds(selected)
        .clickable { onMetric(label) }
}

/** 公共 Compose 玻璃弹窗容器；弹窗宽度与旧版保持同一适度范围。 */
internal fun MainActivity.showComposeDialog(
    compact: Boolean,
    metricsLabel: String?,
    onCancel: (() -> Unit)? = null,
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val dialog = Dialog(this)
    val composeView = ComposeView(this)
    var metricsDialog: Dialog? = null
    val selectedElement = mutableStateOf("尚未选择元素")
    val screenWidth = resources.displayMetrics.widthPixels
    val preferredWidth = (screenWidth * if (compact) 0.82f else 0.88f).roundToInt()
    val availableWidth = (screenWidth - dp(24)).coerceAtLeast(1)
    val maxWidth = dp(if (compact) 360 else 400).coerceAtMost(availableWidth)
    val minWidth = dp(280).coerceAtMost(maxWidth)
    val dialogWidth = preferredWidth.coerceIn(minWidth, maxWidth)
    // 在 show() 前完成窗口尺寸配置，避免内容先按默认宽度测量后再跳到目标位置。
    dialog.window?.apply {
        setWindowAnimations(0)
        setGravity(Gravity.CENTER)
        setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    // Dialog 的 decorView 不会自动继承 Activity 的生命周期所有者；显式绑定后，
    // ComposeView 才能安全创建 WindowRecomposer，避免点击编辑项时崩溃。
    composeView.setViewTreeLifecycleOwner(this)
    composeView.setViewTreeSavedStateRegistryOwner(this)
    composeView.setContent {
        val dark = isDark()
        CompositionLocalProvider(LocalBarcodeThemeColors provides barcodeThemeColors(dark)) {
            MaterialTheme {
            CompositionLocalProvider(
                LocalDialogMetric provides { selectedElement.value = it },
                LocalDialogSelectedElement provides selectedElement,
            ) {
                content { dialog.dismiss() }
            }
            }
        }
    }
    dialog.setContentView(composeView)
    dialog.setCanceledOnTouchOutside(true)
    dialog.setOnCancelListener { onCancel?.invoke() }
    dialog.setOnShowListener {
        dialog.window?.apply {
            setDimAmount(barcodeThemeColors(isDark()).dialogDimAmount)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // 取消系统默认的长动画，弹窗显示由 Compose 内容立即接管，避免双重过渡造成卡顿。
            setWindowAnimations(0)
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            setGravity(Gravity.CENTER)
        }
        if (metricsLabel != null) metricsDialog = showSimulationMetricsCompose(metricsLabel, selectedElement)
    }
    dialog.setOnDismissListener { metricsDialog?.dismiss() }
    dialog.show()
}

/**
 * Beta 测试专用指标面板。它使用 Compose 独立窗口显示在实际弹窗下方，
 * 不参与正式业务，也不再通过 PopupWindow/旧 View 树注入控件。
 */
internal fun MainActivity.showSimulationMetricsCompose(label: String, selectedElement: MutableState<String>): Dialog {
    val metricsDialog = Dialog(this)
    val composeView = ComposeView(this)
    composeView.setViewTreeLifecycleOwner(this)
    composeView.setViewTreeSavedStateRegistryOwner(this)
    composeView.setContent {
        val dark = isDark()
        val colors = barcodeThemeColors(dark)
        CompositionLocalProvider(LocalBarcodeThemeColors provides colors) {
            MaterialTheme {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                    .background(colors.input)
                        .border(1.dp, colors.focusedInputBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text("弹窗：$label", color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("当前元素：${selectedElement.value}", color = colors.secondary, fontSize = 12.sp)
                    when (selectedElement.value) {
                        "标题" -> {
                            Text("文字：当前弹窗标题", color = colors.secondary, fontSize = 12.sp)
                            Text("外边距：上 0dp，左右 0dp    字号：18sp", color = colors.secondary, fontSize = 12.sp)
                            Text("样式：Medium，主文字色，单行文本", color = colors.secondary, fontSize = 12.sp)
                        }
                        "副标题" -> {
                            Text("文字：当前弹窗副标题/说明文字", color = colors.secondary, fontSize = 12.sp)
                            Text("外边距：上 10dp，左右 0dp    字号：15sp", color = colors.secondary, fontSize = 12.sp)
                            Text("样式：常规字重，次要文字色，可多行显示", color = colors.secondary, fontSize = 12.sp)
                        }
                        else -> {
                            val action = selectedElement.value.removePrefix("按钮：")
                            Text("按钮文字：$action", color = colors.secondary, fontSize = 12.sp)
                            Text("外边距：上 16dp，按钮间距 8dp    内边距：左右 10dp、上下 7dp", color = colors.secondary, fontSize = 12.sp)
                            Text("尺寸：内容自适应，高度约 40dp    边框：1dp，圆角 12dp", color = colors.secondary, fontSize = 12.sp)
                            Text("样式：按钮色背景，点击反馈，选中为红色直角框", color = colors.secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        }
    }
    metricsDialog.setContentView(composeView)
    metricsDialog.setCanceledOnTouchOutside(false)
    metricsDialog.window?.apply {
        setWindowAnimations(0)
        setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        attributes = attributes.apply { dimAmount = 0f; y = (resources.displayMetrics.heightPixels * 0.64f).roundToInt() }
        setLayout(dp(320), WindowManager.LayoutParams.WRAP_CONTENT)
    }
    metricsDialog.show()
    return metricsDialog
}

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
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.62f).coerceAtLeast(180f).dp
    val maxWidth = (configuration.screenWidthDp - 24).coerceAtLeast(1).dp
    val resolvedMenuWidth = menuWidth?.coerceAtMost(maxWidth)
    val widthModifier = if (menuWidth != null) {
        Modifier.width(resolvedMenuWidth ?: maxWidth)
    } else {
        Modifier.widthIn(max = maxWidth)
    }
    val horizontalOffset = if (alignEndWithAnchor && anchorWidth != null && resolvedMenuWidth != null) {
        anchorWidth - resolvedMenuWidth
    } else {
        0.dp
    }
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
internal fun ComposeGlassDialogCard(
    dark: Boolean,
    horizontalPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val onMetric = LocalDialogMetric.current
    val card = LocalBarcodeThemeColors.current.surface
    Box(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 400.dp)
            .globalCardSurface(dark, card, RoundedCornerShape(20.dp), 2.dp)
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
    val selected = LocalDialogSelectedElement.current?.value == "按钮：$text"
    val foreground = when {
        primary -> colors.onAccent
        destructive -> colors.destructive
        else -> colors.accent
    }
    val border = if (primary) foreground.copy(alpha = 0.62f) else colors.border
    val background = if (primary) {
        colors.progress
    } else colors.button
    Box(
        modifier = modifier
            .globalButtonChrome(RoundedCornerShape(12.dp), 1.dp)
            .dialogMetricBounds(selected)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable { onMetric("按钮：$text"); onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = foreground,
            fontSize = 15.sp,
            maxLines = 1,
            style = LocalTextStyle.current.copy(background = Color.Transparent),
        )
    }
}

@Composable
internal fun ComposeDropdownDivider(dark: Boolean) {
    HorizontalDivider(
        thickness = 1.dp,
        color = LocalBarcodeThemeColors.current.divider,
    )
}

internal fun MainActivity.showIos26NoticeDialogCompose(message: String, showMetrics: Boolean = false) {
    showComposeDialog(compact = true, metricsLabel = if (showMetrics) "提示弹窗" else null) { dismiss ->
        val dark = isDark()
        val onMetric = LocalDialogMetric.current
        ComposeGlassDialogCard(dark) {
            Text(
                message,
                modifier = Modifier.fillMaxWidth().clickable { onMetric("文本") },
                color = LocalBarcodeThemeColors.current.primary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("确定", dark, dismiss, modifier = Modifier)
            }
        }
    }
}

internal fun MainActivity.showSimulatedDialogCompose(
    title: String,
    message: String,
    negative: String?,
    neutral: String?,
    positive: String?,
    showMetrics: Boolean = true,
) {
    showComposeDialog(compact = false, metricsLabel = if (showMetrics) title else null) { dismiss ->
        val dark = isDark()
        val onMetric = LocalDialogMetric.current
        ComposeGlassDialogCard(dark) {
            Text(title, modifier = Modifier.dialogMetricTarget("标题"), color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                message,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).dialogMetricTarget("副标题"),
                color = LocalBarcodeThemeColors.current.secondary,
                fontSize = 15.sp,
            )
            val actions = listOfNotNull(negative, neutral, positive)
            if (actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    actions.forEachIndexed { index, action ->
                        // 测试中心只模拟按钮点击并显示元素数据，不关闭模拟弹窗；点击外部才退出。
                        DialogAction(
                            action,
                            dark,
                            { onMetric("按钮：$action") },
                            modifier = if (index == 0) Modifier else Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 带实际确认回调的 Compose 确认弹窗，供收藏编辑等业务继续复用原确认逻辑。 */
internal fun MainActivity.showComposeConfirmDialog(
    title: String,
    message: String,
    positive: String,
    onConfirm: () -> Unit,
) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        val onMetric = LocalDialogMetric.current
        ComposeGlassDialogCard(dark) {
            Text(title, modifier = Modifier.dialogMetricTarget("标题"), color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(message, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).dialogMetricTarget("副标题"), color = LocalBarcodeThemeColors.current.secondary, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction(
                    positive,
                    dark,
                    { onConfirm(); dismiss() },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
