package com.luckyalanzhou.barcodegenerator

import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 安装权限提示使用 Compose；系统设置页和 APK 安装 Intent 仍使用 Android 系统能力。 */
internal fun MainActivity.installApkCompose(file: File) {
    try {
        if (!file.isFile || file.length() == 0L) {
            toast("更新文件不存在，请重新下载")
            return
        }
        if (!packageManager.canRequestPackageInstalls()) {
            viewModel.pendingInstallPath = file.absolutePath
            showComposeDialog(
                compact = true,
                metricsLabel = null,
                onCancel = { viewModel.pendingInstallPath = null },
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
                            viewModel.pendingInstallPath = null
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
        if (intent.resolveActivity(packageManager) == null) {
            toast("未找到可用的安装程序")
            return
        }
        startActivity(intent)
    } catch (error: Exception) {
        val reason = error.message ?: "未知安装错误"
        settingsStore.setUpdateError(reason)
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
        onCancel = { updateDialogShowing = false },
    ) { dismiss ->
        val dark = isDark()
        ComposeGlassDialogCard(dark) {
            Text(
                "发现新版本",
                color = if (dark) Color(0xfff2f4f8) else Color(0xff182230),
                fontSize = 20.sp,
            )
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
                ComposeUpdateAction("忽略更新", dark) {
                    availableUpdateUrl = null
                    updateDialogShowing = false
                    dismiss()
                    if (page == "settings") render()
                }
                ComposeUpdateAction("稍后更新", dark, Modifier.padding(start = 10.dp)) {
                    updateDialogShowing = false
                    dismiss()
                }
                ComposeUpdateAction("立即更新", dark, Modifier.padding(start = 10.dp), primary = true) {
                    updateDialogShowing = false
                    dismiss()
                    if (!simulateOnly) {
                        window.decorView.post {
                            DebugLog.record("update", "immediate update clicked; starting download")
                            downloadAndInstall(downloadUrl, expectedSize, expectedSha256)
                        }
                    }
                }
            }
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
        modifier = Modifier.fillMaxWidth().height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(track)
            .border(1.dp, fill.copy(alpha = 0.78f), RoundedCornerShape(9.dp)),
    ) {
        val filledWidth = size.width * animated
        if (filledWidth > 0f) {
            val glowWidth = 42.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(fill.copy(alpha = .72f), fill, Color.White.copy(alpha = .92f), fill),
                    startX = (filledWidth - glowWidth).coerceAtLeast(0f),
                    endX = (filledWidth + glowWidth).coerceAtMost(size.width),
                ),
                topLeft = Offset(1.dp.toPx(), 2.dp.toPx()),
                size = Size((filledWidth - 2.dp.toPx()).coerceAtLeast(0f), size.height - 4.dp.toPx()),
                cornerRadius = CornerRadius(5.dp.toPx()),
            )
        }
        if (animated > 0f && animated < 1f) {
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(filledWidth, 2.dp.toPx()),
                end = Offset(filledWidth, size.height - 2.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
            )
        }
    }
}

@Composable
private fun ComposeDownloadProgressDialog(
    progress: MutableState<Int>,
    indeterminate: MutableState<Boolean>,
    status: MutableState<String>,
    dark: Boolean,
    onCancel: () -> Unit,
) {
    ComposeGlassDialogCard(dark) {
        Text("下载更新", color = if (dark) Color(0xfff2f4f8) else Color(0xff182230), fontSize = 20.sp)
        if (indeterminate.value) {
            Box(
                Modifier.fillMaxWidth().height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (dark) Color(0xff152938) else Color(0xffe4eaf2))
                    .border(1.dp, if (dark) Color(0xff6b7280) else Color(0xffb8c0cc), RoundedCornerShape(9.dp)),
            )
        } else {
            ComposeSegmentedProgress(progress.value, dark)
        }
        Text(
            status.value,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            color = if (dark) Color(0xffc5cedb) else Color(0xff667085),
            fontSize = 14.sp,
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
            DialogAction("取消下载", dark, onCancel)
        }
    }
}

internal fun MainActivity.cancelUpdateDownload() {
    viewModel.updateDownloadGeneration++
    viewModel.updateDownloadJob?.cancel()
    viewModel.updateDownloadJob = null
    updateDownloadRunning = false
}

