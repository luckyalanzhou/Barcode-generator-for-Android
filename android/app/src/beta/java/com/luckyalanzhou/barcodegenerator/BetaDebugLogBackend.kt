package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Beta 专用应用内诊断日志；正式版不包含此实现。 */
private object BetaDebugLogBackend {
    private const val DIRECTORY_NAME = "debug-logs"
    private const val FILE_PREFIX = "debug-"
    private const val FILE_SUFFIX = ".log"
    private const val RETENTION_DAYS = 7L
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var directory: File? = null

    fun initialize(context: Context) {
        val target = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        synchronized(lock) {
            directory = target
            target.mkdirs()
            migrateLegacyFile(context.applicationContext, target)
            cleanup(target)
        }
    }

    fun record(tag: String, message: String, error: Throwable? = null) {
        val targetDirectory = directory ?: return
        val target = dailyFile(targetDirectory)
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
            }.onFailure { Log.e("BarcodeGenerator.DebugLog", "Beta log write failed", it) }
        }
    }

    fun snapshot(context: Context): File {
        val targetDirectory = directory ?: File(context.applicationContext.filesDir, DIRECTORY_NAME).also {
            it.mkdirs()
            directory = it
        }
        synchronized(lock) {
            cleanup(targetDirectory)
            val source = dailyFile(targetDirectory)
            if (!source.exists()) source.writeText("暂无应用调试日志\n", Charsets.UTF_8)
            val export = File(context.cacheDir, "barcode-generator-debug.log")
            source.copyTo(export, overwrite = true)
            return export
        }
    }

    private fun dailyFile(targetDirectory: File): File =
        File(targetDirectory, "$FILE_PREFIX${today()}$FILE_SUFFIX")

    private fun dailyFiles(targetDirectory: File): List<File> = targetDirectory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
        .sortedBy { it.name }

    private fun cleanup(targetDirectory: File) {
        // “超过 7 天”按自然日计算：今天往前第 7 天仍保留，更早的才清理。
        val cutoff = LocalDate.now(ZoneId.systemDefault()).minusDays(RETENTION_DAYS)
        dailyFiles(targetDirectory).forEach { file ->
            val date = file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)
            runCatching { LocalDate.parse(date) }.getOrNull()?.takeIf { it.isBefore(cutoff) }?.let {
                if (!file.delete()) Log.w("BarcodeGenerator.DebugLog", "Could not delete old log: ${file.name}")
            }
        }
    }

    private fun migrateLegacyFile(context: Context, targetDirectory: File) {
        val legacy = File(context.filesDir, "debug.log")
        if (!legacy.isFile) return
        val current = dailyFile(targetDirectory)
        runCatching {
            if (!current.exists()) legacy.copyTo(current)
            legacy.delete()
        }.onFailure { Log.w("BarcodeGenerator.DebugLog", "Could not migrate legacy debug log", it) }
    }

    private fun today(): String = LocalDate.now(ZoneId.systemDefault()).toString()
}

internal fun debugLogInitializeImpl(context: Context) = BetaDebugLogBackend.initialize(context)
internal fun debugLogRecordImpl(tag: String, message: String, error: Throwable?) = BetaDebugLogBackend.record(tag, message, error)
internal fun debugLogSnapshotImpl(context: Context): File = BetaDebugLogBackend.snapshot(context)
