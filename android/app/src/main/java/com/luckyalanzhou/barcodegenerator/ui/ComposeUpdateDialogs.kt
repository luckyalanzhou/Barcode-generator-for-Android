package com.luckyalanzhou.barcodegenerator

import android.net.Uri
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
                metricsLabel = null,
                onCancel = { viewModel.setPendingInstallPath(null) },
            ) { dismiss ->
                val dark = isDark()
                ComposeGlassDialogCard(dark) {
                    Text(
                        "需要允许安装未知应用",
                        color = if (dark) Color(0xfff2f4f8) else Color(0xff182230),
                        fontSize = 18.sp,
                    )
                    Text(
                        "为了安装应用更新，请在系统设置中允许“条码生成器”安装未知应用。开启后返回本应用，将自动继续安装。",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = if (dark) Color(0xffc5cedb) else Color(0xff667085),
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
                                        android.net.Uri.parse("package:$packageName"),
                                    ),
                                )
                            },
                            modifier = Modifier.padding(start = 8.dp),
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
    simulateOnly: Boolean = false,
    showMetrics: Boolean = false,
) {
    showComposeDialog(
        compact = true,
        metricsLabel = if (showMetrics) "发现新版本弹窗" else null,
        onCancel = { viewModel.setUpdateDialogShowing(false) },
    ) { dismiss ->
        UpdateAvailableDialogContent(
            latest = latest,
            dark = isDark(),
            onIgnore = {
                viewModel.clearAvailableUpdate()
                viewModel.setUpdateDialogShowing(false)
                dismiss()
            },
            onLater = {
                viewModel.setUpdateDialogShowing(false)
                dismiss()
            },
            onUpdate = {
                viewModel.setUpdateDialogShowing(false)
                dismiss()
                if (!simulateOnly) {
                    window.decorView.post {
                        DebugLog.record("update", "immediate update clicked; starting download")
                        downloadAndInstallCompose(downloadUrl, expectedSize, expectedSha256)
                    }
                }
            },
        )
    }
}

@Composable
internal fun UpdateAvailableDialogContent(
    latest: String,
    dark: Boolean,
    onIgnore: () -> Unit,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    ComposeGlassDialogCard(dark) {
        Text("发现新版本", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
        Text(
            "检测到版本 $latest，是否立即更新？",
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            color = if (dark) Color(0xffc5cedb) else Color(0xff667085),
            fontSize = 15.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            ComposeUpdateAction("忽略更新", dark, onClick = onIgnore)
            ComposeUpdateAction("稍后更新", dark, Modifier.padding(start = 10.dp), onClick = onLater)
            ComposeUpdateAction("立即更新", dark, Modifier.padding(start = 10.dp), primary = true, onClick = onUpdate)
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

@Composable
private fun ComposeSegmentedProgress(progress: Int, dark: Boolean) {
    val animation = rememberComposeAnimationConfig()
    val animated = animateFloatAsState(
        targetValue = progress.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = animation.progressDurationMillis),
        label = "downloadProgress",
    ).value
    val fill = if (dark) Color(0xff36c8ff) else Color(0xff2678db)
    val track = if (dark) Color(0xff152938) else Color(0xffe4eaf2)
    Canvas(
        modifier = Modifier.fillMaxWidth().height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(track)
            .border(1.dp, fill.copy(alpha = 0.45f), RoundedCornerShape(7.dp)),
    ) {
        val filledWidth = size.width * animated
        if (filledWidth > 0f) {
            val glowWidth = 32.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(fill.copy(alpha = .70f), fill, Color.White.copy(alpha = .78f), fill),
                    startX = (filledWidth - glowWidth).coerceAtLeast(0f),
                    endX = (filledWidth + glowWidth).coerceAtMost(size.width),
                ),
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size((filledWidth - 2.dp.toPx()).coerceAtLeast(0f), size.height - 2.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
        }
        if (animated > 0f && animated < 1f) {
            drawLine(
                color = Color.White.copy(alpha = 0.78f),
                start = Offset(filledWidth, 2.dp.toPx()),
                end = Offset(filledWidth, size.height - 2.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

@Composable
private fun ComposeIndeterminateProgress(dark: Boolean) {
    val fill = if (dark) Color(0xff36c8ff) else Color(0xff2678db)
    val track = if (dark) Color(0xff152938) else Color(0xffe4eaf2)
    val transition = rememberInfiniteTransition(label = "downloadIndeterminate")
    val offset by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "downloadShimmer",
    )
    Canvas(
        Modifier.fillMaxWidth().height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(track)
            .border(1.dp, fill.copy(alpha = .45f), RoundedCornerShape(7.dp)),
    ) {
        val center = size.width * offset
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(fill.copy(alpha = .08f), fill, fill.copy(alpha = .08f)),
                startX = center - 64.dp.toPx(),
                endX = center + 64.dp.toPx(),
            ),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
    }
}

@Composable
private fun ComposeDownloadProgressDialog(
    viewModel: BarcodeViewModel,
    dark: Boolean,
    onCancel: () -> Unit,
) {
    val downloadState by viewModel.updateDownloadUiState.collectAsStateWithLifecycle()
    ComposeGlassDialogCard(dark) {
        Text("下载更新", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
        if (downloadState.indeterminate) {
            ComposeIndeterminateProgress(dark)
        } else {
            ComposeSegmentedProgress(downloadState.progress, dark)
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                downloadState.status,
                modifier = Modifier.weight(1f),
                color = if (dark) Color(0xffc5cedb) else Color(0xff667085),
                fontSize = 14.sp,
                maxLines = 1,
            )
            if (!downloadState.indeterminate) {
                Text(
                    "${downloadState.progress.coerceIn(0, 100)}%",
                    color = if (dark) Color(0xff8fdcff) else Color(0xff2678db),
                    fontSize = 14.sp,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            DialogAction("取消下载", dark, onCancel)
        }
    }
}

internal fun MainActivity.cancelUpdateDownload() {
    viewModel.cancelUpdateDownload()
}

internal fun MainActivity.downloadAndInstallCompose(
    apkUrl: String,
    expectedSize: Long? = viewModel.updateUiState.value.expectedSize,
    expectedSha256: String? = viewModel.updateUiState.value.sha256,
    simulateOnly: Boolean = false,
    showMetrics: Boolean = false,
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
        metricsLabel = if (showMetrics) "下载进度弹窗" else null,
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
    if (simulateOnly) {
        viewModel.setUpdateDownloadProgress(50, false, "已下载 50%（模拟）")
        viewModel.setUpdateDownloadRunning(false)
        return
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
    showComposeDialog(compact = true, metricsLabel = null) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text("更新下载失败", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
            Text(reason, Modifier.fillMaxWidth().padding(top = 10.dp), color = if (dark) Color(0xffc5cedb) else Color(0xff667085), fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction("重新下载", dark, {
                    dismiss()
                    downloadAndInstallCompose(apkUrl, expectedSize, expectedSha256)
                })
            }
        }
    }
}
