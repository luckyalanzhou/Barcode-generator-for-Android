package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.app.isDark
import com.luckyalanzhou.barcodegenerator.ui.app.showComposeDialog
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeGlassDialogCard
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun MainActivity.showIos26NoticeDialogCompose(message: String) {
    showComposeDialog(compact = true) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(message, modifier = Modifier.fillMaxWidth(), color = LocalAppColorScheme.current.text.primary, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("确定", dark, dismiss)
            }
        }
    }
}

/** App composition adapts the shared confirmation UI to the Activity-owned dialog host. */
internal fun MainActivity.showComposeConfirmDialog(
    title: String,
    message: String,
    positive: String,
    onConfirm: () -> Unit,
) {
    showComposeDialog(compact = false) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(title, color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(
                message,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = LocalAppColorScheme.current.text.secondary,
                fontSize = 15.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, dismiss)
                DialogAction(positive, dark, { onConfirm(); dismiss() }, Modifier.padding(start = 20.dp))
            }
        }
    }
}
