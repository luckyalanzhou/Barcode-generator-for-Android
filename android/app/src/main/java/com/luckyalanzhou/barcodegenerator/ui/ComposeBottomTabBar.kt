package com.luckyalanzhou.barcodegenerator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class ComposeTabSpec(val label: String, val description: String, val icon: Int, val selectedIcon: Int)

@Composable
internal fun BarcodeComposeBottomTabBar(selectedIndex: Int, dark: Boolean, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
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
    val selectedBackground = if (dark) Color(0xff1b2838).copy(alpha = 0.92f) else Color.White.copy(alpha = 0.86f)
    Row(
        modifier = modifier.fillMaxSize().pointerInput(selectedIndex) {
            var lastTarget = selectedIndex
            detectHorizontalDragGestures(
                onDragStart = { position ->
                    lastTarget = (position.x / size.width * tabs.size).toInt().coerceIn(tabs.indices)
                },
                onHorizontalDrag = { change, _ ->
                    val target = (change.position.x / size.width * tabs.size).toInt().coerceIn(tabs.indices)
                    if (target != lastTarget) {
                        lastTarget = target
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(target)
                    }
                }
            )
        }.padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = selectedIndex == index
            val itemColor = if (selected) selectedColor else unselectedColor
            val itemScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.96f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "tabScale$index"
            )
            Box(
                modifier = Modifier.weight(1f).graphicsLayer { scaleX = itemScale; scaleY = itemScale }
                    .clip(RoundedCornerShape(18.dp)).background(if (selected) selectedBackground else Color.Transparent)
                    .clickable {
                        if (!selected) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTabSelected(index)
                    }.padding(vertical = 3.dp), contentAlignment = Alignment.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (index == 3) ComposeSettingsTabIcon(selected, itemColor) else Icon(
                        painter = painterResource(if (selected) tab.selectedIcon else tab.icon),
                        contentDescription = tab.description, tint = itemColor, modifier = Modifier.size(28.dp)
                    )
                    Text(tab.label, color = itemColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ComposeSettingsTabIcon(selected: Boolean, tint: Color) {
    val progress = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            progress.snapTo(0f); scale.snapTo(0.5f)
            launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) }
            progress.animateTo(1f, tween(180)); progress.animateTo(0f, tween(180))
        } else {
            progress.animateTo(0f, tween(140))
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
        }
    }
    val angle = progress.value * 45f
    Box(Modifier.size(28.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }, contentAlignment = Alignment.Center) {
        Icon(painter = painterResource(R.drawable.ic_tab_settings_outer), contentDescription = "\u8bbe\u7f6e", tint = tint, modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = -angle })
        Icon(painter = painterResource(R.drawable.ic_tab_settings_inner), contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = 0.58f; scaleY = 0.58f; rotationZ = angle
        })
    }
}
