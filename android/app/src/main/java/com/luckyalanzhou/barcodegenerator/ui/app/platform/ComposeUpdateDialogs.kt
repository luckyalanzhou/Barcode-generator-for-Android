package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.domain.UpdateFailureCause
import com.luckyalanzhou.barcodegenerator.domain.UpdateFailure
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.domain.UPDATE_FAILURE_SUMMARY
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
import kotlinx.coroutines.CancellationException

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
internal fun reportApkInstallFailure(
    error: Exception,
    recordUpdateError: (String) -> Unit,
    showToast: (UpdateFailure?) -> Unit,
) {
    recordUpdateError(UPDATE_FAILURE_SUMMARY)
    showToast((error as? UpdateFailureCause)?.failure)
}

internal fun MainActivity.installApkCompose(file: File) {
    try {
        if (!file.isFile || file.length() == 0L) {
            if (updateViewModel.uiState.value.pendingInstallPath == file.absolutePath) {
                updateViewModel.setPendingInstallPath(null)
            }
            installPermissionDialogShowing = false
            toast(getString(R.string.update_file_missing))
            return
        }
        val shareableFile = moveLegacyPendingApkToShareableCache(cacheDir, file)
        if (!packageManager.canRequestPackageInstalls()) {
            updateViewModel.setPendingInstallPath(shareableFile.absolutePath)
            if (installPermissionDialogShowing) return
            installPermissionDialogShowing = true
            showComposeDialog(
                compact = true,
                onCancel = {
                    installPermissionDialogShowing = false
                    updateViewModel.setPendingInstallPath(null)
                },
            ) { dismiss ->
                val dark = isDark()
                ComposeGlassDialogCard(dark) {
                    Text(
                        getString(R.string.update_allow_unknown_apps_title),
                        color = LocalAppColorScheme.current.text.primary,
                        fontSize = 18.sp,
                    )
                    Text(
                        getString(R.string.update_allow_unknown_apps_help),
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = LocalAppColorScheme.current.text.secondary,
                        fontSize = 14.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        DialogAction(getString(R.string.action_cancel), dark, {
                            installPermissionDialogShowing = false
                            updateViewModel.setPendingInstallPath(null)
                            dismiss()
                        })
                        DialogAction(
                            getString(R.string.open_settings),
                            dark,
                            {
                                installPermissionDialogShowing = false
                                dismiss()
                                try {
                                    startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            "package:$packageName".toUri(),
                                        ),
                                    )
                                } catch (_: Exception) {
                                    toast(getString(R.string.update_install_settings_failed))
                                    installApkCompose(shareableFile)
                                }
                            },
                            modifier = Modifier.padding(start = 20.dp),
                            primary = true,
                        )
                    }
                }
            }
            return
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", shareableFile)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("APK", uri)
        }
        startActivity(intent)
    } catch (error: Exception) {
        installPermissionDialogShowing = false
        reportApkInstallFailure(
            error = error,
            recordUpdateError = { settingsViewModel.recordUpdateError(it) },
            showToast = { failure ->
                val reason = failure?.let(resources::updateFailureMessage)
                    ?: getString(R.string.install_unknown_error)
                toast(getString(R.string.install_failed_detail, reason))
            },
        )
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
            // 只有版本忽略状态成功落盘后才关闭宿主 Dialog。
            onIgnore = {
                updateViewModel.ignoreAvailableUpdate(onIgnored = dismiss, onFailure = { toast(getString(it)) })
            },
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
    val request = UpdateDownloadRequest(apkUrl, expectedSize, expectedSha256)
    val currentDownload = updateViewModel.uiState.value
    val isResuming = currentDownload.activeDownloadRequest == request
    if (currentDownload.downloadRunning && !isResuming) {
        DebugLog.record("update", "download ignored because another download is running")
        return
    }
    if (!isResuming) updateViewModel.resetDownloadState()
    var dismissDialog: (() -> Unit)? = null
    lateinit var cancelDownload: () -> Unit
    cancelDownload = {
        updateViewModel.clearActiveDownloadRequest(request)
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
                        updateViewModel.clearActiveDownloadRequest(request)
                        dismiss()
                        try {
                            val file = File(event.filePath)
                            validateDownloadedApkForInstall(file, updateViewModel::validateDownloadedApk)
                            installApkCompose(file)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            showDownloadFailedCompose(
                                apkUrl,
                                expectedSize,
                                expectedSha256,
                                resources.updateFailureMessage((error as? UpdateFailureCause)?.failure ?: UpdateFailure.InvalidApk),
                            )
                        }
                    }
                    is UpdateEvent.DownloadFailed -> {
                        updateViewModel.clearActiveDownloadRequest(
                            UpdateDownloadRequest(event.apkUrl, event.expectedSize, event.expectedSha256),
                        )
                        dismiss()
                        showDownloadFailedCompose(
                            event.apkUrl,
                            event.expectedSize,
                            event.expectedSha256,
                            resources.updateFailureMessage(event.reason),
                        )
                    }
                }
            }
        }
        ComposeDownloadProgressDialog(downloadState, isDark(), cancelDownload)
    }
    DebugLog.record("update", "download dialog shown expectedSize=$expectedSize shaPresent=${expectedSha256 != null}")
    if (!isResuming) updateViewModel.startDownload(apkUrl, expectedSize, expectedSha256)
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
            Text(getString(R.string.update_download_failed), color = LocalAppColorScheme.current.text.primary, fontSize = 18.sp)
            Text(reason, Modifier.fillMaxWidth().padding(top = 10.dp), color = LocalAppColorScheme.current.text.secondary, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                DialogAction(getString(R.string.update_download_again), dark, {
                    dismiss()
                    downloadAndInstallCompose(apkUrl, expectedSize, expectedSha256)
                })
            }
        }
    }
}
