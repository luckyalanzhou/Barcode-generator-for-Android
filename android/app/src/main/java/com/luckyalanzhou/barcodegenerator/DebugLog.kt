package com.luckyalanzhou.barcodegenerator

import android.content.Context
import java.io.File

/** 正式版占位接口：不记录、不创建、不导出任何调试日志。 */
object DebugLog {
    fun initialize(context: Context) {
        if (BuildConfig.DEBUG_LOG_EXPORT) invokeBeta("debugLogInitializeImpl", Context::class.java, context)
    }
    fun record(tag: String, message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG_LOG_EXPORT) invokeBeta("debugLogRecordImpl", String::class.java, String::class.java, Throwable::class.java, tag, message, error)
    }
    fun snapshot(context: Context): File = if (BuildConfig.DEBUG_LOG_EXPORT) {
        runCatching {
            betaClass().getMethod("debugLogSnapshotImpl", Context::class.java).invoke(null, context) as File
        }.getOrElse { File(context.cacheDir, "debug.log") }
    } else {
        File(context.cacheDir, "debug.log")
    }
}

private fun betaClass() = Class.forName("com.luckyalanzhou.barcodegenerator.BetaDebugLogBackendKt")
private fun invokeBeta(name: String, vararg typesAndValues: Any?) {
    runCatching {
        val types = typesAndValues.filterIndexed { index, _ -> index % 2 == 0 }.map { it as Class<*> }.toTypedArray()
        val values = typesAndValues.filterIndexed { index, _ -> index % 2 == 1 }.toTypedArray()
        betaClass().getMethod(name, *types).invoke(null, *values)
    }
}
