package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.MainActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun isValidFavoriteFolderPath(value: String): Boolean {
    val parts = value.split('/')
    return parts.size in 1..2 && parts.all { part ->
        part.isNotBlank() && part != "." && part != ".." && !part.contains('\\')
    }
}

/** History-only confirmation dialogs, isolated from favorite folder editors. */
internal fun MainActivity.showClearHistoryConfirmCompose(onConfirm: () -> Unit) {
    showComposeDialog(compact = false, metricsLabel = null) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(
                "一键清空历史记录",
                color = LocalBarcodeThemeColors.current.primary,
                fontSize = 18.sp,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                DialogAction("取消", dark, dismiss)
                DialogAction(
                    "确定",
                    dark,
                    { onConfirm(); dismiss() },
                    modifier = Modifier.padding(start = 8.dp),
                    destructive = true,
                )
            }
        }
    }
}

internal fun MainActivity.confirmClearCompose(favoritesOnly: Boolean) {
    if (!favoritesOnly) {
        showClearHistoryConfirmCompose { viewModel.clearHistoryAndPersist() }
        return
    }
    showComposeConfirmDialog(
        title = "清空收藏",
        message = "确定删除全部收藏吗？",
        positive = "删除",
    ) {
        viewModel.clearFavoritesAndPersist()
    }
}
