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

internal fun LayoutCoordinates.boundsOnScreen(): Rect =
    boundsInWindow().translate(localToScreen(Offset.Zero) - localToWindow(Offset.Zero))

internal class SlideSelectionMenuScope {
    internal data class Entry(val coordinates: LayoutCoordinates, val action: () -> Unit)
    internal val entries = mutableMapOf<Int, Entry>()
    internal var coordinates: LayoutCoordinates? = null
    internal var selected by mutableStateOf<Int?>(null)
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

/** Root observer owns both the original long-press and subsequent menu touches. */
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
        menuOpen = true
        ready = false
        continuation = pointerDown
        // A new presentation must not inherit the previous menu's debounce window.
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
        val result = when {
            chosen != null -> ContextMenuRelease.Select(chosen)
            // Releasing a source drag ends the drag, not the context-menu presentation.
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

    fun close() {
        menuOpen = false
        ready = false
        cancelTouch()
    }
}

internal sealed interface ContextMenuRelease {
    data class Select(val key: Int) : ContextMenuRelease
    data object KeepOpen : ContextMenuRelease
    data object Dismiss : ContextMenuRelease
}

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

/** Preserve keyboard/TalkBack clicks; pointer events are processed by the root host. */
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
        if (scope.selected == key) drawRect(selectionColor)
    }
}

@Composable
internal fun SlideSelectionMenu(
    scope: SlideSelectionMenuScope,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(SlideSelectionMenuScope) -> Unit,
) {
    DisposableEffect(scope) { onDispose { scope.coordinates = null; scope.entries.clear() } }
    Column(modifier.onGloballyPositioned { scope.coordinates = it }) { content(scope) }
}
