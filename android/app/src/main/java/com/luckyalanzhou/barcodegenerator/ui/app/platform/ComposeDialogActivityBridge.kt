package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.R
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
import android.view.Window
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import android.content.Intent
import android.provider.Settings
import android.net.Uri

internal fun MainActivity.showIos26NoticeDialogCompose(message: String) {
    showComposeDialog(compact = true) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(message, modifier = Modifier.fillMaxWidth(), color = LocalAppColorScheme.current.text.primary, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_ok), dark, dismiss)
            }
        }
    }
}

/** 系统要求先解释用途时显示简短提示，用户确认后才发起权限请求。 */
internal fun MainActivity.showPermissionRationaleDialog(
    permissionLabel: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    showComposeDialog(compact = true, onCancel = onCancel) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(getString(R.string.permission_title, permissionLabel), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(
                getString(R.string.permission_explanation, permissionLabel),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = LocalAppColorScheme.current.text.secondary,
                fontSize = 14.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_cancel), dark, { onCancel(); dismiss() })
                DialogAction(getString(R.string.action_continue), dark, { dismiss(); onContinue() }, Modifier.padding(start = 20.dp), primary = true)
            }
        }
    }
}

/** 永久拒绝后提供明确的系统设置入口，避免再次点击只得到无效权限弹窗。 */
internal fun MainActivity.showPermissionSettingsDialog(permissionLabel: String) {
    showComposeDialog(compact = true) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(getString(R.string.permission_enable_in_settings), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(
                getString(R.string.permission_enable_help, permissionLabel),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                color = LocalAppColorScheme.current.text.secondary,
                fontSize = 14.sp,
            )
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.action_cancel), dark, dismiss)
                DialogAction(
                    getString(R.string.open_settings),
                    dark,
                    {
                        dismiss()
                        try {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", packageName, null)
                            })
                        } catch (error: Exception) {
                            DebugLog.record("permission", "open_app_settings_failed", error)
                            toast(getString(R.string.app_settings_open_failed))
                        }
                    },
                    Modifier.padding(start = 20.dp),
                    primary = true,
                )
            }
        }
    }
}

/** 由 App 组装层将共享确认界面接入 Activity 持有的弹窗宿主。 */
internal fun MainActivity.showComposeConfirmDialog(
    title: String,
    message: String,
    positive: String,
    backdropSourceWindow: Window? = null,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
) {
    showComposeDialog(compact = false, backdropSourceWindow = backdropSourceWindow) { dismiss ->
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
                DialogAction(getString(R.string.action_cancel), dark, dismiss)
                DialogAction(positive, dark, { onConfirm(); dismiss() }, Modifier.padding(start = 20.dp),
                    primary = !destructive, destructive = destructive)
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
                DialogAction(getString(R.string.action_cancel), dark, { if (!confirming) dismiss() })
                DialogAction(
                    if (confirming) getString(R.string.processing) else positive,
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
                                    toast(getString(R.string.action_failed, title))
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
