package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.icons.BarcodeIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteIcon
import com.luckyalanzhou.barcodegenerator.icons.HistoryIcon
import com.luckyalanzhou.barcodegenerator.icons.SettingsIcon

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private data class ComposeTabSpec(val label: String, val description: String, val icon: ImageVector)

@Composable
internal fun BarcodeComposeBottomTabBar(selectedIndex: Int, dark: Boolean, onTabSelected: (index: Int, fromSwipe: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val tabs = remember {
        listOf(
            ComposeTabSpec("\u751f\u6210", "\u751f\u6210\u6761\u7801", BarcodeIcon),
            // 历史和收藏只使用线框图标；选中态通过颜色、液态玻璃框和弹簧动画表达。
            ComposeTabSpec("\u5386\u53f2", "\u5386\u53f2\u8bb0\u5f55", HistoryIcon),
            ComposeTabSpec("\u6536\u85cf", "\u6536\u85cf\u5939", FavoriteIcon),
            ComposeTabSpec("\u8bbe\u7f6e", "\u8bbe\u7f6e", SettingsIcon)
        )
    }
    val themeColors = LocalAppColorScheme.current
    val selectedColor = themeColors.controls.accent
    val unselectedColor = themeColors.navigation.tabUnselected
    var dragProgress by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var lastTarget by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex, dragging) {
        if (!dragging) {
            dragProgress = selectedIndex.toFloat()
        }
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
                        onTabSelected(target, true)
                    }
                },
                onDragEnd = {
                    dragging = false
                    onTabSelected(lastTarget, true)
                },
                onDragCancel = { dragging = false }
            )
        }
    ) {
        val tabWidth = (maxWidth - 12.dp) / tabs.size
        val indicatorOffset = (tabWidth + 4.dp) * dragProgress
        Box(
            // 液态玻璃包住完整的图标+文字单元；外层 itemScale 让二者保持同一套动画。
            modifier = Modifier.offset(x = indicatorOffset).width(tabWidth).height(56.dp)
                // 以导航栏左侧为水平基准，避免 Center 先居中后再叠加偏移导致错位。
                .align(Alignment.CenterStart)
                .drawBehind {
                    val inset = 1.5.dp.toPx()
                    val rimTop = themeColors.navigation.tabRimTop
                    val rimBottom = themeColors.navigation.tabRimBottom
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                rimTop,
                                rimBottom,
                            ),
                        ),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        style = Stroke(width = 1.35.dp.toPx()),
                    )
                    drawRoundRect(
                        color = themeColors.navigation.tabHighlight,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = CornerRadius(16.5.dp.toPx()),
                        style = Stroke(width = 0.55.dp.toPx()),
                    )
                }
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedIndex == index
                val itemCenter = (tabWidth + 4.dp) * index + tabWidth / 2
                val glassLeft = indicatorOffset
                val glassRight = indicatorOffset + tabWidth
                val contentHalfWidth = 16.dp
                val contentLeft = itemCenter - contentHalfWidth
                val contentRight = itemCenter + contentHalfWidth
                val glassTouchesContent = dragging && glassRight >= contentLeft && glassLeft <= contentRight
                val itemColor by animateColorAsState(
                    targetValue = if (if (dragging) glassTouchesContent else selected) selectedColor else unselectedColor,
                    animationSpec = tween(100),
                    label = "tab-item-color-$index",
                )
                val itemScale by animateFloatAsState(
                    targetValue = if (glassTouchesContent) 1.12f else 1f,
                    animationSpec = tween(120),
                    label = "tab-item-scale-$index",
                )
                Box(
                    modifier = Modifier.weight(1f).graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                        .pointerInput(index) {
                            detectTapGestures {
                                onTabSelected(index, false)
                            }
                        }.padding(vertical = 3.dp), contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.description,
                            tint = itemColor,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(tab.label, color = itemColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}
