package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.luckyalanzhou.barcodegenerator.ui.support.logging.runWithDiagnosticFallback
import com.luckyalanzhou.barcodegenerator.ui.support.logging.selectRecentLogRecords
import com.luckyalanzhou.barcodegenerator.ui.support.logging.buildLogExport
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DiagnosticWriteQueue
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DailyLogCleanupSchedule
import com.luckyalanzhou.barcodegenerator.ui.support.logging.formatDiagnosticThrowable
import com.luckyalanzhou.barcodegenerator.ui.support.logging.sanitizeDiagnosticLogText
import com.luckyalanzhou.barcodegenerator.ui.support.logging.createUniqueLogExportFile
import com.luckyalanzhou.barcodegenerator.ui.app.platform.ensureShareableCacheDirectory
import com.luckyalanzhou.barcodegenerator.ui.app.platform.SHAREABLE_DEBUG_LOGS_DIRECTORY

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
    private val cleanupSchedule = DailyLogCleanupSchedule()
    @Volatile private var directory: File? = null
    private val writer = DiagnosticWriteQueue(
        onFailure = { DebugLog.recordBackendFailure("diagnostics", "async write failed", null, it) },
        onDropped = { writeNow("diagnostics", "queue_overflow omittedRecords=$it", null) },
    )

    fun initialize(context: Context) {
        val target = File(context.applicationContext.filesDir, DIRECTORY_NAME)
        val applicationContext = context.applicationContext
        synchronized(lock) { directory = target }
        // MainActivity 在首屏创建前调用日志初始化；文件迁移和清理由同一串行队列执行，
        // 后续日志自然排在准备任务之后，不阻塞首帧，也不改变记录顺序。
        writer.submit {
            synchronized(lock) {
                target.mkdirs()
                migrateLegacyFile(applicationContext, target)
                val cleanupDay = today()
                cleanup(target, cleanupDay)
                cleanupSchedule.markCleaned(cleanupDay)
            }
        }
    }

    fun record(tag: String, message: String, error: Throwable? = null) {
        val recordedAt = System.currentTimeMillis()
        if (tag == "crash") {
            // 崩溃记录必须同步兜底，不能依赖进程退出前异步队列恰好完成。
            if (!writer.flush()) writeNow("diagnostics", "crash_flush_timeout pending records may be omitted", null)
            writeNow(tag, message, error, recordedAt)
        } else writer.submit {
            runWithDiagnosticFallback(
                operation = { writeNow(tag, message, error, recordedAt) },
                fallback = { backendError ->
                    DebugLog.recordBackendFailure(tag, message, error, backendError)
                },
            )
        }
    }

    private fun writeNow(tag: String, message: String, error: Throwable?, recordedAt: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val targetDirectory = directory ?: error("Beta log backend not initialized")
            val currentDay = today()
            if (cleanupSchedule.needsCleanup(currentDay)) {
                cleanup(targetDirectory, currentDay)
                cleanupSchedule.markCleaned(currentDay)
            }
            val target = dailyFile(targetDirectory, currentDay)
            val line = buildString {
                // SimpleDateFormat 非线程安全，格式化必须与写入共用锁。
                append(formatter.format(Date(recordedAt))).append(" [").append(tag).append("] ")
                append(message.replace('\n', ' '))
                error?.let { append(" | ").append(formatDiagnosticThrowable(it)) }
                append("\n")
            }
            target.parentFile?.mkdirs()
            if (target.length() + line.toByteArray(Charsets.UTF_8).size > MAX_BYTES) {
                val previous = if (target.exists()) sanitizeDiagnosticLogText(target.readText(Charsets.UTF_8)) else ""
                val recent = selectRecentLogRecords(previous + line, (MAX_BYTES - 256).toInt())
                replaceLog(target, "${formatter.format(Date())} [diagnostics] rotation omittedRecords=${recent.omittedRecords}\n" + recent.text)
            } else {
                target.appendText(line, Charsets.UTF_8)
            }
            // 写入失败向上传递，由 DebugLog 写兜底文件，不能静默丢日志。
        }
    }

    fun snapshot(context: Context): File {
        val fullyFlushed = writer.flush()
        val targetDirectory = directory ?: File(context.applicationContext.filesDir, DIRECTORY_NAME).also {
            it.mkdirs()
            directory = it
        }
        synchronized(lock) {
            val cleanupDay = today()
            cleanup(targetDirectory, cleanupDay)
            cleanupSchedule.markCleaned(cleanupDay)
            // 最新日志优先，避免导出大小达到上限时丢掉最近一次崩溃上下文。
            val fallback = File(context.applicationContext.filesDir, "debug.log")
            val sources = (listOf(fallback).filter { it.isFile && it.length() > 0 } + dailyFiles(targetDirectory))
                .sortedByDescending { it.lastModified() }
            val export = createUniqueLogExportFile(
                ensureShareableCacheDirectory(context.cacheDir, SHAREABLE_DEBUG_LOGS_DIRECTORY),
                "barcode-generator-debug-${exportTimestamp()}.log",
            )
            val readable = sources.map { file ->
                file.name to try {
                    sanitizeDiagnosticLogText(file.readText(Charsets.UTF_8))
                } catch (error: Exception) {
                    // 单个日志损坏不能导致其余日期全部丢失，导出中保留失败说明。
                    "${formatter.format(Date())} [diagnostics] source_read_failed file=${file.name} | ${formatDiagnosticThrowable(error)}\n"
                }
            }
            try {
                export.writeText(buildLogExport(readable, MAX_EXPORT_BYTES.toInt()), Charsets.UTF_8)
                if (!fullyFlushed) export.appendText("\n[diagnostics] export_flush_timeout pending records may be omitted\n", Charsets.UTF_8)
                return export
            } catch (error: Throwable) {
                export.delete()
                throw error
            }
        }
    }

    private fun dailyFile(targetDirectory: File, date: LocalDate = today()): File =
        File(targetDirectory, "$FILE_PREFIX$date$FILE_SUFFIX")

    private fun dailyFiles(targetDirectory: File): List<File> = targetDirectory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.startsWith(FILE_PREFIX) && it.name.endsWith(FILE_SUFFIX) }
        .sortedBy { it.name }

    private fun cleanup(targetDirectory: File, today: LocalDate) {
        // “超过 7 天”按自然日计算：今天往前第 7 天仍保留，更早的才清理。
        val cutoff = today.minusDays(RETENTION_DAYS)
        dailyFiles(targetDirectory).forEach { file ->
            val date = file.name.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)
            val parsedDate = try {
                LocalDate.parse(date)
            } catch (_: DateTimeParseException) {
                null
            }
            parsedDate?.takeIf { it.isBefore(cutoff) }?.let {
                if (!file.delete()) Log.w("BarcodeGenerator.DebugLog", "Could not delete old log: ${file.name}")
            }
        }
    }

    private fun migrateLegacyFile(context: Context, targetDirectory: File) {
        val legacy = File(context.filesDir, "debug.log")
        if (!legacy.isFile) return
        val current = dailyFile(targetDirectory)
        runWithDiagnosticFallback(
            operation = {
                // 当当天日志已存在时也合并旧文件，不能直接删除未迁移的异常。
                val migrated = sanitizeDiagnosticLogText(legacy.readText(Charsets.UTF_8))
                val existing = if (current.exists()) sanitizeDiagnosticLogText(current.readText(Charsets.UTF_8)) else ""
                val merged = migrated + existing
                val recent = selectRecentLogRecords(merged, (MAX_BYTES - 256).toInt())
                replaceLog(current, "${formatter.format(Date())} [diagnostics] legacy_migration omittedRecords=${recent.omittedRecords}\n" + recent.text)
                check(legacy.delete()) { "Could not delete migrated legacy log" }
            },
            fallback = {
                Log.w("BarcodeGenerator.DebugLog", "Could not migrate legacy debug log | ${formatDiagnosticThrowable(it)}")
            },
        )
    }

    private fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

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
