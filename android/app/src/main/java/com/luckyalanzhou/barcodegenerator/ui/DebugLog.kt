package com.luckyalanzhou.barcodegenerator

import android.util.Log
import android.content.Context
import java.io.File
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

/** 正式版占位接口；Beta 额外启用持久化诊断日志。 */
object DebugLog {
    private const val TAG = "BarcodeGenerator.DebugLog"
    private const val FALLBACK_FILE_NAME = "debug.log"

    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG_LOG_EXPORT) return
        fallbackTarget = File(context.applicationContext.filesDir, FALLBACK_FILE_NAME)
        try {
            invokeBeta("debugLogInitializeImpl", Context::class.java, context)
        } catch (error: Throwable) {
            Log.e(TAG, "Beta log backend initialization failed", error)
            writeFallback("diagnostics", "日志后端初始化失败", error)
        }
    }

    fun record(tag: String, message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG_LOG_EXPORT) return
        try {
            invokeBeta(
                "debugLogRecordImpl",
                String::class.java, tag,
                String::class.java, message,
                Throwable::class.java, error,
            )
        } catch (backendError: Throwable) {
            Log.e(TAG, "Beta log backend record failed: [$tag] $message", backendError)
            writeFallback(tag, message, error ?: backendError)
        }
    }

    fun snapshot(context: Context): File {
        if (!BuildConfig.DEBUG_LOG_EXPORT) return File(context.cacheDir, "debug.log")
        try {
            val exported = invokeBetaResult("debugLogSnapshotImpl", Context::class.java, context) as? File
            if (exported != null && exported.isFile && exported.length() > 0L) return exported
            throw IllegalStateException("日志后端返回了空文件")
        } catch (error: Throwable) {
            Log.e(TAG, "Beta log backend snapshot failed", error)
            val source = fallbackTarget ?: File(context.applicationContext.filesDir, FALLBACK_FILE_NAME)
            if (!source.exists() || source.length() == 0L) {
                writeFallback("diagnostics", "日志导出后端不可用，已使用兜底日志文件", error, source)
            }
            return File(context.cacheDir, "barcode-generator-debug.log").also { export ->
                source.copyTo(export, overwrite = true)
            }
        }
    }
}

private val fallbackLock = Any()
@Volatile private var fallbackTarget: File? = null

private fun betaClass() = Class.forName("com.luckyalanzhou.barcodegenerator.BetaDebugLogBackendKt")
private fun invokeBeta(name: String, vararg typesAndValues: Any?) {
    invokeBetaResult(name, *typesAndValues)
}

private fun invokeBetaResult(name: String, vararg typesAndValues: Any?): Any? {
    val types = typesAndValues.filterIndexed { index, _ -> index % 2 == 0 }.map { it as Class<*> }.toTypedArray()
    val values = typesAndValues.filterIndexed { index, _ -> index % 2 == 1 }.toTypedArray()
    return betaClass().getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(null, *values)
}

private fun writeFallback(tag: String, message: String, error: Throwable? = null, targetOverride: File? = null) {
    val target = targetOverride ?: fallbackTarget ?: return
    val line = buildString {
        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
            .append(" [").append(tag).append("] ")
            .append(message.replace('\n', ' '))
        error?.let { append(" | ").append(Log.getStackTraceString(it)) }
        append('\n')
    }
    synchronized(fallbackLock) {
        runCatching {
            target.parentFile?.mkdirs()
            target.appendText(line, Charsets.UTF_8)
        }.onFailure { Log.e("BarcodeGenerator.DebugLog", "Fallback log write failed", it) }
    }
}
