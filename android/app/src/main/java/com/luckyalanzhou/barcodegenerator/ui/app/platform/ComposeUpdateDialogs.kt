package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.presentation.UpdateEvent
import com.luckyalanzhou.barcodegenerator.presentation.update.UpdateViewModel
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import com.luckyalanzhou.barcodegenerator.ui.dialogs.UpdateAvailableDialogContent
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeGlassDialogCard
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeDownloadProgressDialog
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction

import androidx.core.net.toUri
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 安装权限提示使用 Compose；系统设置页和 APK 安装 Intent 仍使用 Android 系统能力。 */
internal fun MainActivity.installApkCompose(file: File) {
    try {
        if (!file.isFile || file.length() == 0L) {
            toast("更新文件不存在，请重新下载")
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            updateViewModel.setPendingInstallPath(file.absolutePath)
            showComposeDialog(
                compact = true,
                onCancel = { updateViewModel.setPendingInstallPath(null) },
            ) { dismiss ->
                val dark = isDark()
                ComposeGlassDialogCard(dark) {
                    Text(
                        "需要允许安装未知应用",
                        color = LocalAppColorScheme.current.text.primary,
                        fontSize = 18.sp,
                    )
                    Text(
                        "为了安装应用更新，请在系统设置中允许“条码生成器”安装未知应用。开启后返回本应用，将自动继续安装。",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = LocalAppColorScheme.current.text.secondary,
                        fontSize = 14.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DialogAction("取消", dark, {
                            updateViewModel.setPendingInstallPath(null)
                            dismiss()
                        })
                        DialogAction(
                            "去设置",
                            dark,
                            {
                                dismiss()
                                runCatching {
                                    startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            "package:$packageName".toUri(),
                                        ),
                                    )
                                }.onFailure { toast("无法打开安装设置") }
                            },
                            modifier = Modifier.padding(start = 20.dp),
                            primary = true,
                        )
                    }
                }
            }
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("APK", uri)
        }
        startActivity(intent)
    } catch (error: Exception) {
        val reason = error.message ?: "未知安装错误"
        settingsViewModel.recordUpdateError(reason)
        toast("安装失败：$reason")
    }
}

internal fun MainActivity.showUpdateAvailableDialogCompose(
    latest: String,
    downloadUrl: String,
    expectedSize: Long?,
    expectedSha256: String?,
) {
    showComposeDialog(
        compact = true,
        onCancel = { updateViewModel.setDialogShowing(false) },
    ) { dismiss ->
        UpdateAvailableDialogContent(
            latest = latest,
            dark = isDark(),
            onLater = {
                updateViewModel.setDialogShowing(false)
                dismiss()
            },
            onUpdate = {
                updateViewModel.setDialogShowing(false)
                dismiss()
                window.decorView.post {
                    DebugLog.record("update", "immediate update clicked; starting download")
                    downloadAndInstallCompose(downloadUrl, expectedSize, expectedSha256)
                }
            },
        )
    }
}

internal fun MainActivity.cancelUpdateDownload() {
    updateViewModel.cancelDownload()
}

internal fun MainActivity.downloadAndInstallCompose(
    apkUrl: String,
    expectedSize: Long? = updateViewModel.uiState.value.expectedSize,
    expectedSha256: String? = updateViewModel.uiState.value.sha256,
) {
    if (updateViewModel.uiState.value.downloadRunning) {
        DebugLog.record("update", "download ignored because another download is running")
        return
    }
    updateViewModel.resetDownloadState()
    var dismissDialog: (() -> Unit)? = null
    lateinit var cancelDownload: () -> Unit
    cancelDownload = {
        cancelUpdateDownload()
        dismissDialog?.invoke()
    }
    showComposeDialog(
        compact = false,
        onCancel = cancelDownload,
    ) { dismiss ->
        val downloadState by updateViewModel.downloadUiState.collectAsStateWithLifecycle()
        dismissDialog = dismiss
        LaunchedEffect(Unit) {
            updateViewModel.events.collect { event ->
                when (event) {
                    is UpdateEvent.DownloadReady -> {
                        dismiss()
                        try {
                            val file = File(event.filePath)
                            updateViewModel.validateDownloadedApk(file)
                            installApkCompose(file)
                        } catch (error: Exception) {
                            showDownloadFailedCompose(
                                apkUrl,
                                expectedSize,
                                expectedSha256,
                                error.message ?: "APK 校验失败",
                            )
                        }
                    }
                    is UpdateEvent.DownloadFailed -> {
                        dismiss()
                        showDownloadFailedCompose(
                            event.apkUrl,
                            event.expectedSize,
                            event.expectedSha256,
                            event.reason,
                        )
                    }
                }
            }
        }
        ComposeDownloadProgressDialog(downloadState, isDark(), cancelDownload)
    }
    DebugLog.record("update", "download dialog shown url=" + apkUrl + " expectedSize=" + expectedSize + " shaPresent=" + (expectedSha256 != null))
    updateViewModel.startDownload(apkUrl, expectedSize, expectedSha256)
}

internal fun MainActivity.showDownloadFailedCompose(
    apkUrl: String,
    expectedSize: Long?,
    expectedSha256: String?,
    reason: String,
) {
    showComposeDialog(compact = true) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text("更新下载失败", color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(reason, Modifier.fillMaxWidth().padding(top = 10.dp), color = LocalAppColorScheme.current.text.secondary, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("重新下载", dark, {
                    dismiss()
                    downloadAndInstallCompose(apkUrl, expectedSize, expectedSha256)
                })
            }
        }
    }
}
