package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.luckyalanzhou.barcodegenerator.ui.support.logging.selectRecentLogRecords
import com.luckyalanzhou.barcodegenerator.ui.support.logging.buildLogExport

/** Beta 专用应用内诊断日志；正式版不包含此实现。 */
private object BetaDebugLogBackend {
    private const val DIRECTORY_NAME = "debug-logs"
    private const val FILE_PREFIX = "debug-"
    private const val FILE_SUFFIX = ".log"
    private const val RETENTION_DAYS = 7L
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private const val MAX_EXPORT_BYTES = 8L * 1024L * 1024L
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
        synchronized(lock) {
            val targetDirectory = directory ?: error("Beta log backend not initialized")
            val target = dailyFile(targetDirectory)
            val line = buildString {
                // SimpleDateFormat 非线程安全，格式化必须与写入共用锁。
                append(formatter.format(Date())).append(" [").append(tag).append("] ")
                append(message.replace('\n', ' '))
                error?.let { append(" | ").append(Log.getStackTraceString(it)) }
                append("\n")
            }
            target.parentFile?.mkdirs()
            if (target.length() + line.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                val recent = selectRecentLogRecords((if (target.exists()) target.readText(Charsets.UTF_8) else "") + line, (MAX_BYTES - 256).toInt())
                replaceLog(target, "${formatter.format(Date())} [diagnostics] rotation omittedRecords=${recent.omittedRecords}\n" + recent.text)
            } else {
                target.appendText(line, Charsets.UTF_8)
            }
            // 写入失败向上传递，由 DebugLog 写兜底文件，不能静默丢日志。
        }
    }

    fun snapshot(context: Context): File {
        val targetDirectory = directory ?: File(context.applicationContext.filesDir, DIRECTORY_NAME).also {
            it.mkdirs()
            directory = it
        }
        synchronized(lock) {
            cleanup(targetDirectory)
            // 最新日志优先，避免导出大小达到上限时丢掉最近一次崩溃上下文。
            val fallback = File(context.applicationContext.filesDir, "debug.log")
            val sources = (listOf(fallback).filter { it.isFile && it.length() > 0 } + dailyFiles(targetDirectory))
                .sortedByDescending { it.lastModified() }
            val export = File(context.cacheDir, "barcode-generator-debug-${exportTimestamp()}.log")
            val readable = sources.map { file ->
                file.name to try {
                    file.readText(Charsets.UTF_8)
                } catch (error: Exception) {
                    // 单个日志损坏不能导致其余日期全部丢失，导出中保留失败说明。
                    "${formatter.format(Date())} [diagnostics] source_read_failed file=${file.name} | ${Log.getStackTraceString(error)}\n"
                }
            }
            export.writeText(buildLogExport(readable, MAX_EXPORT_BYTES.toInt()), Charsets.UTF_8)
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
            // 当当天日志已存在时也合并旧文件，不能直接删除未迁移的异常。
            val merged = legacy.readText(Charsets.UTF_8) + (if (current.exists()) current.readText(Charsets.UTF_8) else "")
            val recent = selectRecentLogRecords(merged, (MAX_BYTES - 256).toInt())
            replaceLog(current, "${formatter.format(Date())} [diagnostics] legacy_migration omittedRecords=${recent.omittedRecords}\n" + recent.text)
            check(legacy.delete()) { "Could not delete migrated legacy log" }
        }.onFailure { Log.w("BarcodeGenerator.DebugLog", "Could not migrate legacy debug log", it) }
    }

    private fun today(): String = LocalDate.now(ZoneId.systemDefault()).toString()

    /** 轮转或迁移失败时恢复旧文件，不能先清空再写导致整天日志丢失。 */
    private fun replaceLog(file: File, text: String) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun exportTimestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
}

internal fun debugLogInitializeImpl(context: Context) = BetaDebugLogBackend.initialize(context)
internal fun debugLogRecordImpl(tag: String, message: String, error: Throwable?) = BetaDebugLogBackend.record(tag, message, error)
internal fun debugLogSnapshotImpl(context: Context): File = BetaDebugLogBackend.snapshot(context)
