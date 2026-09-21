package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.BarcodeViewModel
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.UpdateEvent
import com.luckyalanzhou.barcodegenerator.ui.rememberComposeAnimationConfig

import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween

/** 安装权限提示使用 Compose；系统设置页和 APK 安装 Intent 仍使用 Android 系统能力。 */
internal fun MainActivity.installApkCompose(file: File) {
    try {
        if (!file.isFile || file.length() == 0L) {
            toast("更新文件不存在，请重新下载")
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            viewModel.setPendingInstallPath(file.absolutePath)
            showComposeDialog(
                compact = true,
                onCancel = { viewModel.setPendingInstallPath(null) },
            ) { dismiss ->
                val dark = isDark()
                ComposeGlassDialogCard(dark) {
                    Text(
                        "需要允许安装未知应用",
                        color = LocalBarcodeThemeColors.current.primary,
                        fontSize = 18.sp,
                    )
                    Text(
                        "为了安装应用更新，请在系统设置中允许“条码生成器”安装未知应用。开启后返回本应用，将自动继续安装。",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = LocalBarcodeThemeColors.current.secondary,
                        fontSize = 14.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DialogAction("取消", dark, {
                            viewModel.setPendingInstallPath(null)
                            dismiss()
                        })
                        DialogAction(
                            "去设置",
                            dark,
                            {
                                dismiss()
                                startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        "package:$packageName".toUri(),
                                    ),
                                )
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
        onCancel = { viewModel.setUpdateDialogShowing(false) },
    ) { dismiss ->
        UpdateAvailableDialogContent(
            latest = latest,
            dark = isDark(),
            onLater = {
                viewModel.setUpdateDialogShowing(false)
                dismiss()
            },
            onUpdate = {
                viewModel.setUpdateDialogShowing(false)
                dismiss()
                window.decorView.post {
                    DebugLog.record("update", "immediate update clicked; starting download")
                    downloadAndInstallCompose(downloadUrl, expectedSize, expectedSha256)
                }
            },
        )
    }
}

@Composable
internal fun UpdateAvailableDialogContent(
    latest: String,
    dark: Boolean,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    ComposeGlassDialogCard(dark, horizontalPadding = 14.dp) {
        Text("发现新版本", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
        Text(
            "检测到版本 $latest，是否立即更新？",
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            color = LocalBarcodeThemeColors.current.secondary,
            fontSize = 15.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ComposeUpdateAction("稍后更新", dark, onClick = onLater)
            ComposeUpdateAction("立即更新", dark, primary = true, onClick = onUpdate)
        }
    }
}

@Composable
private fun ComposeUpdateAction(
    text: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    DialogAction(
        text = text,
        dark = dark,
        onClick = onClick,
        modifier = modifier,
        primary = primary,
    )
}

internal fun MainActivity.cancelUpdateDownload() {
    viewModel.cancelUpdateDownload()
}

internal fun MainActivity.downloadAndInstallCompose(
    apkUrl: String,
    expectedSize: Long? = viewModel.updateUiState.value.expectedSize,
    expectedSha256: String? = viewModel.updateUiState.value.sha256,
) {
    if (viewModel.updateUiState.value.downloadRunning) {
        DebugLog.record("update", "download ignored because another download is running")
        return
    }
    viewModel.resetUpdateDownloadState()
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
        dismissDialog = dismiss
        LaunchedEffect(Unit) {
            viewModel.updateEvents.collect { event ->
                when (event) {
                    is UpdateEvent.DownloadReady -> {
                        dismiss()
                        try {
                            val file = File(event.filePath)
                            viewModel.validateDownloadedApk(file)
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
        ComposeDownloadProgressDialog(viewModel, isDark(), cancelDownload)
    }
    DebugLog.record("update", "download dialog shown url=" + apkUrl + " expectedSize=" + expectedSize + " shaPresent=" + (expectedSha256 != null))
    viewModel.startUpdateDownload(apkUrl, expectedSize, expectedSha256)
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
            Text("更新下载失败", color = LocalBarcodeThemeColors.current.primary, fontSize = 18.sp)
            Text(reason, Modifier.fillMaxWidth().padding(top = 10.dp), color = LocalBarcodeThemeColors.current.secondary, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("重新下载", dark, {
                    dismiss()
                    downloadAndInstallCompose(apkUrl, expectedSize, expectedSha256)
                })
            }
        }
    }
}
