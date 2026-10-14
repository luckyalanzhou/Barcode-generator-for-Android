package com.luckyalanzhou.barcodegenerator.ui.component

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuColors
import kotlin.math.max
import kotlin.math.min

/** 将 Compose 布局坐标换算为屏幕坐标，用于菜单锚定和跨组件命中判断。 */
internal fun LayoutCoordinates.boundsOnScreen(): Rect =
    boundsInWindow().translate(localToScreen(Offset.Zero) - localToWindow(Offset.Zero))

/** 保存菜单项当前屏幕范围及选中项，并按可视区域裁剪命中范围。 */
internal class SlideSelectionMenuScope {
    internal data class Entry(val coordinates: LayoutCoordinates, val action: () -> Unit)
    internal val entries = mutableMapOf<Int, Entry>()
    internal var coordinates: LayoutCoordinates? = null
    internal var selected by mutableStateOf<Int?>(null)
    // 松手后保留一次确认高亮，直到退场完成，避免触摸清理提前抹掉反馈。
    internal var confirmed by mutableStateOf<Int?>(null)
    private val hitBounds = linkedMapOf<Int, Rect>()

    fun bounds(): Map<Int, Rect> {
        hitBounds.clear()
        val viewport = coordinates?.takeIf { it.isAttached }?.boundsOnScreen() ?: return hitBounds
        for ((key, entry) in entries) {
            if (!entry.coordinates.isAttached) continue
            val rect = entry.coordinates.boundsOnScreen()
            val clipped = Rect(max(viewport.left, rect.left), max(viewport.top, rect.top),
                min(viewport.right, rect.right), min(viewport.bottom, rect.bottom))
            if (clipped.width > 0f && clipped.height > 0f) hitBounds[key] = clipped
        }
        return hitBounds
    }
}

/** 手势会话负责跟踪原始长按和后续菜单触摸，使拖动选项和普通点击共用一套选中状态。 */
internal class ContextMenuGestureSession {
    val selection = SlideSelectionMenuScope()
    val gesture = MenuSlideSelection()
    var rootCoordinates: LayoutCoordinates? = null
    var pointerDown = false
    var lastPointer = Offset.Zero
    var menuOpen = false
    var ready = false
    var continuation = false
    var moved = false
    var origin = Offset.Zero
    var sourceBounds = Rect.Zero
    var sourceDragMarginPx = 0f
    var lastHapticAt = 0L
    var feedbackPoint by mutableStateOf<Offset?>(null)
        private set

    fun open() {
        selection.entries.clear()
        selection.selected = null
        selection.confirmed = null
        menuOpen = true
        ready = false
        continuation = pointerDown
        // 新菜单每次重新计时，不能继承上一次菜单的触感反馈防抖窗口。
        lastHapticAt = 0L
        moved = false
        origin = lastPointer
        feedbackPoint = null
        if (continuation) gesture.arm() else gesture.cancel()
    }

    fun move(point: Offset, touchSlop: Float, bounds: Map<Int, Rect> = selection.bounds()) {
        if ((point - origin).getDistance() > touchSlop) moved = true
        if (ready) gesture.move(point, bounds)
        if (moved || selection.selected != null || gesture.selected != null) feedbackPoint = point
    }

    fun release(point: Offset, bounds: Map<Int, Rect> = selection.bounds()): ContextMenuRelease {
        val chosen = if (ready) gesture.release(point, bounds) else null
        selection.confirmed = chosen
        val result = when {
            chosen != null -> ContextMenuRelease.Select(chosen)
            // 从来源卡片拖入菜单后松手，只结束来源拖动，不关闭操作菜单。
            continuation -> ContextMenuRelease.KeepOpen
            !ready -> ContextMenuRelease.KeepOpen
            else -> ContextMenuRelease.Dismiss
        }
        cancelTouch()
        return result
    }

    fun cancelTouch() {
        gesture.cancel()
        selection.selected = null
        feedbackPoint = null
        continuation = false
        moved = false
    }

    fun close(preserveConfirmation: Boolean = false) {
        menuOpen = false
        ready = false
        cancelTouch()
        if (!preserveConfirmation) selection.confirmed = null
    }
}

