package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.ui.app.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.presentation.UpdateCheckResult
import com.luckyalanzhou.barcodegenerator.presentation.update.UpdateViewModel
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import com.luckyalanzhou.barcodegenerator.ui.app.AppRoute
import com.luckyalanzhou.barcodegenerator.ui.app.lanshare.closeLanShare
import com.luckyalanzhou.barcodegenerator.ui.app.composeAppShellActions
import com.luckyalanzhou.barcodegenerator.ui.app.showIos26NoticeDialog
import com.luckyalanzhou.barcodegenerator.ui.app.toast

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

/**
 * 更新检查与 APK 安全校验业务。
 *
 * 更新确认、下载进度、失败重试和安装权限界面全部由 ComposeUpdateDialogs.kt 绘制；
 * 本文件只保留网络、版本、签名和文件系统逻辑。
 */
internal fun MainActivity.checkForUpdates() {
    DebugLog.record("update", "manual check started current=${BuildConfig.VERSION_NAME}")
    updateViewModel.startManualCheck()
}

/** Activity 重建后由新实例接收 ViewModel 持有的手动检查结果。 */
internal fun MainActivity.observeManualUpdateCheckResults() {
    lifecycleScope.launch {
        updateViewModel.manualCheckResults.collect { result ->
            when (result) {
                UpdateCheckResult.InProgress -> Unit
                is UpdateCheckResult.Available -> DebugLog.record("update", "latest=${result.version} available=true")
                UpdateCheckResult.UpToDate -> showIos26NoticeDialog("当前已是最新版本")
                is UpdateCheckResult.Failed -> toast(result.reason)
            }
        }
    }
}

