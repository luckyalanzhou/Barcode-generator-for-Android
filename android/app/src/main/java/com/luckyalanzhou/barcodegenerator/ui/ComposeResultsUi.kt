package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.domain.CodeItem

import com.luckyalanzhou.barcodegenerator.icons.EditIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteIcon
import com.luckyalanzhou.barcodegenerator.icons.FavoriteFilledIcon
import com.luckyalanzhou.barcodegenerator.icons.IosShareIcon

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt

@Composable
internal fun ComposeResultsPage(
    viewModel: BarcodeViewModel,
    settings: SettingsUiState,
    dark: Boolean,
    onSaveFavorite: () -> Unit,
    onShare: () -> Unit,
) {
    val resultState by viewModel.resultUiState.collectAsStateWithLifecycle()
    val themeColors = LocalBarcodeThemeColors.current
    val primary = themeColors.primary
    val secondary = themeColors.secondary
    val actionColor = themeColors.link
    val items = resultState.items
    val isFavorite = items.isNotEmpty() && items.all { it.favorite }
    val favoriteActionIcon = if (isFavorite) FavoriteFilledIcon else FavoriteIcon

    if (items.isEmpty()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("生成结果", color = primary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Text("暂无生成结果", color = secondary, fontSize = 17.sp, modifier = Modifier.padding(vertical = 40.dp))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!resultState.showingHistoryResult) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    ResultAction(EditIcon, "编辑", actionColor) {
                        viewModel.editCurrentResult()
                    }
                    ResultAction(
                        favoriteActionIcon,
                        "收藏",
                        if (isFavorite) themeColors.favoriteActive else actionColor,
                        onSaveFavorite,
                    )
                    ResultAction(IosShareIcon, "分享", actionColor, onShare)
                }
            }
        }
        items(items, key = { it.id }) { item ->
            ComposeResultBarcode(viewModel, item, primary, settings, dark)
        }
    }
}
@Composable
private fun ResultAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.width(25.dp).height(27.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
internal fun ComposeResultBarcode(
    viewModel: BarcodeViewModel,
    item: CodeItem,
    textColor: Color,
    settings: SettingsUiState,
    dark: Boolean,
) {
    val isCode128 = item.format == "Code 128-B"
    val style = settings.style
    val density = LocalDensity.current.density
    val barHeight = style.barHeight.coerceIn(30, 150).coerceAtLeast(1)
    val barWidth = style.barWidth.roundToInt().coerceIn(120, 360)
    val textSize = style.textSize.coerceIn(10f, 24f)
    val showFormat = style.showFormat
    // 先读取已生成的图片；未命中时才在后台生成并写回，页面导航不等待。
    val displayed by produceState<Bitmap?>(initialValue = null, item, barWidth, barHeight, textSize, showFormat, dark) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            viewModel.loadOrCreateBarcodeImage(item, style, dark, density)
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (displayed == null) {
            // 先保留稳定占位高度，避免后台生成期间列表上下跳动。
            Spacer(Modifier.fillMaxWidth().height(if (isCode128) barHeight.dp else 200.dp))
        } else if (isCode128) {
            Image(
                bitmap = displayed!!.asImageBitmap(),
                contentDescription = "${item.format} 条码",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.width(barWidth.dp).height(barHeight.dp),
            )
            Text(
                text = if (showFormat) "${item.text} · ${item.format}" else item.text,
                color = textColor,
                fontSize = textSize.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            )
        } else {
            Image(
                bitmap = displayed!!.asImageBitmap(),
                contentDescription = "${item.format} 条码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(
                    displayed!!.width.toFloat() / displayed!!.height.toFloat(),
                ),
            )
        }
    }
}

