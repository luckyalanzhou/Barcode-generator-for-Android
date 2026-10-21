package com.luckyalanzhou.barcodegenerator.presentation.generate

/** 批量粘贴解析结果；失败时提供原始文本行号，调用方不得应用部分结果。 */
internal sealed interface BatchBarcodeTextResult {
    data class Success(val values: List<String>) : BatchBarcodeTextResult
    data class Failure(val line: Int, val reason: String) : BatchBarcodeTextResult
}

/** 草稿追加结果；容量检查通过前不修改调用方持有的当前输入。 */
internal sealed interface BatchBarcodeAppendResult {
    data class Success(val values: List<String>) : BatchBarcodeAppendResult
    data object CapacityExceeded : BatchBarcodeAppendResult
}

/**
 * 把逐行文本或单列 CSV 转成条码输入。
 * 解析只负责文本结构，不修剪字段、不去重、不做条码格式校验；后者沿用生成用例。
 */
internal object BatchBarcodeTextParser {
    const val MAX_INPUT_ROWS = 100

    fun parse(text: String, maxRows: Int = MAX_INPUT_ROWS): BatchBarcodeTextResult {
        if (maxRows <= 0) return BatchBarcodeTextResult.Failure(1, "没有可用的输入行")
        val values = ArrayList<String>()
        val lines = text.removePrefix("\uFEFF").split(Regex("\\r\\n|\\n|\\r"))
        lines.forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            val value = parseSingleCsvField(line)
                ?: return BatchBarcodeTextResult.Failure(
                    line = index + 1,
                    reason = "每行只能有一个条码值；含逗号或双引号的值请用双引号括起",
                )
            if (value.isBlank()) return@forEachIndexed
            if (values.size == maxRows) {
                return BatchBarcodeTextResult.Failure(index + 1, "最多导入 $maxRows 行条码")
            }
            values += value
        }
        return if (values.isEmpty()) {
            BatchBarcodeTextResult.Failure(1, "请粘贴至少一行非空条码内容")
        } else {
            BatchBarcodeTextResult.Success(values)
        }
    }

    fun append(current: List<String>, imported: List<String>, maxRows: Int = MAX_INPUT_ROWS): BatchBarcodeAppendResult {
        val existing = if (current.size == 1 && current.single().isEmpty()) emptyList() else current
        if (maxRows <= 0 || existing.size > maxRows || imported.size > maxRows - existing.size) {
            return BatchBarcodeAppendResult.CapacityExceeded
        }
        return BatchBarcodeAppendResult.Success(existing + imported)
    }

    /** 接受普通单字段文本，或带标准 CSV 双引号转义的单字段记录；不接受静默丢列。 */
    private fun parseSingleCsvField(line: String): String? {
        if (!line.startsWith('"')) {
            if (',' in line || '"' in line) return null
            return line
        }
        val value = StringBuilder()
        var index = 1
        while (index < line.length) {
            val character = line[index]
            if (character != '"') {
                value.append(character)
                index++
                continue
            }
            if (line.getOrNull(index + 1) == '"') {
                value.append('"')
                index += 2
                continue
            }
            // 关闭引号后必须到行尾；逗号意味着额外列，其他字符属于格式错误。
            return if (index == line.lastIndex) value.toString() else null
        }
        return null
    }
}
