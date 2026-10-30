package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.io.File

/** 原子预留日志导出文件名，避免同秒重复导出时相互覆盖。 */
internal fun createUniqueLogExportFile(directory: File, requestedName: String): File {
    require(requestedName.isNotBlank() && '/' !in requestedName && '\\' !in requestedName) {
        "日志导出文件名无效"
    }
    check(directory.isDirectory || directory.mkdirs()) { "无法创建日志导出目录" }

    val extensionIndex = requestedName.lastIndexOf('.').takeIf { it > 0 }
    val stem = extensionIndex?.let { requestedName.substring(0, it) } ?: requestedName
    val extension = extensionIndex?.let { requestedName.substring(it) }.orEmpty()
    var suffix = 0L
    while (true) {
        val candidateName = if (suffix == 0L) requestedName else "$stem-$suffix$extension"
        val candidate = File(directory, candidateName)
        if (candidate.createNewFile()) return candidate
        suffix = Math.incrementExact(suffix)
    }
}
