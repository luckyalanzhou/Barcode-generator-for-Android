package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Retain the foreground until the common exit finishes; an action is consumed at most once. */
@Stable
internal class TabMenuPresentation<T> {
    var menu by mutableStateOf<T?>(null)
        private set
    var ready by mutableStateOf(false)
        private set
    var open by mutableStateOf(false)
        private set
    private var pendingAction: (() -> Unit)? = null

    fun show(value: T) {
        pendingAction = null
        ready = false
        menu = value
        open = true
    }

    fun measured() { if (menu != null) ready = true }

    fun dismiss(action: (() -> Unit)? = null) {
        if (!open) return
        open = false
        pendingAction = action
        if (!ready) closed()
    }

    fun closed() {
        if (open) return
        val action = pendingAction
        pendingAction = null
        menu = null
        ready = false
        action?.invoke()
    }
}
