package com.luckyalanzhou.barcodegenerator.ui.feature.history

import com.luckyalanzhou.barcodegenerator.ui.dialogs.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** History-only confirmation content, isolated from Activity and persistence APIs. */
@Composable
internal fun ClearHistoryDialogContent(
    dark: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ComposeGlassDialogCard(dark) {
        Text(
            "一键清空历史记录",
            color = LocalAppColorScheme.current.text.primary,
            fontSize = 18.sp,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            DialogAction("取消", dark, onDismiss)
            DialogAction(
                "确定",
                dark,
                { onConfirm(); onDismiss() },
                modifier = Modifier.padding(start = 20.dp),
                destructive = true,
            )
        }
    }
}
