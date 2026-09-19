package com.luckyalanzhou.barcodegenerator.ui.dialogs

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.ui.*

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * 更新检查与 APK 安全校验业务。
 *
 * 更新确认、下载进度、失败重试和安装权限界面全部由 ComposeUpdateDialogs.kt 绘制；
 * 本文件只保留网络、版本、签名和文件系统逻辑。
 */
internal fun MainActivity.checkForUpdates(silent: Boolean = false) {
    DebugLog.record("update", "check started silent=${silent} current=${BuildConfig.VERSION_NAME}")
    lifecycleScope.launch {
        when (val result = viewModel.checkForUpdates()) {
            is UpdateCheckResult.Available -> {
                DebugLog.record("update", "latest=${result.version} available=true")
                if (!viewModel.updateUiState.value.dialogShowing) viewModel.setUpdateDialogShowing(true)
            }
            UpdateCheckResult.UpToDate -> if (!silent) showIos26NoticeDialog("当前已是最新版本")
            is UpdateCheckResult.Failed -> if (!silent) toast(result.reason)
        }
    }
}

