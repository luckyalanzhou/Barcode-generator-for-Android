package com.luckyalanzhou.barcodegenerator.ui.dialogs

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** 外观、字符纠错：以按钮右边缘为锚点，整个菜单向左下展开，收起后才移除窗口。 */
@Composable
internal fun CornerDropdownMenu(
    expanded: Boolean, onDismiss: () -> Unit, modifier: Modifier,
    shape: Shape, color: Color, shadow: Dp, content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    val reduced = LocalVisualEffectsPolicy.current.reduceMotion
    val transition = rememberTransition(state, label = "settings-menu")
    val scale = transition.animateFloat(transitionSpec = {
        if (reduced) tween(90) else spring(dampingRatio = .86f, stiffness = 460f)
    }, label = "whole-panel-scale") { if (it || reduced) 1f else .86f }
    val panelAlpha = transition.animateFloat(transitionSpec = { tween(if (reduced) 90 else 160) },
        label = "panel-opacity") { if (it) 1f else 0f }
    val position = remember { CornerMenuPositionProvider() }
    if (state.currentState || state.targetState || !state.isIdle) {
        Popup(position, onDismiss, PopupProperties(focusable = true)) {
            Surface(modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = panelAlpha.value
                transformOrigin = TransformOrigin(1f, if (position.above) 1f else 0f)
            }, shape = shape, color = color, shadowElevation = shadow) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp), content = content)
            }
        }
    }
}

/** 常规向下，只有下方不足时改为向上；始终保持按钮右侧锚定并避让窗口。 */
internal class CornerMenuPositionProvider : PopupPositionProvider {
    var above by mutableStateOf(false)
        private set
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        above = anchorBounds.bottom + popupContentSize.height > windowSize.height &&
            anchorBounds.top >= popupContentSize.height
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (if (above) anchorBounds.top - popupContentSize.height else anchorBounds.bottom)
            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}
