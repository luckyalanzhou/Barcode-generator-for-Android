package com.luckyalanzhou.barcodegenerator.ui.app

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.icons.BarcodeIcon
import com.luckyalanzhou.barcodegenerator.icons.CloudDownloadIcon
import com.luckyalanzhou.barcodegenerator.icons.CloudUploadIcon
import com.luckyalanzhou.barcodegenerator.icons.DeleteIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteIcon
import com.luckyalanzhou.barcodegenerator.icons.HistoryFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.HistoryIcon
import com.luckyalanzhou.barcodegenerator.icons.SettingsFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.SettingsIcon
import com.luckyalanzhou.barcodegenerator.icons.UpgradeIcon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
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
    val selectedIcon: ImageVector = icon,
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
    showSelectionIndicator: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val tabs = remember {
        listOf(
            ComposeTabSpec("\u751f\u6210", "\u751f\u6210\u6761\u7801", BarcodeIcon),
            // 历史、收藏和设置选中时使用各自的填充图标。
            ComposeTabSpec("\u5386\u53f2", "\u5386\u53f2\u8bb0\u5f55", HistoryIcon, HistoryFilledIcon),
            ComposeTabSpec("\u6536\u85cf", "\u6536\u85cf\u5939", FavoriteIcon, FavoriteFilledIcon),
            ComposeTabSpec("\u8bbe\u7f6e", "\u8bbe\u7f6e", SettingsIcon, SettingsFilledIcon)
        )
    }
    val themeColors = LocalAppColorScheme.current
    val selectedColor = themeColors.controls.accent
    val unselectedColor = themeColors.navigation.tabUnselected
    val hapticView = LocalView.current
    var lastTabHapticAt by remember { mutableLongStateOf(0L) }
    var lastTarget by remember { mutableIntStateOf(selectedIndex) }
    var tapPulseTab by remember { mutableIntStateOf(-1) }
    var tapPulseGeneration by remember { mutableIntStateOf(0) }
    val tabBoundsOnScreen = remember { mutableStateListOf(Rect.Zero, Rect.Zero, Rect.Zero, Rect.Zero) }
    val tapScope = rememberCoroutineScope()
    val motion = remember(tabs.size) { TabGlassMotionState(tapScope, selectedIndex, tabs.size) }
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
        tapPulseTab = index
        tapPulseGeneration += 1
        val generation = tapPulseGeneration
        tapScope.launch {
            delay(ComposeAnimationConfig.tabJellyResetDelayMillis)
            if (tapPulseGeneration == generation) tapPulseTab = -1
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .pointerInput(motion) {
                // Observe the real contact location without consuming clicks or long presses.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    motion.press(down.position)
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer != null) motion.updateTouch(pointer.position)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        motion.endPress()
                    }
                }
            }
            .pointerInput(motion) {
                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        motion.beginDrag(position, android.os.SystemClock.uptimeMillis())
                        motion.pulse(.014f)
                        lastTarget = motion.progress.roundToInt().coerceIn(tabs.indices)
                    },
                    onHorizontalDrag = { change, dragAmount ->
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
                    },
                    onDragEnd = {
                        motion.release(lastTarget)
                        motion.pulse(.018f)
                        currentOnTabSelected(lastTarget, true)
                    },
                    onDragCancel = {
                        motion.release(currentSelectedIndex)
                    },
                )
            }
    ) {
        TabLiquidGlassScene(
            motion = motion,
            tabCount = tabs.size,
            accent = selectedColor,
            background = themeColors.surfaces.background,
            visible = showSelectionIndicator,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selected = selectedIndex == index
                    val itemColor by animateColorAsState(
                        targetValue = if (selected) selectedColor else unselectedColor,
                        animationSpec = tween(ComposeAnimationConfig.tabItemColorDurationMillis),
                        label = "tab-item-color-$index",
                    )
                    val tapScale = animateFloatAsState(
                        targetValue = if (tapPulseTab == index) .92f else 1f,
                        animationSpec = ComposeAnimationConfig.pressSpring(),
                        label = "tab-icon-tap-response-$index",
                    )
                    val tabClickModifier = if (index in 1..3) {
                        Modifier.combinedClickable(
                            onClick = { handleTabClick(index) },
                            onLongClick = {
                                hapticView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                                        focusIcon = if (selected) tab.selectedIcon else tab.icon,
                                        focusLabel = tab.label,
                                        focusTint = if (selected) selectedColor else unselectedColor,
                                        dark = dark,
                                        actions = actions,
                                    ),
                                )
                            },
                        )
                    } else {
                        Modifier.clickable(
                            onClick = { handleTabClick(index) },
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .onGloballyPositioned { coordinates ->
                                val topLeft = coordinates.localToScreen(Offset.Zero)
                                val bounds = Rect(
                                    left = topLeft.x,
                                    top = topLeft.y,
                                    right = topLeft.x + coordinates.size.width,
                                    bottom = topLeft.y + coordinates.size.height,
                                )
                                if (tabBoundsOnScreen[index] != bounds) tabBoundsOnScreen[index] = bounds
                            }
                            .then(tabClickModifier)
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = tab.description,
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
