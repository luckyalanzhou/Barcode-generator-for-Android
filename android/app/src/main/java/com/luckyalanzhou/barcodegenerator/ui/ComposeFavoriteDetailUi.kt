package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*

import com.luckyalanzhou.barcodegenerator.ui.AppRoute
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatSavedTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(time))

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposeFavoriteDetailPage(
    viewModel: BarcodeViewModel,
    settings: SettingsUiState,
    dark: Boolean,
    group: FavoriteGroup,
) {
    val anchor = LocalView.current
    val dataState by viewModel.dataState.collectAsStateWithLifecycle()
    val currentGroup = dataState.groups.firstOrNull { it.id == group.id } ?: group
    val groupItems = currentGroup.itemIds.mapNotNull { id -> dataState.items.firstOrNull { it.id == id } }
    val themeColors = LocalBarcodeThemeColors.current
    val primary = themeColors.primary
    val secondary = themeColors.secondary
    val card = themeColors.card

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "favorite-detail-header") {
            FavoriteDetailHeader(
                group = currentGroup,
                primary = primary,
                secondary = secondary,
                onBack = { viewModel.navigateTo(AppRoute.Favorites) },
            )
        }
        if (groupItems.isEmpty()) {
            item(key = "favorite-detail-empty") {
                Text("此收藏暂无条码", color = secondary, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), textAlign = TextAlign.Center)
            }
        } else {
            items(groupItems, key = { it.id }, contentType = { "favorite-barcode" }) { item ->
                Column(
                    Modifier.fillMaxWidth()
                        .globalCardSurface(dark, card, RoundedCornerShape(16.dp), 2.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                viewModel.openFavoriteForEditing(currentGroup)
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    ComposeResultBarcode(viewModel, item, primary, settings, dark)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${item.text}\n${item.format}", color = primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteDetailHeader(
    group: FavoriteGroup,
    primary: Color,
    secondary: Color,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(group.name, color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Text("${group.folder} · 保存于 ${formatSavedTime(group.savedAt)}", color = secondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(14.dp)) { Text("返回收藏", fontSize = 15.sp) }
    }
}
