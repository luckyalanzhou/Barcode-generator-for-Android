package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity

import android.content.Context
import android.app.Dialog
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt

/** 创建 Compose 弹窗使用的平台窗口；独立 seam 允许测试取消策略。 */
internal fun createComposeDialog(context: Context, canCancel: () -> Boolean): Dialog =
    object : Dialog(context) {
        override fun cancel() {
            if (canCancel()) super.cancel()
        }
    }

/** 公共 Compose 弹窗窗口基础设施。 */
internal fun MainActivity.showComposeDialog(
    compact: Boolean,
    onCancel: (() -> Unit)? = null,
    canCancel: () -> Boolean = { true },
    content: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val dialog = createComposeDialog(this, canCancel)
    val composeView = ComposeView(this)
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
        AppTheme(settingsViewModel.style.colorScheme) { content { dialog.dismiss() } }
    }
    dialog.setContentView(composeView)
    dialog.setCanceledOnTouchOutside(true)
    dialog.setOnCancelListener { onCancel?.invoke() }
    dialog.setOnShowListener {
        dialog.window?.apply {
            setDimAmount(appVisualEffects(isDark()).dialogDimAmount)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
            setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            setGravity(Gravity.CENTER)
        }
    }
    dialog.show()
}
