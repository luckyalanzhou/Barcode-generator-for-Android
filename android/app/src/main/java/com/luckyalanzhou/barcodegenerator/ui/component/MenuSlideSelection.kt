package com.luckyalanzhou.barcodegenerator.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Gesture state independent of UI callbacks: only a valid release can choose an action. */
internal class MenuSlideSelection {
    var selected: Int? = null
        private set
    var active: Boolean = false
        private set

    /** Original long-press can start outside the menu and enter a row later. */
    fun arm() {
        active = true
        selected = null
    }

    fun begin(position: Offset, bounds: Map<Int, Rect>): Boolean {
        selected = hit(position, bounds)
        active = selected != null
        return active
    }

    fun move(position: Offset, bounds: Map<Int, Rect>) {
        if (active) selected = hit(position, bounds)
    }

    fun release(position: Offset, bounds: Map<Int, Rect>): Int? {
        val action = if (active) hit(position, bounds) else null
        cancel()
        return action
    }

    fun cancel() {
        selected = null
        active = false
    }

    private fun hit(position: Offset, bounds: Map<Int, Rect>): Int? =
        bounds.entries.firstOrNull { (_, rect) -> rect.contains(position) }?.key
}
