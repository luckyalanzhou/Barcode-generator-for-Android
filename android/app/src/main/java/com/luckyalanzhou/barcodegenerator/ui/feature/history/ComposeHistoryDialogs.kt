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

/** 仅负责呈现历史清空确认内容，不直接依赖 Activity 或持久化接口。 */
/** 清空历史记录的二次确认内容；确认后才执行删除回调，取消或关闭弹窗不会改动记录。 */
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
