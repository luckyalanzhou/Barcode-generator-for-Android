package com.luckyalanzhou.barcodegenerator.ui.app

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.luckyalanzhou.barcodegenerator.ui.component.performLightMenuOpenHaptic
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressAction
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.icons.BarcodeIcon
import com.luckyalanzhou.barcodegenerator.icons.CloudDownloadIcon
import com.luckyalanzhou.barcodegenerator.icons.CloudUploadIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteIcon
import com.luckyalanzhou.barcodegenerator.icons.HistoryIcon
import com.luckyalanzhou.barcodegenerator.icons.SettingsIcon
import com.luckyalanzhou.barcodegenerator.icons.UpgradeIcon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ComposeTabSpec(
    val label: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BarcodeComposeBottomTabBar(
    selectedIndex: Int,
    dark: Boolean,
    onTabSelected: (index: Int, fromSwipe: Boolean) -> Unit,
    onHistoryClear: () -> Unit,
    onFavoritesImport: () -> Unit,
    onFavoritesExport: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onLongPressActionMenuRequested: (TabLongPressMenuState) -> Unit,
    modifier: Modifier = Modifier,
    showSelectionIndicator: Boolean = true,
) {
    val tabs = remember {
        listOf(
            ComposeTabSpec("\u751f\u6210", "\u751f\u6210\u6761\u7801", BarcodeIcon),
            ComposeTabSpec("\u5386\u53f2", "\u5386\u53f2\u8bb0\u5f55", HistoryIcon),
            ComposeTabSpec("\u6536\u85cf", "\u6536\u85cf\u5939", FavoriteIcon),
            ComposeTabSpec("\u8bbe\u7f6e", "\u8bbe\u7f6e", SettingsIcon)
        )
    }
    val themeColors = LocalAppColorScheme.current
    val effects = LocalVisualEffectsPolicy.current
    val selectedColor = if (effects.highContrast) lerp(themeColors.controls.accent, themeColors.text.primary, .55f) else themeColors.controls.accent
    val unselectedColor = if (effects.highContrast) themeColors.text.primary else themeColors.navigation.tabUnselected
    val hapticView = LocalView.current
    val density = LocalDensity.current.density
    var lastTabHapticAt by remember { mutableLongStateOf(0L) }
    var lastTarget by remember { mutableIntStateOf(selectedIndex) }
    var tapPulseTab by remember { mutableIntStateOf(-1) }
    var tapPulseGeneration by remember { mutableIntStateOf(0) }
    val tabBoundsOnScreen = remember { mutableStateListOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero) }
    val tapScope = rememberCoroutineScope()
    val motion = remember(tabs.size) { TabGlassMotionState(tapScope, selectedIndex, tabs.size) }
    val tabFocus = remember(tabs.size) { List(tabs.size) { FocusRequester() } }
    LaunchedEffect(effects.reduceMotion) { motion.setReducedMotion(effects.reduceMotion) }
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnTabSelected by rememberUpdatedState(onTabSelected)
    LaunchedEffect(selectedIndex) { motion.select(selectedIndex) }

    fun performTabSwitchHaptic() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastTabHapticAt < TAB_SWITCH_HAPTIC_MIN_INTERVAL_MS) return
        lastTabHapticAt = now
        hapticView.performSubtleTabHaptic()
    }

    fun handleTabClick(index: Int) {
        if (index != selectedIndex) performTabSwitchHaptic()
        motion.select(index)
        motion.pulse(if (index == selectedIndex) .014f else .03f)
        onTabSelected(index, false)
        tapPulseTab = if (effects.reduceMotion) -1 else index
        tapPulseGeneration += 1
        val generation = tapPulseGeneration
        tapScope.launch {
            delay(ComposeAnimationConfig.tabJellyResetDelayMillis)
            if (tapPulseGeneration == generation) tapPulseTab = -1
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
            .then(if (effects.opaqueGlass) Modifier.background(themeColors.surfaces.background) else Modifier)
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .pointerInput(motion, showSelectionIndicator, density) {
                if (!showSelectionIndicator) return@pointerInput
                // Observe the real contact location without consuming clicks or long presses.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val selectedTab = motion.progress.roundToInt().coerceIn(tabs.indices)
                    val selectedCellContact = tabSelectedCellContains(
                        size.width.toFloat(), size.height.toFloat(), tabs.size, selectedTab, down.position,
                    )
                    if (selectedCellContact) motion.press(down.position)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (selectedCellContact && pointer != null) motion.updateTouch(pointer.position)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        motion.endPress()
                    }
                }
            }
            .pointerInput(motion, showSelectionIndicator, density) {
                // The root menu host owns the continuing long-press pointer.
                if (!showSelectionIndicator) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Eligibility belongs to the original DOWN, never to a later slop position.
                    val selectedTab = motion.progress.roundToInt().coerceIn(tabs.indices)
                    if (!tabSelectedCellContains(
                            size.width.toFloat(), size.height.toFloat(), tabs.size, selectedTab, down.position,
                        )
                    ) return@awaitEachGesture
                    var overSlop = 0f
                    val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        change.consume()
                        overSlop = over
                    } ?: return@awaitEachGesture
                    motion.beginDrag(start.position, start.uptimeMillis)
                    motion.pulse(.014f)
                    lastTarget = motion.progress.roundToInt().coerceIn(tabs.indices)
                    fun move(change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float) {
                        change.consume()
                        val tabWidth = (size.width.toFloat() / tabs.size).coerceAtLeast(1f)
                        motion.drag(dragAmount / tabWidth, change.position, change.uptimeMillis)
                        val target = motion.progress.roundToInt().coerceIn(tabs.indices)
                        if (target != lastTarget) {
                            lastTarget = target
                            motion.pulse(.025f)
                            performTabSwitchHaptic()
                            currentOnTabSelected(target, true)
                        }
                    }
                    move(start, overSlop)
                    val completed = horizontalDrag(start.id) { change ->
                        move(change, change.position.x - change.previousPosition.x)
                    }
                    if (completed) {
                        motion.release(lastTarget)
                        motion.pulse(.018f)
                        currentOnTabSelected(lastTarget, true)
                    } else {
                        motion.release(currentSelectedIndex)
                    }
                }
            }
    ) {
        TabLiquidGlassScene(
            motion = motion,
            tabCount = tabs.size,
            accent = selectedColor,
            background = themeColors.surfaces.background,
            visible = showSelectionIndicator,
        ) { frameProvider ->
            Row(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedIndex == index
                    val interactionSource = remember(index) { MutableInteractionSource() }
                    var tabPlaced by remember { mutableStateOf(false) }
                    DisposableEffect(Unit) { onDispose { tabPlaced = false } }
                    val itemColor = if (!showSelectionIndicator && selected) selectedColor else unselectedColor
                    val tapScale = animateFloatAsState(
                        targetValue = if (!effects.reduceMotion && tapPulseTab == index) .92f else 1f,
                        animationSpec = if (effects.reduceMotion) tween(0) else ComposeAnimationConfig.pressSpring(),
                        label = "tab-icon-tap-response-$index",
                    )
                    val openMenu: () -> Unit = {
                                hapticView.performLightMenuOpenHaptic()
                                val actions = when (index) {
                                    1 -> listOf(
                                        TabLongPressAction(
                                            label = "清空历史记录",
                                            icon = DeleteIcon,
                                            onClick = onHistoryClear,
                                        ),
                                    )
                                    2 -> listOf(
                                        TabLongPressAction(
                                            label = "导入收藏",
                                            icon = CloudDownloadIcon,
                                            onClick = onFavoritesImport,
                                        ),
                                        TabLongPressAction(
                                            label = "导出收藏",
                                            icon = CloudUploadIcon,
                                            onClick = onFavoritesExport,
                                        ),
                                    )
                                    else -> listOf(
                                        TabLongPressAction(
                                            label = "检查更新",
                                            icon = UpgradeIcon,
                                            onClick = onCheckForUpdates,
                                        ),
                                    )
                                }
                                onLongPressActionMenuRequested(
                                    TabLongPressMenuState(
                                        anchorBoundsOnScreen = tabBoundsOnScreen[index],
                                        focusIcon = tab.icon,
                                        focusLabel = tab.label,
                                        focusTint = if (selected) selectedColor else unselectedColor,
                                        dark = dark,
                                        actions = actions,
                                        restoreFocus = { if (tabPlaced) tabFocus[index].requestFocus() },
                                    ),
                                )
                    }
                    val tabClickModifier = if (index in 1..3) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { handleTabClick(index) },
                            onLongClickLabel = "打开${tab.label}操作菜单",
                            onLongClick = openMenu,
                            hapticFeedbackEnabled = false,
                        )
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { handleTabClick(index) },
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .focusRequester(tabFocus[index])
                            .semantics(mergeDescendants = true) {
                                this.selected = selected
                                contentDescription = tab.description
                                if (index in 1..3) customActions = listOf(
                                    CustomAccessibilityAction("打开${tab.label}操作菜单") { openMenu(); true },
                                )
                            }
                            .onGloballyPositioned { coordinates ->
                                tabPlaced = true
                                val topLeft = coordinates.localToScreen(Offset.Zero)
                                val bounds = Rect(
                                    left = topLeft.x,
                                    top = topLeft.y,
                                    right = topLeft.x + coordinates.size.width,
                                    bottom = topLeft.y + coordinates.size.height,
                                )
                                if (tabBoundsOnScreen[index] != bounds) tabBoundsOnScreen[index] = bounds
                            }
                            .then(tabClickModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = itemColor,
                                modifier = Modifier.size(26.dp).graphicsLayer {
                                    val squash = 1f - tapScale.value
                                    scaleX = 1f + squash * .34f
                                    scaleY = tapScale.value
                                    translationY = squash * 12.dp.toPx()
                                },
                            )
                            Text(
                                tab.label,
                                color = itemColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                        if (showSelectionIndicator) {
                            Box(
                                modifier = Modifier.matchParentSize().drawWithContent {
                                    val frame = frameProvider()
                                    val cellWidth = frame.width / tabs.size
                                    val localCapsule = Rect(
                                        left = frame.centerX - frame.halfWidth - index * cellWidth,
                                        top = frame.centerY - frame.halfHeight,
                                        right = frame.centerX + frame.halfWidth - index * cellWidth,
                                        bottom = frame.centerY + frame.halfHeight,
                                    )
                                    val radius = CornerRadius(minOf(frame.halfWidth, frame.halfHeight))
                                    val capsulePath = Path().apply {
                                        addRoundRect(RoundRect(localCapsule, radius))
                                    }
                                    clipPath(capsulePath) { this@drawWithContent.drawContent() }
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = selectedColor,
                                        modifier = Modifier.size(26.dp).graphicsLayer {
                                            val squash = 1f - tapScale.value
                                            scaleX = 1f + squash * .34f
                                            scaleY = tapScale.value
                                            translationY = squash * 12.dp.toPx()
                                        },
                                    )
                                    Text(
                                        tab.label,
                                        color = selectedColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val TAB_SWITCH_HAPTIC_MIN_INTERVAL_MS = 120L

/** Prefer Android's intentionally soft frequent-choice tick, with compatible older-API fallbacks. */
private fun View.performSubtleTabHaptic() {
    val feedback = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
            HapticFeedbackConstants.TEXT_HANDLE_MOVE
        else -> HapticFeedbackConstants.CLOCK_TICK
    }
    performHapticFeedback(feedback)
}
