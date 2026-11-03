package com.luckyalanzhou.barcodegenerator.domain

import java.util.UUID

/** 条码生成领域用例；不依赖 Activity 或 Compose，便于单元测试和后续 ViewModel 调用。 */
class GenerateBarcodesUseCase {
    data class Output(
        val items: List<CodeItem> = emptyList(),
        val errorIndex: Int = -1,
        val errorMessage: String = "",
    ) {
        val isValid: Boolean get() = errorIndex < 0
    }

    /** 限制输入行数并验证所有非空内容；错误返回原始行号，否则生成临时 ID 的条码对象列表。 */
    fun execute(
        input: List<String>,
        format: String,
        existingItems: List<CodeItem>,
        now: Long = System.currentTimeMillis(),
    ): Output {
        if (input.size > MAX_GENERATE_INPUT_ROWS) {
            return Output(
                errorIndex = MAX_GENERATE_INPUT_ROWS,
                errorMessage = "最多支持 $MAX_GENERATE_INPUT_ROWS 行输入",
            )
        }
        // 只过滤完全空白的输入，条码正文（包括首尾空格）必须原样保留。
        val values = input.mapIndexedNotNull { index, value ->
            value.takeIf(String::isNotBlank)?.let { index to it }
        }
        if (values.isEmpty()) return Output(errorIndex = 0, errorMessage = "请输入内容")
        values.forEach { (originalIndex, value) ->
            val validation = BarcodeValidator.validate(value, format)
            if (!validation.valid) return Output(errorIndex = originalIndex, errorMessage = validation.message)
        }
        // 草稿 ID 仅用于界面渲染；插入时由仓库分配正式 ID。
        var nextId = (existingItems.maxOfOrNull { it.id } ?: 0L) + 1L
        // 时间戳用于显示，批次 ID 用于区分同一毫秒内独立提交的生成操作。
        val historyBatchId = UUID.randomUUID().toString()
        return Output(values.map { (_, value) -> CodeItem(nextId++, value, format, now, historyBatchId = historyBatchId) })
    }
}
