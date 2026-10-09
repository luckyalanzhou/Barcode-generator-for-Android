package com.luckyalanzhou.barcodegenerator.ui.dialogs

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuSurface

/** 来源与菜单边界插值，供整个面板共享同一几何变换。 */
internal fun dropdownMorphBounds(source: Rect, target: Rect, progress: Float): Rect {
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    fun blend(a: Float, b: Float) = a + (b - a) * p
    return Rect(blend(source.left, target.left), blend(source.top, target.top),
        blend(source.right, target.right), blend(source.bottom, target.bottom))
}

internal data class DropdownPanelTransform(
    val scaleX: Float, val scaleY: Float, val translationX: Float, val translationY: Float,
)

/** 外框、文字、分割线共用一个变换，不能只变形外框而另行渐显内容。 */
internal fun dropdownPanelTransform(source: Rect, target: Rect, progress: Float): DropdownPanelTransform {
    if (target.width <= 0f || target.height <= 0f) return DropdownPanelTransform(1f, 1f, 0f, 0f)
    val bounds = dropdownMorphBounds(source, target, progress)
    val sx = bounds.width / target.width
    val sy = bounds.height / target.height
    return DropdownPanelTransform(sx, sy, bounds.left - target.left * sx, bounds.top - target.top * sy)
}

/** 保留 48dp 触控目标，动画来源只取居中的可见按钮外框，不把触控留白当成玻璃。 */
internal fun dropdownVisualSource(anchor: Rect, visualHeight: Float): Rect {
    val height = if (visualHeight.isFinite() && visualHeight > 0f)
        visualHeight.coerceAtMost(anchor.height) else anchor.height
    val inset = (anchor.height - height) / 2f
    return Rect(anchor.left, anchor.top + inset, anchor.right, anchor.bottom - inset)
}

/** 点击菜单：来源边界连续扩展成菜单，取消时沿原轨迹收回；不放大目标、不模糊整屏。 */
@Composable
internal fun CornerDropdownMenu(
    expanded: Boolean, onDismiss: () -> Unit, modifier: Modifier,
    shape: Shape, color: Color, anchorHeight: Dp?, content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    val reduced = LocalVisualEffectsPolicy.current.reduceMotion
    val transition = rememberTransition(state, label = "settings-menu")
    val reveal = transition.animateFloat(transitionSpec = {
        if (reduced) tween(90) else if (targetState) spring(dampingRatio = 1f, stiffness = 420f)
        else tween(180)
    }, label = "button-to-menu-bounds") { if (it) 1f else 0f }
    val position = remember { CornerMenuPositionProvider() }
    val density = LocalDensity.current
    val visualHeight = with(density) { anchorHeight?.toPx() ?: 0f }
    val dismiss by rememberUpdatedState(onDismiss)
    if (state.currentState || state.targetState || !state.isIdle) {
        val reserve = with(density) { position.anchor.height.toDp() }
        Popup(position, onDismiss, PopupProperties(focusable = true)) {
            // 窗口同时包含来源与最终菜单，避免展开初期在 Popup 边缘被裁掉。
            // 来源预留区也是空白关闭区域；菜单项消费自己的点击，不触发这里。
            Box(modifier.pointerInput(Unit) { detectTapGestures { dismiss() } }.graphicsLayer {
                val sourceHeight = position.anchor.height.toFloat()
                val target = Rect(0f, if (position.above) 0f else sourceHeight,
                    size.width, if (position.above) size.height - sourceHeight else size.height)
                val p = reveal.value.coerceIn(0f, 1f)
                val geometry = dropdownPanelTransform(
                    dropdownVisualSource(position.sourceInPopup, visualHeight), target, if (reduced) 1f else p)
                scaleX = geometry.scaleX
                scaleY = geometry.scaleY
                translationX = geometry.translationX
                translationY = geometry.translationY
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = p
            }.padding(top = if (position.above) 0.dp else reserve,
                bottom = if (position.above) reserve else 0.dp)) {
                MenuSurface(shape = shape, color = color) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = content)
                }
            }
        }
    }
}

/** 右侧锚定，空间不足向上；将实际来源与最终菜单放在同一个窗口内。 */
internal class CornerMenuPositionProvider : PopupPositionProvider {
    var above by mutableStateOf(false)
        private set
    var anchor by mutableStateOf(IntRect.Zero)
        private set
    var sourceInPopup by mutableStateOf(Rect.Zero)
        private set
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val menuHeight = (popupContentSize.height - anchorBounds.height).coerceAtLeast(0)
        above = anchorBounds.bottom + menuHeight > windowSize.height && anchorBounds.top >= menuHeight
        anchor = anchorBounds
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (if (above) anchorBounds.bottom - popupContentSize.height else anchorBounds.top)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        sourceInPopup = Rect((anchorBounds.left - x).toFloat(), (anchorBounds.top - y).toFloat(),
            (anchorBounds.right - x).toFloat(), (anchorBounds.bottom - y).toFloat())
        return IntOffset(x, y)
    }
}
