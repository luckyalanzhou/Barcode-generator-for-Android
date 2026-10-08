package com.luckyalanzhou.barcodegenerator

import android.content.Context
import java.io.File

/** 正式版不持久化应用调试日志。 */
internal fun debugLogInitializeImpl(context: Context) = Unit
internal fun debugLogRecordImpl(tag: String, message: String, error: Throwable?) = Unit
internal fun debugLogSnapshotImpl(context: Context): File = File(context.cacheDir, "debug.log")