internal fun MainActivity.downloadAndInstallCompose(
    apkUrl: String,
    expectedSize: Long? = availableUpdateExpectedSize,
    expectedSha256: String? = availableUpdateSha256,
    simulateOnly: Boolean = false,
    showMetrics: Boolean = false,
) {
    if (updateDownloadRunning) {
        DebugLog.record("update", "download ignored because another download is running")
        return
    }
    val downloadGeneration = ++viewModel.updateDownloadGeneration
    updateDownloadRunning = true
    val progress = mutableIntStateOf(0)
    val indeterminate = mutableStateOf(false)
    val status = mutableStateOf("准备下载…")
    var dismissDialog: (() -> Unit)? = null
    var cancelled = false
    lateinit var cancelDownload: () -> Unit
    cancelDownload = {
        if (!cancelled) {
            cancelled = true
            cancelUpdateDownload()
            dismissDialog?.invoke()
        }
    }
    showComposeDialog(
        compact = false,
        metricsLabel = if (showMetrics) "下载进度弹窗" else null,
        onCancel = cancelDownload,
    ) { dismiss ->
        dismissDialog = dismiss
        ComposeDownloadProgressDialog(progress, indeterminate, status, isDark(), cancelDownload)
    }
    if (simulateOnly) {
        progress.value = 50
        status.value = "已下载 50%（模拟）"
        updateDownloadRunning = false
        return
    }
    DebugLog.record("update", "download dialog shown url=" + apkUrl + " expectedSize=" + expectedSize + " shaPresent=" + (expectedSha256 != null))
    viewModel.updateDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
        val temp = File(cacheDir, "barcode-generator-update.apk.part")
        val official = File(cacheDir, "barcode-generator-update.apk")
        var connection: HttpURLConnection? = null
        try {
            val limit = UpdateSecurity.MAX_APK_DOWNLOAD_BYTES
            require(expectedSha256 != null) { "该版本缺少 SHA-256 校验信息，无法安全更新" }
            require(expectedSize == null || expectedSize <= limit) { "更新包超过 500 MB 限制" }
            require(Uri.parse(apkUrl).scheme.equals("https", ignoreCase = true)) { "更新包必须使用 HTTPS 下载" }
            connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection!!.apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BarcodeGenerator/" + BuildConfig.VERSION_NAME)
            }
            require(connection!!.responseCode in 200..299) { "HTTP " + connection!!.responseCode }
            DebugLog.record("update", "download response=" + connection!!.responseCode + " contentLength=" + connection!!.contentLengthLong)
            val total = connection!!.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            require(total == null || total <= limit) { "更新包超过 500 MB 限制" }
            temp.delete()
            connection!!.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var done = 0L
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        ensureActive()
                        require(done + count <= limit) { "更新包超过 500 MB 限制" }
                        output.write(buffer, 0, count)
                        done += count
                        withContext(Dispatchers.Main) {
                            if (total != null) {
                                indeterminate.value = false
                                progress.value = (done * 100 / total).toInt().coerceIn(0, 100)
                                status.value = "已下载 " + progress.value + "%"
                            } else {
                                indeterminate.value = true
                                status.value = "正在下载… " + (done / 1024) + " KB"
                            }
                        }
                    }
                }
            }
            require(temp.isFile && temp.length() > 0L) { "APK 为空" }
            require(expectedSize == null || temp.length() == expectedSize) {
                "文件大小校验失败：" + temp.length() + " / " + expectedSize
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val actual = temp.inputStream().use { input ->
                val buffer = ByteArray(16 * 1024)
                var count: Int
                while (input.read(buffer).also { count = it } != -1) digest.update(buffer, 0, count)
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            require(actual.equals(expectedSha256, true)) { "SHA-256 校验失败" }
            validateDownloadedApk(temp)
            DebugLog.record("update", "download validated size=" + temp.length())
            official.delete()
            require(temp.renameTo(official)) { "无法保存更新文件" }
            cacheDir.listFiles()?.filter { it.name.startsWith("barcode-generator-update") && it != official }?.forEach { it.delete() }
            withContext(Dispatchers.Main) {
                dismissDialog?.invoke()
                installApk(official)
            }
        } catch (error: Exception) {
            DebugLog.record("update", "download failed", error)
            temp.delete()
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                dismissDialog?.invoke()
                if (error !is kotlinx.coroutines.CancellationException) {
                    showDownloadFailedCompose(apkUrl, expectedSize, expectedSha256, error.message ?: "未知错误")
                }
            }
        } finally {
            connection?.disconnect()
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (viewModel.updateDownloadGeneration == downloadGeneration) {
                    updateDownloadRunning = false
                    viewModel.updateDownloadJob = null
                }
            }
        }
    }
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
