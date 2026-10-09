package com.luckyalanzhou.barcodegenerator.ui.support.logging

import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.debugLogInitializeImpl
import com.luckyalanzhou.barcodegenerator.debugLogRecordImpl
import com.luckyalanzhou.barcodegenerator.debugLogSnapshotImpl

import android.util.Log
import android.content.Context
import java.io.File
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** 按构建变体选择日志实现；Beta 使用持久化日志，正式版使用空实现。 */
object DebugLog {
    private const val TAG = "BarcodeGenerator.DebugLog"
    private const val FALLBACK_FILE_NAME = "debug.log"
    private val crashHandlerInstalled = AtomicBoolean(false)
    private val lastAction = AtomicReference("none")

    fun initialize(context: Context) {
        if (!BuildConfig.DEBUG_LOG_EXPORT) return
        fallbackTarget = File(context.applicationContext.filesDir, FALLBACK_FILE_NAME)
        try {
            debugLogInitializeImpl(context)
        } catch (error: Throwable) {
            Log.e(TAG, "Beta log backend initialization failed", error)
            writeFallback("diagnostics", "日志后端初始化失败", error)
        }
    }

    /**
     * Beta-only last-action marker and uncaught-exception capture. The previous
     * process handler is still invoked, so this never hides Android's crash.
     */
    fun installUncaughtExceptionHandler() {
        if (!BuildConfig.DEBUG_LOG_EXPORT || !crashHandlerInstalled.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(
                "crash",
                "uncaught thread=${thread.name} lastAction=${lastAction.get()}",
                error,
            )
            previous?.uncaughtException(thread, error)
        }
    }

    fun actionStarted(name: String, details: String = "") {
        val descriptor = buildActionDescriptor(name, details)
        lastAction.set(descriptor)
        record("action", "start $descriptor")
    }

    fun actionSucceeded(name: String, details: String = "") {
        val descriptor = buildActionDescriptor(name, details)
        lastAction.set(descriptor)
        record("action", "success $descriptor")
    }

    fun actionFailed(name: String, error: Throwable, details: String = "") {
        val descriptor = buildActionDescriptor(name, details)
        lastAction.set(descriptor)
        record("action", "failed $descriptor", error)
    }

    fun lastActionSummary(): String = lastAction.get()

    fun record(tag: String, message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG_LOG_EXPORT) return
        try {
            debugLogRecordImpl(tag, message, error)
        } catch (backendError: Throwable) {
            Log.e(TAG, "Beta log backend record failed: [$tag] $message", backendError)
            writeFallback(tag, message, error ?: backendError)
        }
    }

    private fun buildActionDescriptor(name: String, details: String): String =
        if (details.isBlank()) name else "$name $details"

    fun snapshot(context: Context): File {
        if (!BuildConfig.DEBUG_LOG_EXPORT) return File(context.cacheDir, "debug.log")
        record("diagnostics", "export_environment version=${BuildConfig.VERSION_NAME} versionCode=${BuildConfig.VERSION_CODE} " +
            "sdk=${android.os.Build.VERSION.SDK_INT} manufacturer=${android.os.Build.MANUFACTURER} model=${android.os.Build.MODEL} " +
            "density=${context.resources.displayMetrics.density} fontScale=${context.resources.configuration.fontScale} lastAction=${lastAction.get()}")
        try {
            val exported = debugLogSnapshotImpl(context)
            if (exported.isFile && exported.length() > 0L) return exported
            throw IllegalStateException("日志后端返回了空文件")
        } catch (error: Throwable) {
            Log.e(TAG, "Beta log backend snapshot failed", error)
            val source = fallbackTarget ?: File(context.applicationContext.filesDir, FALLBACK_FILE_NAME)
            writeFallback("diagnostics", "日志导出后端不可用，已使用兜底日志文件", error, source)
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            return File(context.cacheDir, "barcode-generator-debug-$timestamp.log").also { export ->
                export.writeText(buildLogExport(listOf("fallback-debug.log" to source.readText(Charsets.UTF_8)), 8 * 1024 * 1024), Charsets.UTF_8)
            }
        }
    }
}

private val fallbackLock = Any()
@Volatile private var fallbackTarget: File? = null

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
            if (target.length() + line.toByteArray(Charsets.UTF_8).size > 2 * 1024 * 1024) {
                val recent = selectRecentLogRecords((if (target.exists()) target.readText(Charsets.UTF_8) else "") + line, 2 * 1024 * 1024 - 256)
                target.writeText("[diagnostics] fallback_rotation omittedRecords=${recent.omittedRecords}\n" + recent.text, Charsets.UTF_8)
            } else target.appendText(line, Charsets.UTF_8)
        }.onFailure { Log.e("BarcodeGenerator.DebugLog", "Fallback log write failed", it) }
    }
}
