package com.luckyalanzhou.barcodegenerator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class ComposeTabSpec(val label: String, val description: String, val icon: Int, val selectedIcon: Int)

@Composable
internal fun BarcodeComposeBottomTabBar(selectedIndex: Int, dark: Boolean, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val animation = rememberComposeAnimationConfig()
    val tabs = remember {
        listOf(
            ComposeTabSpec("\u751f\u6210", "\u751f\u6210\u6761\u7801", R.drawable.ic_tab_barcode, R.drawable.ic_tab_barcode_selected),
            ComposeTabSpec("\u5386\u53f2", "\u5386\u53f2\u8bb0\u5f55", R.drawable.ic_tab_history, R.drawable.ic_tab_history_selected),
            ComposeTabSpec("\u6536\u85cf", "\u6536\u85cf\u5939", R.drawable.ic_tab_favorite, R.drawable.ic_tab_favorite_selected),
            ComposeTabSpec("\u8bbe\u7f6e", "\u8bbe\u7f6e", R.drawable.ic_tab_settings, R.drawable.ic_tab_settings_selected)
        )
    }
    val selectedColor = if (dark) Color(0xfff4f7ff) else Color(0xff246fc4)
    val unselectedColor = if (dark) Color(0xffc4cada) else Color(0xff64748b)
    var dragProgress by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var lastTarget by remember { mutableIntStateOf(selectedIndex) }
    var hoveredIndex by remember { mutableIntStateOf(-1) }
    var glassPulseKey by remember { mutableIntStateOf(0) }
    val glassScale = remember { Animatable(1f) }

    LaunchedEffect(selectedIndex, dragging) {
        if (!dragging) {
            dragProgress = selectedIndex.toFloat()
            hoveredIndex = -1
        }
    }

    LaunchedEffect(glassPulseKey) {
        if (glassPulseKey == 0) return@LaunchedEffect
        glassScale.snapTo(0.96f)
        glassScale.animateTo(1.045f, animation.bouncySpring())
        glassScale.animateTo(1f, animation.settleSpring())
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(4.dp).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { position ->
                    dragging = true
                    val tabWidth = (size.width - 12.dp.toPx()) / tabs.size
                    val step = tabWidth + 4.dp.toPx()
                    dragProgress = ((position.x - tabWidth / 2f) / step)
                        .coerceIn(0f, (tabs.size - 1).toFloat())
                    lastTarget = dragProgress.roundToInt().coerceIn(tabs.indices)
                    hoveredIndex = lastTarget
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val tabWidth = (size.width - 12.dp.toPx()) / tabs.size
                    val step = tabWidth + 4.dp.toPx()
                    dragProgress = (dragProgress + dragAmount / step)
                        .coerceIn(0f, (tabs.size - 1).toFloat())
                    val target = dragProgress.roundToInt().coerceIn(tabs.indices)
                    if (target != lastTarget) {
                        lastTarget = target
                        hoveredIndex = target
                        glassPulseKey++
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(target)
                    }
                },
                onDragEnd = {
                    dragging = false
                    onTabSelected(lastTarget)
                },
                onDragCancel = { dragging = false }
            )
        }
    ) {
        val tabWidth = (maxWidth - 12.dp) / tabs.size
        val indicatorOffset = (tabWidth + 4.dp) * dragProgress
        val glassShape = RoundedCornerShape(18.dp)
        Box(
            // 液态玻璃包住完整的图标+文字单元；外层 itemScale 让二者保持同一套动画。
            modifier = Modifier.offset(x = indicatorOffset).width(tabWidth).height(52.dp)
                // 以导航栏左侧为水平基准，避免 Center 先居中后再叠加偏移导致错位。
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    scaleX = glassScale.value
                    scaleY = glassScale.value
                    shadowElevation = 10.dp.toPx()
                    shape = glassShape
                    clip = false
                }
                .clip(glassShape)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (dark) 0.42f else 0.88f),
                            Color.White.copy(alpha = if (dark) 0.10f else 0.34f),
                        ),
                    ),
                    shape = glassShape,
                )
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val hovered = dragging && hoveredIndex == index
                val itemColor = if (selected) selectedColor else unselectedColor
                val itemScale = remember { Animatable(if (selected) 1f else 0.96f) }
                var itemInitialized by remember { mutableStateOf(false) }
                LaunchedEffect(selected, hovered) {
                    if (!itemInitialized) {
                        itemScale.snapTo(if (selected) 1f else 0.96f)
                        itemInitialized = true
                    } else if (hovered) {
                        itemScale.snapTo(0.90f)
                        itemScale.animateTo(1.12f, animation.bouncySpring())
                        itemScale.animateTo(1f, animation.settleSpring())
                    } else if (selected) {
                        itemScale.snapTo(0.88f)
                        itemScale.animateTo(1f, animation.bouncySpring())
                    } else {
                        itemScale.animateTo(0.96f, animation.settleSpring())
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).graphicsLayer { scaleX = itemScale.value; scaleY = itemScale.value }
                        .pointerInput(index, selected) {
                            detectTapGestures {
                            if (!selected) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTabSelected(index)
                            }
                        }.padding(vertical = 3.dp), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (index == 3) ComposeSettingsTabIcon(selected, itemColor, animation) else Icon(
                            painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
                            contentDescription = tab.description, tint = itemColor, modifier = Modifier.size(28.dp)
                        )
                        Text(tab.label, color = itemColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposeSettingsTabIcon(selected: Boolean, tint: Color, animation: ComposeAnimationConfig) {
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            progress.snapTo(0f); scale.snapTo(0.5f)
            launch { scale.animateTo(1f, animation.bouncySpring()) }
            progress.animateTo(1f, tween(animation.iconRotationDurationMillis)); progress.animateTo(0f, tween(animation.iconRotationDurationMillis))
        } else {
            progress.animateTo(0f, tween(animation.iconRotationDurationMillis))
            scale.animateTo(1f, animation.settleSpring())
        }
    }
    val angle = progress.value * 45f
    // Use one complete vector layer. The former outer/inner overlay could leave
    // clipped-looking gaps when both layers were scaled and rotated together.
    Icon(
        painter = painterResource(R.drawable.ic_tab_settings_selected),
        contentDescription = "\u8bbe\u7f6e",
        tint = tint,
        modifier = Modifier.size(28.dp).graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = angle
        },
    )
}
