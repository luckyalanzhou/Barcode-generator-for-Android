package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.animation.contextMenuSourceScale
import com.luckyalanzhou.barcodegenerator.ui.animation.contextMenuFocusScale
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuSurface
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuSeparator
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuLineStyle
import com.luckyalanzhou.barcodegenerator.ui.component.menu.menuOptionIconSize
import com.luckyalanzhou.barcodegenerator.ui.component.menu.menuOptionLineHeight
import com.luckyalanzhou.barcodegenerator.ui.component.menu.menuOptionVerticalPadding

import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressAction
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.component.SlideSelectionMenu
import com.luckyalanzhou.barcodegenerator.ui.component.slideMenuItem
import com.luckyalanzhou.barcodegenerator.ui.component.ContextMenuGestureSession
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.luckyalanzhou.barcodegenerator.ui.theme.ActionMenuMetrics
import com.luckyalanzhou.barcodegenerator.ui.theme.MenuMetrics
import com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuColors
import com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuShadowColors
import com.luckyalanzhou.barcodegenerator.ui.theme.actionMenuWidthDp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

/**
 * Tab 长按操作菜单：按 Tab 锚点显示，手指在菜单项上滑动时更新选中反馈，松开后执行当前项。
 * 点击菜单外部或系统返回键关闭菜单；具体操作由调用方传入，菜单只负责手势、动画和呈现。
 */
