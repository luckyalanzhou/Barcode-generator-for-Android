package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Tab 长按菜单呈现状态：退出动画完成前保留菜单锚点，关闭时最多执行一次待处理操作。
 * 若菜单动作会打开系统选择器或对话框，则不恢复 Tab 焦点，避免抢占新界面的焦点。
 */
@Stable
internal class TabMenuPresentation<T>(private val onClosed: (T) -> Unit = {}) {
    var menu by mutableStateOf<T?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var open by mutableStateOf(false)
        private set
    // 选择操作与点击空白使用不同退场轨迹；操作仍在退场结束后执行。
    var actionClosing by mutableStateOf(false)
        private set
    private var pendingAction: (() -> Unit)? = null

    fun show(value: T) {
        pendingAction = null
        actionClosing = false
        ready = false
        menu = value
        open = true
    }

    fun measured() { if (menu != null) ready = true }

    fun dismiss(immediately: Boolean = false, action: (() -> Unit)? = null) {
        if (!open) return
        open = false
        actionClosing = action != null
        pendingAction = action
        if (!ready || immediately) closed()
    }

    fun closed() {
        if (open) return
        val action = pendingAction
        val oldMenu = menu
        pendingAction = null
        menu = null
        ready = false
        actionClosing = false
        // 菜单动作可能打开系统选择器或对话框；此时不恢复 Tab 焦点，避免新界面失去焦点。
        if (oldMenu != null && action == null) onClosed(oldMenu)
        action?.invoke()
    }
}
