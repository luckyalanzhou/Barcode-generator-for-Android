package com.luckyalanzhou.barcodegenerator

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ComposeFavoriteDetailPage(activity: MainActivity, group: FavoriteGroup) {
    val dark = activity.isDark()
    val primary = if (dark) Color(0xfff2f4f8) else Color(0xff182230)
    val secondary = if (dark) Color(0xffaeb9c9) else Color(0xff6b7280)
    val card = if (dark) Color(0xff1b222d) else Color(0xfff7f9fc)
    val groupItems = group.itemIds.mapNotNull { id -> activity.items.firstOrNull { it.id == id } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(group.name, color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Text("${group.folder} · 保存于 ${activity.formatSavedTime(group.savedAt)}", color = secondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = { activity.page = "favorites"; activity.showFavoriteGroups() },
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("返回收藏", fontSize = 15.sp) }
        if (groupItems.isEmpty()) {
            Text("此收藏暂无条码", color = secondary, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center)
        } else {
            groupItems.forEach { item ->
                Column(
                    Modifier.fillMaxWidth().background(card, RoundedCornerShape(16.dp)).combinedClickable(
                        onClick = {},
                        onLongClick = { activity.performHapticFeedback(); activity.openFavoriteForEditing(group) }
                    ).padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    ComposeResultBarcode(activity, item, primary)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.text}\n${item.format}", color = primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun MainActivity.performHapticFeedback() {
    window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
}
