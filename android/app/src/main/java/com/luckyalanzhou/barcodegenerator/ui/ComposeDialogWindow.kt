package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*

import android.app.Dialog
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/** 公共 Compose 玻璃弹窗窗口与指标面板的基础设施。 */
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
    val elementBounds = mutableStateOf<Map<String, DialogElementBounds>>(emptyMap())
    val elementVisuals = mutableStateOf<Map<String, DialogElementVisual>>(emptyMap())
    val screenWidth = resources.displayMetrics.widthPixels
    val preferredWidth = (screenWidth * if (compact) 0.82f else 0.88f).roundToInt()
    val availableWidth = (screenWidth - dp(24)).coerceAtLeast(1)
    val maxWidth = dp(if (compact) 360 else 400).coerceAtMost(availableWidth)
    val minWidth = dp(280).coerceAtMost(maxWidth)
    val dialogWidth = preferredWidth.coerceIn(minWidth, maxWidth)
    dialog.window?.apply {
        setWindowAnimations(0)
        setGravity(Gravity.CENTER)
        setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }
    composeView.setViewTreeLifecycleOwner(this)
    composeView.setViewTreeSavedStateRegistryOwner(this)
    composeView.setContent {
        val dark = isDark()
        val colors = barcodeThemeColors(dark)
        val colorScheme = if (dark) barcodeDarkColorScheme(colors.background) else barcodeLightColorScheme(colors.background)
        CompositionLocalProvider(LocalBarcodeThemeColors provides colors) {
            MaterialTheme(colorScheme = colorScheme) {
                CompositionLocalProvider(
                    LocalDialogMetric provides { selectedElement.value = it },
                    LocalDialogSelectedElement provides selectedElement,
                    LocalDialogElementBounds provides elementBounds,
                    LocalDialogElementVisuals provides elementVisuals,
                    LocalDialogInspectOnly provides (metricsLabel != null),
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
            setWindowAnimations(0)
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            setGravity(Gravity.CENTER)
        }
        if (metricsLabel != null) metricsDialog = showSimulationMetricsCompose(metricsLabel, selectedElement, elementBounds, elementVisuals)
    }
    dialog.setOnDismissListener { metricsDialog?.dismiss() }
    dialog.show()
}

/** Beta 测试专用指标面板，与实际弹窗同步显示选中元素的布局信息。 */
internal fun MainActivity.showSimulationMetricsCompose(
    label: String,
    selectedElement: MutableState<String>,
    elementBounds: MutableState<Map<String, DialogElementBounds>>,
    elementVisuals: MutableState<Map<String, DialogElementVisual>>,
): Dialog {
    val metricsDialog = Dialog(this)
    val composeView = ComposeView(this)
    composeView.setViewTreeLifecycleOwner(this)
    composeView.setViewTreeSavedStateRegistryOwner(this)
    composeView.setContent {
        val dark = isDark()
        val colors = barcodeThemeColors(dark)
        val density = LocalDensity.current
        val selectedBounds = elementBounds.value[selectedElement.value]
        val cardBounds = elementBounds.value["弹窗卡片"]
        val visual = elementVisuals.value[selectedElement.value]
        fun px(value: Float): Int = with(density) { value.toDp().value.roundToInt() }
        val outerMargins = if (selectedBounds != null && cardBounds != null) {
            listOf(
                px(selectedBounds.left - cardBounds.left),
                px(cardBounds.right - selectedBounds.right),
                px(selectedBounds.top - cardBounds.top),
                px(cardBounds.bottom - selectedBounds.bottom),
            )
        } else null
        CompositionLocalProvider(LocalBarcodeThemeColors provides colors) {
            val colorScheme = if (dark) barcodeDarkColorScheme(colors.background) else barcodeLightColorScheme(colors.background)
            MaterialTheme(colorScheme = colorScheme) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                            .background(colors.input)
                            .border(1.dp, colors.focusedInputBorder, androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("弹窗：$label", color = colors.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text("当前元素：${selectedElement.value}", color = colors.secondary, fontSize = 12.sp)
                        if (selectedBounds != null) {
                            Text("实际位置：左 ${px(selectedBounds.left)}dp，上 ${px(selectedBounds.top)}dp", color = colors.secondary, fontSize = 12.sp)
                            Text("实际尺寸：宽 ${px(selectedBounds.right - selectedBounds.left)}dp，高 ${px(selectedBounds.bottom - selectedBounds.top)}dp", color = colors.secondary, fontSize = 12.sp)
                            if (outerMargins != null) Text("实时外边距：左 ${outerMargins[0]}dp，右 ${outerMargins[1]}dp，上 ${outerMargins[2]}dp，下 ${outerMargins[3]}dp", color = colors.secondary, fontSize = 12.sp)
                        }
                        if (visual != null) {
                            Text("实时文字颜色：${visual.textColor}    字号：${visual.fontSize}    字重：${visual.fontWeight}", color = colors.secondary, fontSize = 12.sp)
                            visual.backgroundColor?.let { Text("实时背景颜色：$it", color = colors.secondary, fontSize = 12.sp) }
                            visual.borderColor?.let { Text("实时边框颜色：$it", color = colors.secondary, fontSize = 12.sp) }
                        }
                        when (selectedElement.value) {
                            "尚未选择元素" -> Text("请点击弹窗标题、副标题、按钮或空白区域查看布局边界", color = colors.secondary, fontSize = 12.sp)
                            "弹窗卡片" -> {
                                Text("元素：整个弹窗卡片", color = colors.secondary, fontSize = 12.sp)
                                Text("距离外边框：左 0dp，右 0dp，上 0dp，下 0dp", color = colors.secondary, fontSize = 12.sp)
                                Text("距离内边框：左 16dp，右 16dp，上 14dp，下 14dp", color = colors.secondary, fontSize = 12.sp)
                                Text("尺寸：宽度自适应，圆角 20dp，轻阴影", color = colors.secondary, fontSize = 12.sp)
                            }
                            "标题" -> {
                                Text("文字：当前弹窗标题", color = colors.secondary, fontSize = 12.sp)
                                Text("距离外边框：左 18dp，右 18dp，上 16dp，下 10dp", color = colors.secondary, fontSize = 12.sp)
                                Text("距离内边框：左 0dp，右 0dp，上 0dp，下 0dp", color = colors.secondary, fontSize = 12.sp)
                                Text("样式：Medium，主文字色，单行文本", color = colors.secondary, fontSize = 12.sp)
                            }
                            "副标题" -> {
                                Text("文字：当前弹窗副标题/说明文字", color = colors.secondary, fontSize = 12.sp)
                                Text("距离外边框：左 18dp，右 18dp，上 10dp，下 16dp", color = colors.secondary, fontSize = 12.sp)
                                Text("距离内边框：左 0dp，右 0dp，上 0dp，下 0dp", color = colors.secondary, fontSize = 12.sp)
                                Text("样式：常规字重，次要文字色，可多行显示", color = colors.secondary, fontSize = 12.sp)
                            }
                            else -> {
                                val action = selectedElement.value.removePrefix("按钮：")
                                Text("按钮文字：$action", color = colors.secondary, fontSize = 12.sp)
                                Text("距离外边框：左 18dp，右 18dp，上 16dp，下 16dp", color = colors.secondary, fontSize = 12.sp)
                                Text("距离内边框：左 10dp，右 10dp，上 7dp，下 7dp", color = colors.secondary, fontSize = 12.sp)
                                Text("按钮间距：相邻按钮之间 8dp", color = colors.secondary, fontSize = 12.sp)
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
        setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        attributes = attributes.apply { dimAmount = 0f; y = (resources.displayMetrics.heightPixels * 0.64f).roundToInt() }
        setLayout(dp(320), WindowManager.LayoutParams.WRAP_CONTENT)
    }
    metricsDialog.show()
    return metricsDialog
}