@Composable
internal fun TabLongPressActionOverlay(
    state: TabLongPressMenuState,
    progress: State<Float>,
    unblurredBottom: Dp,
    previewReady: Boolean,
    actionExit: State<Float>,
    actionClosing: Boolean,
    interactive: Boolean,
    gesture: ContextMenuGestureSession,
    onMeasured: () -> Unit,
    onDismiss: () -> Unit,
    onAction: (() -> Unit) -> Unit,
) {
    val localizedResources = LocalResources.current
    val dark = state.dark
    val anchorBoundsOnScreen = state.anchorBoundsOnScreen
    val menuAnchorBoundsOnScreen = state.menuAnchorBoundsOnScreen
    val focusLabel = state.focusLabel
    val actions = state.actions
    val showTitle = menuShowsTitle(state.title)
    val colors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val frosted = android.os.Build.VERSION.SDK_INT >= 31 && !effects.opaqueGlass && !effects.reduceMotion
    val menuFocus = remember { FocusRequester() }
    val density = LocalDensity.current
    val dragScale = if (state.tabAnchor && !effects.reduceMotion) remember(state) { Animatable(1f) } else null
    if (dragScale != null) {
        val touchSlop = LocalViewConfiguration.current.touchSlop
        val resistance = with(density) { 64.dp.toPx() }
        LaunchedEffect(gesture, dragScale, touchSlop, resistance) {
            snapshotFlow { gesture.sourceDragOffset }.collectLatest { offset ->
                if (offset == null) {
                    dragScale.animateTo(1f, spring(dampingRatio = 1f, stiffness = 450f))
                } else {
                    dragScale.snapTo(tabMenuDragScale(offset, touchSlop, resistance))
                }
            }
        }
    }
    // 普通选项自然测量；标题动态取最高选项高度的 3/4，兼容大字体与换行。
    val optionIconSize = menuOptionIconSize(density)
    val estimatedRowHeightPx = with(density) {
        maxOf(menuOptionLineHeight.toPx(), optionIconSize.toPx()) + menuOptionVerticalPadding.toPx() * 2f
    }
    var measuredRowHeightPx by remember(state, density) { mutableFloatStateOf(estimatedRowHeightPx) }
    val headingHeight = with(density) { (measuredRowHeightPx * (3f / 4f)).toDp() }
    val panelShape = RoundedCornerShape(ActionMenuMetrics.corner)
    val sourceCardShape = remember { RoundedCornerShape(14.dp) }
    val menuColors = actionMenuColors(colors, dark, effects.highContrast)
    val menuShadowColors = actionMenuShadowColors(dark)
    val separator = menuColors.separator
    var overlayOriginOnScreen by remember { mutableStateOf(Offset.Zero) }
    var overlayCoordinatesReady by remember { mutableStateOf(false) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val actionsReady by remember(progress, interactive) {
        derivedStateOf { interactive && progress.value >= .99f }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
            val origin = coordinates.localToScreen(Offset.Zero)
            if (overlayOriginOnScreen != origin) overlayOriginOnScreen = origin
            overlayCoordinatesReady = true
        },
    ) {
            Box(
                Modifier.fillMaxSize()
                    .drawBehind {
                        drawRect(menuBackdropVeil(progress.value, dark, frosted))
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() },
                        indication = null, onClick = onDismiss)
                    .clearAndSetSemantics { },
            )

            val panelWidth = actionMenuWidthDp(maxWidth.value).dp
            val screenWidthPx = with(density) { maxWidth.toPx() }
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val edgePaddingPx = with(density) { 12.dp.toPx() }
            val gapPx = with(density) { 8.dp.toPx() }
            val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
            val bottomInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
            // Keep the pressed Tab raised even when the system disables the transition.
            val focusLift = when {
                state.tabAnchor -> 18.dp
                effects.reduceMotion -> 0.dp
                else -> 12.dp
            }
            val focusLiftPx = with(density) { focusLift.toPx() }
            val desiredHeightPx = (if (showTitle) measuredRowHeightPx * (3f / 4f) else 0f) + actions.size * measuredRowHeightPx
            val rowMenuAnchor = if (state.tabAnchor) menuAnchorBoundsOnScreen else liftedRowMenuAnchor(
                anchorBoundsOnScreen, overlayOriginOnScreen, screenHeightPx, statusBarTopPx,
                bottomInsetPx, gapPx, desiredHeightPx, focusLiftPx)
            val sourceShiftPx = if (state.tabAnchor) 0f else rowMenuAnchor.top - anchorBoundsOnScreen.top
            LaunchedEffect(gesture, rowMenuAnchor, focusLiftPx, actionClosing, effects.reduceMotion) {
                snapshotFlow { progress.value }.collect { value ->
                    // 再次按住时以当前显示中的卡片位置为起点，而非列表中原始行的位置。
                    gesture.sourceBounds = anchorBoundsOnScreen.translate(
                        Offset(0f, (sourceShiftPx - focusLiftPx) *
                            (if (actionClosing || effects.reduceMotion) 1f else value.coerceIn(0f, 1f))))
                }
            }
            val menuSpace = contextMenuSpace(rowMenuAnchor, overlayOriginOnScreen,
                with(density) { maxHeight.toPx() }, statusBarTopPx, bottomInsetPx,
                gapPx, focusLiftPx, desiredHeightPx, state.tabAnchor)
            val placement = if (state.tabAnchor) tabMenuPlacement(rowMenuAnchor, overlayOriginOnScreen, panelSize,
                screenWidthPx, statusBarTopPx, edgePaddingPx, gapPx, focusLiftPx, menuSpace.above, alignTabEdge = true)
            else rowMenuPlacement(rowMenuAnchor.copy(left = menuAnchorBoundsOnScreen.left), overlayOriginOnScreen, panelSize,
                screenWidthPx, statusBarTopPx, edgePaddingPx, gapPx, focusLiftPx, menuSpace.above)
            val popupReady = overlayCoordinatesReady && anchorBoundsOnScreen != Rect.Zero && panelSize != IntSize.Zero
            // The bar is drawn above the blurred page. Keep the panel opaque wherever it could cover those controls.
            val panelFrosted = frosted && popupReady && placement.top + panelSize.height <=
                screenHeightPx - with(density) { unblurredBottom.toPx() }
            LaunchedEffect(popupReady) {
                if (popupReady) {
                    onMeasured() // Measurement starts the reveal; it must not wait for the reveal.
                    gesture.ready = actionsReady
                }
            }
            LaunchedEffect(popupReady, actionsReady) {
                gesture.ready = popupReady && actionsReady
            }
            LaunchedEffect(popupReady, interactive) { if (popupReady && interactive) menuFocus.requestFocus() }
            if (popupReady && previewReady) {
                // 预览只轻微聚焦，尺寸随实际目标适配；不再套用统一的大幅放大比例。
                val focusScale = if (effects.reduceMotion) 1f else contextMenuFocusScale(
                    if (state.tabAnchor) with(density) { 64.dp.toPx() } else anchorBoundsOnScreen.width,
                    anchorBoundsOnScreen.height, with(density) { 2.dp.toPx() })
                val focusWidth = if (state.tabAnchor) with(density) { anchorBoundsOnScreen.width.toDp() } else with(density) {
                    minOf(anchorBoundsOnScreen.width,
                        (screenWidthPx - edgePaddingPx * 2f) / focusScale).toDp()
                }
                val focusHeight = with(density) { anchorBoundsOnScreen.height.toDp() }
                val focusWidthPx = with(density) { focusWidth.toPx() }
                val focusHeightPx = with(density) { focusHeight.toPx() }
                val focusCenterY = (anchorBoundsOnScreen.top + anchorBoundsOnScreen.bottom) / 2f - overlayOriginOnScreen.y
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (anchorBoundsOnScreen.center.x - overlayOriginOnScreen.x - focusWidthPx / 2f).roundToInt(),
                                (focusCenterY - focusHeightPx / 2f).roundToInt(),
                            )
                        }
                        .size(focusWidth, focusHeight)
                        .graphicsLayer {
                            val exit = actionExit.value
                            alpha = when {
                                actionClosing -> 1f - exit
                                 state.tabAnchor -> 1f
                                 else -> (progress.value / .2f).coerceIn(0f, 1f)
                            }
                             val pop = contextMenuSourceScale(progress.value,
                                if (actionClosing) exit else 0f, effects.reduceMotion, focusScale)
                            scaleX = pop
                            scaleY = pop
                            translationY = (sourceShiftPx - focusLiftPx) *
                                 (if (actionClosing || effects.reduceMotion) 1f else progress.value.coerceIn(0f, 1f))
                            if (!state.tabAnchor) {
                                shape = sourceCardShape
                                shadowElevation = 12.dp.toPx() * progress.value.coerceIn(0f, 1f)
                                ambientShadowColor = Color.Black.copy(alpha = if (dark) .24f else .10f)
                                spotShadowColor = Color.Black.copy(alpha = if (dark) .30f else .16f)
                            }
                        }
                        .then(if (state.tabAnchor) Modifier else Modifier
                            // 只有文件夹/收藏文件长按时显示浮起卡片；Tab 菜单不绘制来源卡片外框。
                            .drawWithContent {
                                val p = progress.value.coerceIn(0f, 1f)
                                drawRoundRect(colors.surfaces.card.copy(alpha = p),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
                                drawContent()
                                if (effects.highContrast) drawRoundRect(colors.text.primary.copy(alpha = p),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                                    style = Stroke(1.dp.toPx()))
                            }
                            .clip(sourceCardShape))
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                     state.sourceContent()
                }
            }
            val availableHeightPx = menuSpace.height.coerceAtLeast(with(density) { 48.dp.toPx() })
            val drawPanelChrome: GraphicsLayerScope.() -> Unit = {
                shape = panelShape
                clip = true
                shadowElevation = MenuMetrics.surfaceShadowElevation.toPx() *
                    menuGlassReveal(progress.value, effects.reduceMotion).shadow
                ambientShadowColor = menuShadowColors.ambient
                spotShadowColor = menuShadowColors.spot
            }
            MenuSurface(
                    modifier = Modifier.width(panelWidth)
                        .heightIn(max = with(density) { availableHeightPx.toDp() })
                        .onSizeChanged { if (panelSize != it) panelSize = it }
                        .offset {
                            IntOffset(
                                placement.left.roundToInt(),
                                placement.top.roundToInt(),
                            )
                        }
                        .graphicsLayer {
                             val reveal = menuGlassReveal(progress.value, effects.reduceMotion)
                             alpha = reveal.alpha
                            // 全部选项作为完整面板同时出现，不再依次裁切标题和菜单行。
                            if (dragScale == null) drawPanelChrome()
                            val presentationScale = if (effects.reduceMotion) 1f else if (actionClosing) 1f - .03f * actionExit.value
                                else reveal.scale
                            val pivotY = if (menuSpace.above) 1f else 0f
                            scaleX = presentationScale
                            scaleY = presentationScale
                            transformOrigin = TransformOrigin(placement.pivotX, pivotY)
                             val source = if (actionClosing || effects.reduceMotion) 1f else progress.value.coerceIn(0f, 1f)
                            translationY = -focusLiftPx * source - sourceShiftPx * (1f - source)
                            if (!effects.reduceMotion && !actionClosing) {
                                // 从靠近来源的一侧短距离展开，方向随上下锚定改变。
                                 val remaining = 1f - progress.value.coerceIn(0f, 1f)
                                translationX = (if (state.tabAnchor && placement.anchorCenterX >= screenWidthPx / 2f)
                                    10f else -10f).dp.toPx() * remaining
                                translationY += (if (menuSpace.above) 14f else -14f).dp.toPx() *
                                     remaining
                            }
                        }
                        .then(if (dragScale != null) Modifier.graphicsLayer {
                            // Keep the shadow and clip in the same layer as the dragged panel.
                            drawPanelChrome()
                            scaleX = dragScale.value
                            scaleY = dragScale.value
                            transformOrigin = tabMenuDragOrigin(placement.anchorCenterX < screenWidthPx / 2f)
                        } else Modifier)
                        .focusRequester(menuFocus)
                        .focusable()
                        .onPreviewKeyEvent {
                            if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) { onDismiss(); true } else false
                        }
                        .semantics {
                            paneTitle = localizedResources.getString(R.string.named_action_menu, focusLabel)
                            isTraversalGroup = true
                            dismiss { onDismiss(); true }
                        }
                        .pointerInput(Unit) {
                            // 标题区或空白面板上的点按不能穿透到背景并关闭菜单；仍允许从标题区域开始滚动。
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false).consume()
                                waitForUpOrCancellation()?.consume()
                            }
                        },
                    shape = panelShape,
                    color = colors.surfaces.panel,
                    fillOpacity = if (panelFrosted) {
                        { frostedMenuPanelAlpha(progress.value, dark) }
                    } else null,
                    obscuresBackdropContent = true,
                ) {
                    key(state) {
                    SlideSelectionMenu(gesture.selection, Modifier.verticalScroll(rememberScrollState())) { selection ->
                    if (showTitle) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(headingHeight).padding(horizontal = ActionMenuMetrics.horizontalPadding),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = state.title,
                            color = menuColors.title,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            style = MenuLineStyle,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    MenuSeparator(color = separator)
                    }
                    actions.forEachIndexed { index, action ->
                        if (index > 0) MenuSeparator(color = separator)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .onSizeChanged { measuredRowHeightPx = maxOf(measuredRowHeightPx, it.height.toFloat()) }
                                .slideMenuItem(selection, index, actionsReady, onClick = action.onClick)
                                .clickable(enabled = actionsReady, role = Role.Button) {
                                    selection.confirmed = index
                                    onAction(action.onClick)
                                }
                                .heightIn(min = MenuMetrics.minimumActionItemHeight)
                                .padding(horizontal = ActionMenuMetrics.horizontalPadding, vertical = menuOptionVerticalPadding),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 删除等破坏性操作的文字和图标在浅色、深色主题中都固定使用红色。
                            val actionColor = if (action.icon == DeleteIcon) colors.content.deleteIcon else colors.text.primary
                            Text(
                                text = action.label,
                                modifier = Modifier.weight(1f),
                                color = actionColor,
                                fontSize = MenuMetrics.optionTextSize,
                                lineHeight = menuOptionLineHeight,
                                style = MenuLineStyle,
                            )
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = actionColor,
                                modifier = Modifier.padding(start = ActionMenuMetrics.iconGap).size(optionIconSize),
                            )
                        }
                    }
                    }
                    }
                }
    }
}