internal sealed interface ContextMenuRelease {
    data class Select(val key: Int) : ContextMenuRelease
    data object KeepOpen : ContextMenuRelease
    data object Dismiss : ContextMenuRelease
}

/**
 * 在根触摸层协调菜单手势：拖动时更新高亮和轻触感，松手命中菜单项时执行动作；
 * 点击来源区域可继续拖动，点击菜单外部才关闭菜单。
 */
@Composable
internal fun Modifier.contextMenuGestures(
    session: ContextMenuGestureSession,
    onDismiss: () -> Unit,
    onAction: (() -> Unit) -> Unit,
): Modifier {
    val dismiss by rememberUpdatedState(onDismiss)
    val action by rememberUpdatedState(onAction)
    val view = LocalView.current
    return onGloballyPositioned { session.rootCoordinates = it }.pointerInput(session) {
        fun point(local: Offset): Offset? = session.rootCoordinates?.takeIf { it.isAttached }?.localToScreen(local)
        fun feedback() {
            val previous = session.selection.selected
            session.selection.selected = session.gesture.selected
            val now = SystemClock.uptimeMillis()
            if (!shouldPerformMenuSelectionHaptic(previous, session.gesture.selected, now, session.lastHapticAt)) return
            session.lastHapticAt = now
            view.performLightMenuHaptic()
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = point(down.position) ?: return@awaitEachGesture
            session.pointerDown = true
            session.lastPointer = start
            session.origin = start
            session.sourceDragMarginPx = 48.dp.toPx()
            if (session.menuOpen && session.ready && !down.isConsumed) {
                if (session.sourceBounds.inflate(session.sourceDragMarginPx).contains(start) &&
                    session.selection.bounds().values.none { it.contains(start) }) {
                    session.continuation = true
                    session.gesture.arm()
                    down.consume()
                } else if (session.gesture.begin(start, session.selection.bounds())) {
                    down.consume()
                    feedback()
                    session.move(start, viewConfiguration.touchSlop)
                }
            }
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val screen = point(change.position) ?: break
                    session.lastPointer = screen
                    if (event.changes.any { it.id != down.id && it.pressed }) {
                        session.cancelTouch()
                        break
                    }
                    if (session.menuOpen && session.gesture.active) {
                        if (change.isConsumed) break
                        change.consume()
                        if (!change.pressed) {
                            when (val release = session.release(screen)) {
                                is ContextMenuRelease.Select -> session.selection.entries[release.key]?.action?.let { action(it) }
                                ContextMenuRelease.Dismiss -> dismiss()
                                ContextMenuRelease.KeepOpen -> Unit
                            }
                            break
                        }
                        session.move(screen, viewConfiguration.touchSlop)
                        feedback()
                    }
                    if (!change.pressed) break
                }
            } finally {
                session.pointerDown = false
                session.cancelTouch()
            }
        }
    }
}

/** 保留键盘与 TalkBack 的点击操作；触屏拖动由根手势层统一处理，避免每个菜单项重复识别。 */
@Composable
internal fun Modifier.slideMenuItem(
    scope: SlideSelectionMenuScope,
    key: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val action by rememberUpdatedState(onClick)
    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val selectionColor = actionMenuColors(colors, colors.surfaces.panel.luminance() < .35f,
        effects.highContrast).selection
    DisposableEffect(key, enabled) { onDispose { scope.entries.remove(key) } }
    return onGloballyPositioned {
        if (enabled) scope.entries[key] = SlideSelectionMenuScope.Entry(it) { action() }
        else scope.entries.remove(key)
    }.drawBehind {
        if (scope.selected == key || scope.confirmed == key) drawRect(selectionColor)
    }
}

/** 记录菜单整体和子项位置，为拖动选择提供可见范围内的稳定命中区域。 */
@Composable
internal fun SlideSelectionMenu(
    scope: SlideSelectionMenuScope,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(SlideSelectionMenuScope) -> Unit,
) {
    DisposableEffect(scope) { onDispose { scope.coordinates = null; scope.entries.clear() } }
    Column(modifier.onGloballyPositioned { scope.coordinates = it }) { content(scope) }
}
