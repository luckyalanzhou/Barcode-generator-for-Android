package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.app.toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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

/** 由 App 组装层将共享确认界面接入 Activity 持有的弹窗宿主。 */
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

/** 持久化型破坏性操作：确认框等写入回执后才关闭，失败时保留重试入口。 */
internal fun MainActivity.showComposeDurableConfirmDialog(
    title: String,
    message: String,
    positive: String,
    onConfirm: suspend () -> Unit,
) {
    var operationInProgress = false
    showComposeDialog(compact = false, canCancel = { !operationInProgress }) { dismiss ->
        val dark = isDark()
        var confirming by remember { mutableStateOf(false) }
        ComposeGlassDialogCard(dark) {
            Text(title, color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(
                message,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = LocalAppColorScheme.current.text.secondary,
                fontSize = 15.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("取消", dark, { if (!confirming) dismiss() })
                DialogAction(
                    if (confirming) "处理中…" else positive,
                    dark,
                    {
                        if (!confirming) {
                            confirming = true
                            operationInProgress = true
                            lifecycleScope.launch {
                                try {
                                    onConfirm()
                                    dismiss()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    DebugLog.record("favorites", "confirmed operation failed: $title", error)
                                    toast("${title}失败，请重试")
                                } finally {
                                    confirming = false
                                    operationInProgress = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(start = 20.dp),
                    destructive = true,
                )
            }
        }
    }
}
