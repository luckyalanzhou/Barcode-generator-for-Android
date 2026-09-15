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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

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
    // Dialog 的 decorView 不会自动继承 Activity 的生命周期所有者；显式绑定后，
    // ComposeView 才能安全创建 WindowRecomposer，避免点击编辑项时崩溃。
    composeView.setViewTreeLifecycleOwner(this)
    composeView.setViewTreeSavedStateRegistryOwner(this)
    composeView.setContent { content { dialog.dismiss() } }
    dialog.setContentView(composeView)
    dialog.setCanceledOnTouchOutside(true)
    dialog.setOnCancelListener { onCancel?.invoke() }
    dialog.setOnShowListener {
        dialog.window?.apply {
            setDimAmount(if (isDark()) 0.48f else 0.34f)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            setGravity(Gravity.CENTER)
            val screenWidth = resources.displayMetrics.widthPixels
            val preferred = (screenWidth * if (compact) 0.82f else 0.88f).roundToInt()
            val available = (screenWidth - dp(24)).coerceAtLeast(1)
            val maxWidth = dp(if (compact) 360 else 400).coerceAtMost(available)
            val minWidth = dp(280).coerceAtMost(maxWidth)
            setLayout(preferred.coerceIn(minWidth, maxWidth), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        if (metricsLabel != null) metricsDialog = showSimulationMetricsCompose(metricsLabel)
    }
    dialog.setOnDismissListener { metricsDialog?.dismiss() }
    dialog.show()
}

/**
 * Beta 测试专用指标面板。它使用 Compose 独立窗口显示在实际弹窗下方，
 * 不参与正式业务，也不再通过 PopupWindow/旧 View 树注入控件。
 */
internal fun MainActivity.showSimulationMetricsCompose(label: String): Dialog {
    val metricsDialog = Dialog(this)
    val composeView = ComposeView(this)
    composeView.setViewTreeLifecycleOwner(this)
    composeView.setViewTreeSavedStateRegistryOwner(this)
    composeView.setContent {
        val dark = isDark()
        val text = if (dark) Color(0xffc5cedb) else Color(0xff667085)
        val card = if (dark) Color(0xff102234) else Color(0xfff4f8ff)
        val border = if (dark) Color(0xff2d79b6) else Color(0xffb8d7f2)
        SelectionContainer {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(card)
                    .border(1.dp, border, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("弹窗：$label", color = text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("元素数据", color = text, fontSize = 12.sp)
                Text("点击测试弹窗内的任意元素，可在此查看该元素的完整布局数据。", color = text, fontSize = 12.sp)
                Text("位置：由 Compose 窗口布局决定    尺寸：自适应内容", color = text, fontSize = 12.sp)
                Text("内边距：按统一弹窗规范    外观：圆角边框、无重阴影", color = text, fontSize = 12.sp)
                Text("状态：可见=true  可点击=true  可用=true", color = text, fontSize = 12.sp)
            }
        }
    }
    metricsDialog.setContentView(composeView)
    metricsDialog.setCanceledOnTouchOutside(false)
    metricsDialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        attributes = attributes.apply { dimAmount = 0f; y = (resources.displayMetrics.heightPixels * 0.64f).roundToInt() }
        setLayout(dp(320), WindowManager.LayoutParams.WRAP_CONTENT)
    }
    metricsDialog.show()
    metricsDialog.window?.setLayout(dp(320), WindowManager.LayoutParams.WRAP_CONTENT)
    return metricsDialog
}

@Composable
internal fun ComposeGlassDialogCard(
    dark: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val card = if (dark) Color(0xff1c1c1e) else Color(0xfffbfcff)
    val border = if (dark) Color(0xff3a3a3c) else Color(0xffd8d8dc)
    Box(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 400.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(card)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) { Column(content = content) }
}

@Composable
internal fun DialogAction(
    text: String,
    dark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val foreground = if (primary) Color.White else if (dark) Color(0xffb8ccff) else Color(0xff2166d1)
    val border = if (primary) foreground.copy(alpha = 0.62f) else if (dark) Color(0xff52657f) else Color(0xffb7c7df)
    val background = if (primary) {
        if (dark) Color(0xff246fca) else Color(0xff2d7fda)
    } else Color.Transparent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = foreground, fontSize = 15.sp, maxLines = 1) }
}

internal fun MainActivity.showIos26NoticeDialogCompose(message: String, showMetrics: Boolean = false) {
    showComposeDialog(compact = true, metricsLabel = if (showMetrics) "提示弹窗" else null) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(
                message,
                modifier = Modifier.fillMaxWidth(),
                color = if (dark) Color(0xfff2f4f8) else Color(0xff182230),
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
        ComposeGlassDialogCard(dark) {
            Text(title, color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(
                message,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = if (dark) Color(0xffc5cedb) else Color(0xff667085),
                fontSize = 15.sp,
            )
            val actions = listOfNotNull(negative, neutral, positive)
            if (actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    actions.forEachIndexed { index, action ->
                        DialogAction(action, dark, dismiss, modifier = if (index == 0) Modifier else Modifier.padding(start = 8.dp))
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
        ComposeGlassDialogCard(dark) {
            Text(title, color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(message, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), color = if (dark) Color(0xffc5cedb) else Color(0xff667085), fontSize = 15.sp)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction(positive, dark, { onConfirm(); dismiss() }, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
