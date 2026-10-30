package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.util.IdentityHashMap

private const val MAX_DIAGNOSTIC_THROWABLE_CHARS = 32 * 1024
private const val MAX_DIAGNOSTIC_THROWABLES = 12
private const val MAX_DIAGNOSTIC_STACK_FRAMES = 48
private const val MAX_DIAGNOSTIC_SUPPRESSED = 4
private val diagnosticUriPattern = Regex("""(?i)\b(?:content|file|https?)://[^\s"'<>]+""")
private val diagnosticWindowsPathPattern = Regex("""(?i)\b[A-Z]:\\[^\s"'<>]+""")
// 旧日志中的 Unix 路径可能包含空格，无法可靠判断路径终点，因此从已知敏感根目录起脱敏本行余下内容。
private val diagnosticUnixPathPattern = Regex("""(?i)(?<![A-Za-z0-9])/(?:storage|sdcard|data/(?:user(?:_de)?/\d+|data))/.*$""")
private val diagnosticUploadedFileNamePattern = Regex("""(?i)\bfile uploaded name=.*$""")
private val throwableHeaderPattern = Regex("""^([A-Za-z_$][A-Za-z0-9_$.]*(?:Exception|Error|Throwable))(?::.*)?$""")
private val causedByHeaderPattern = Regex("""^(\s*(?:Caused by: |Suppressed: ))([A-Za-z_$][A-Za-z0-9_$.]*(?:Exception|Error|Throwable))(?::.*)?$""")

/** 保留异常类型和调用栈，但省略异常消息，避免系统把 URI、路径或用户内容写入日志。 */
internal fun formatDiagnosticThrowable(error: Throwable): String {
    val output = StringBuilder()
    val visited = IdentityHashMap<Throwable, Boolean>()
    var throwableCount = 0

    fun appendLimited(value: String) {
        val remaining = MAX_DIAGNOSTIC_THROWABLE_CHARS - output.length
        if (remaining > 0) output.append(value, 0, minOf(value.length, remaining))
    }

    fun append(current: Throwable, relation: String, depth: Int) {
        if (output.length >= MAX_DIAGNOSTIC_THROWABLE_CHARS) return
        if (depth >= MAX_DIAGNOSTIC_THROWABLES || throwableCount >= MAX_DIAGNOSTIC_THROWABLES) {
            appendLimited("... additional exception details omitted\n")
            return
        }
        if (visited.put(current, true) != null) {
            appendLimited("... cyclic exception reference omitted\n")
            return
        }
        throwableCount++

        appendLimited(relation)
        appendLimited(current.javaClass.name)
        appendLimited(" [message redacted]\n")

        val frames = current.stackTrace
        frames.take(MAX_DIAGNOSTIC_STACK_FRAMES).forEach { frame ->
            appendLimited("\tat $frame\n")
        }
        if (frames.size > MAX_DIAGNOSTIC_STACK_FRAMES) {
            appendLimited("\t... ${frames.size - MAX_DIAGNOSTIC_STACK_FRAMES} stack frames omitted\n")
        }

        current.suppressed.take(MAX_DIAGNOSTIC_SUPPRESSED).forEach { suppressed ->
            append(suppressed, "\tSuppressed: ", depth + 1)
        }
        if (current.suppressed.size > MAX_DIAGNOSTIC_SUPPRESSED) {
            appendLimited("\t... additional suppressed exceptions omitted\n")
        }
        current.cause?.let { cause -> append(cause, "Caused by: ", depth + 1) }
    }

    append(error, "", 0)
    if (output.length == MAX_DIAGNOSTIC_THROWABLE_CHARS) {
        val marker = "\n... diagnostic stack truncated\n"
        output.replace(MAX_DIAGNOSTIC_THROWABLE_CHARS - marker.length, MAX_DIAGNOSTIC_THROWABLE_CHARS, marker)
    }
    return output.toString().trimEnd()
}

/** 从旧版应用写入的日志记录中遮蔽已知的用户输入内容。 */
internal fun sanitizeDiagnosticLogText(text: String): String = text.lineSequence().joinToString("\n") { line ->
    var sanitized = line
        .replace(diagnosticUriPattern, "[URI redacted]")
        .replace(diagnosticWindowsPathPattern, "[PATH redacted]")
        .replace(diagnosticUnixPathPattern, "[PATH redacted]")
        .replace(diagnosticUploadedFileNamePattern) { "file uploaded name=[redacted]" }

    val causeHeader = causedByHeaderPattern.matchEntire(sanitized)
    if (causeHeader != null) {
        sanitized = "${causeHeader.groupValues[1]}${causeHeader.groupValues[2]} [message redacted]"
    } else {
        val errorDivider = sanitized.lastIndexOf(" | ")
        if (errorDivider >= 0) {
            val errorHeader = sanitized.substring(errorDivider + 3)
            val match = throwableHeaderPattern.matchEntire(errorHeader)
            if (match != null) {
                sanitized = "${sanitized.substring(0, errorDivider + 3)}${match.groupValues[1]} [message redacted]"
            }
        }
        val leadingWhitespace = sanitized.takeWhile(Char::isWhitespace)
        val header = sanitized.drop(leadingWhitespace.length)
        val match = throwableHeaderPattern.matchEntire(header)
        if (match != null) sanitized = "$leadingWhitespace${match.groupValues[1]} [message redacted]"
    }
    sanitized
}
