package com.luckyalanzhou.barcodegenerator

import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import com.luckyalanzhou.barcodegenerator.ui.DebugLog

/** Beta 专用调试日志导出。 */
internal fun MainActivity.shareDebugLogImpl() {
    DebugLog.record("diagnostics", "user requested debug log export")
    val logFile = DebugLog.snapshot(this)
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", logFile)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, logFile.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("应用调试日志", uri)
    }
    startActivity(Intent.createChooser(share, "发送调试日志"))
}
