package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 测试版应用内诊断日志；不读取其他应用的 logcat。 */
object DebugLog {
    private const val FILE_NAME = "debug.log"
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var file: File? = null

    fun initialize(context: Context) {
        if (BuildConfig.DEBUG_LOG_EXPORT) file = File(context.applicationContext.filesDir, FILE_NAME)
    }

    fun record(tag: String, message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG_LOG_EXPORT) return
        val target = file ?: return
        val line = buildString {
            append(formatter.format(Date())).append(" [").append(tag).append("] ")
            append(message.replace('\n', ' '))
            error?.let { append(" | ").append(Log.getStackTraceString(it)) }
            append("\n")
        }
        synchronized(lock) {
            runCatching {
                target.parentFile?.mkdirs()
                if (target.length() > MAX_BYTES) {
                    target.writeText(target.readText(Charsets.UTF_8).takeLast((MAX_BYTES / 2).toInt()), Charsets.UTF_8)
                }
                target.appendText(line, Charsets.UTF_8)
            }
        }
    }

    fun snapshot(context: Context): File {
        val source = file ?: File(context.applicationContext.filesDir, FILE_NAME)
        if (!source.exists()) {
            source.parentFile?.mkdirs()
            source.writeText("暂无应用调试日志\n", Charsets.UTF_8)
        }
        // FileProvider 已允许缓存目录；复制后分享，避免暴露应用私有文件目录。
        val export = File(context.cacheDir, "barcode-generator-debug.log")
        source.copyTo(export, overwrite = true)
        return export
    }
}
