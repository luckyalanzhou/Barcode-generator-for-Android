package com.luckyalanzhou.barcodegenerator

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
internal fun ComposeResultsPage(activity: MainActivity) {
    val dark = activity.isDark()
    val primary = if (dark) Color(0xfff2f4f8) else Color(0xff182230)
    val secondary = if (dark) Color(0xffaeb9c9) else Color(0xff6b7280)
    val actionColor = if (dark) Color(0xffd7e3f5) else Color(0xff2453a6)
    val items = activity.resultItems

    if (items.isEmpty()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("生成结果", color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Text("暂无生成结果", color = secondary, fontSize = 17.sp, modifier = Modifier.padding(vertical = 40.dp))
        }
        return
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!activity.showingHistoryResult) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                ResultAction(R.drawable.ic_action_edit, "编辑", actionColor) {
                    activity.inputDraft = items.map { it.text }.toMutableList()
                    activity.pendingGenerateFormat = items.firstOrNull()?.format
                    activity.page = "generate"
                    activity.render()
                }
                ResultAction(R.drawable.ic_action_favorite, "收藏", actionColor) { activity.saveResultAsFavorite() }
                ResultAction(R.drawable.ic_action_share, "分享", actionColor) { activity.shareResultPage() }
            }
        }

        items.forEachIndexed { index, item ->
            ComposeResultBarcode(activity, item, primary)
            if (index < items.lastIndex && item.format == "Code 128-B" && items[index + 1].format == "Code 128-B" && activity.style.margin > 0) {
                Spacer(Modifier.height(activity.style.margin.coerceAtLeast(0).dp))
            }
        }
    }
}

@Composable
private fun ResultAction(icon: Int, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clickable(onClick = onClick).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.width(25.dp).height(27.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
internal fun ComposeResultBarcode(activity: MainActivity, item: CodeItem, textColor: Color) {
    val encoded = activity.encode(item.text, activity.formats.firstOrNull { it.first == item.format }?.second ?: com.google.zxing.BarcodeFormat.CODE_128)
        ?: return
    val isCode128 = item.format == "Code 128-B"
    val barHeight = activity.style.barHeight.coerceIn(30, 150).coerceAtLeast(1)
    val displayed = if (isCode128) {
        val cropped = trimBarcodeHorizontal(Bitmap.createBitmap(encoded, 0, 0, encoded.width, barHeight.coerceAtMost(encoded.height)))
        addBarcodeQuietZone(cropped)
    } else encoded

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (isCode128) {
            Image(
                bitmap = displayed.asImageBitmap(),
                contentDescription = "${item.format} 条码",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.width(activity.style.barWidth.roundToInt().coerceIn(120, 360).dp).height(barHeight.dp)
            )
            Text(
                text = if (activity.style.showFormat) "${item.text} · ${item.format}" else item.text,
                color = textColor,
                fontSize = activity.style.textSize.coerceIn(10f, 24f).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
            )
        } else {
            Image(
                bitmap = displayed.asImageBitmap(),
                contentDescription = "${item.format} 条码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(displayed.width.toFloat() / displayed.height.toFloat())
            )
        }
    }
}
