package com.luckyalanzhou.barcodegenerator

import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider

/** 通过系统分享面板导出测试版的应用内诊断日志。 */
internal fun MainActivity.shareDebugLog() {
    if (!BuildConfig.DEBUG_LOG_EXPORT) return
    DebugLog.record("diagnostics", "user requested debug log export")
    val logFile = DebugLog.snapshot(this)
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, "barcode-generator-debug.log")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("应用调试日志", uri)
    }
    startActivity(Intent.createChooser(share, "发送调试日志"))
}
