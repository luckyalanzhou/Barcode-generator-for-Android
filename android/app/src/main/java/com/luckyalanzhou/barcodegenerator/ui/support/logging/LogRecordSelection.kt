package com.luckyalanzhou.barcodegenerator.ui.support.logging

/** 按完整记录（包括多行堆栈）保留最新内容，不在 UTF-8 字节中间切割。 */
internal data class LogRecordSelection(val text: String, val omittedRecords: Int)

internal fun selectRecentLogRecords(text: String, maxBytes: Int): LogRecordSelection {
    require(maxBytes >= 0)
    val starts = Regex("(?m)^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[")
        .findAll(text).map { it.range.first }.toList()
    val records = if (starts.isEmpty()) listOf(text).filter(String::isNotEmpty) else buildList {
        if (starts.first() > 0) add(text.substring(0, starts.first()))
        starts.forEachIndexed { index, start -> add(text.substring(start, starts.getOrNull(index + 1) ?: text.length)) }
    }
    var remaining = maxBytes
    val selected = ArrayDeque<String>()
    var omitted = 0
    for (record in records.asReversed()) {
        val bytes = record.toByteArray(Charsets.UTF_8).size
        if (bytes <= remaining) {
            selected.addFirst(record)
            remaining -= bytes
        } else {
            // 超出预算的整条记录省略；裁减数量在导出摘要中明示。
            omitted++
        }
    }
    return LogRecordSelection(selected.joinToString(""), omitted)
}

/** 最新文件优先，实际 UTF-8 字节数包含分段标题与裁减说明。 */
internal fun buildLogExport(sources: List<Pair<String, String>>, maxBytes: Int): String {
    require(maxBytes >= 256)
    var remaining = maxBytes - 256
    var omitted = 0
    val body = StringBuilder()
    sources.forEach { (name, content) ->
        val header = "===== $name =====\n"
        val overhead = header.toByteArray(Charsets.UTF_8).size + 1
        val recent = selectRecentLogRecords(content, (remaining - overhead).coerceAtLeast(0))
        omitted += recent.omittedRecords
        if (recent.text.isNotEmpty()) {
            body.append(header).append(recent.text).append('\n')
            remaining -= overhead + recent.text.toByteArray(Charsets.UTF_8).size
        }
    }
    if (body.isEmpty()) body.append("暂无可导出的完整日志记录\n")
    body.append("[diagnostics] export omittedRecords=$omitted limitBytes=$maxBytes newestFilesFirst=true\n")
    return body.toString()
}
