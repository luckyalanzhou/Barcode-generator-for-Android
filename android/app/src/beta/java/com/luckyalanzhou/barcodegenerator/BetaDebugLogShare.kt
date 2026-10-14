package com.luckyalanzhou.barcodegenerator

import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import com.luckyalanzhou.barcodegenerator.ui.app.toast

/** Beta 专用调试日志导出。 */
internal fun MainActivity.shareDebugLogImpl() {
    lifecycleScope.launch {
        try {
            DebugLog.record("diagnostics", "user requested debug log export")
            // 文件扫描和打包放到 IO，系统分享面板仍在主线程启动。
            val logFile = withContext(Dispatchers.IO) { DebugLog.snapshot(this@shareDebugLogImpl) }
            val uri = FileProvider.getUriForFile(this@shareDebugLogImpl, "$packageName.fileprovider", logFile)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, logFile.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("应用调试日志", uri)
            }
            startActivity(Intent.createChooser(share, "发送调试日志"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DebugLog.actionFailed("debug_log_export", error)
            toast("无法导出调试日志，请重试")
        }
    }
}
