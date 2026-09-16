package com.luckyalanzhou.barcodegenerator

/** 条码生成领域用例；不依赖 Activity 或 Compose，便于单元测试和后续 ViewModel 调用。 */
internal class GenerateBarcodesUseCase {
    data class Output(
        val items: List<CodeItem> = emptyList(),
        val errorIndex: Int = -1,
        val errorMessage: String = "",
    ) {
        val isValid: Boolean get() = errorIndex < 0
    }

    fun execute(
        input: List<String>,
        format: String,
        existingItems: List<CodeItem>,
        now: Long = System.currentTimeMillis(),
    ): Output {
        // 只过滤完全空白的输入，条码正文（包括首尾空格）必须原样保留。
        val values = input.mapIndexedNotNull { index, value ->
            value.takeIf(String::isNotBlank)?.let { index to it }
        }
        if (values.isEmpty()) return Output(errorIndex = 0, errorMessage = "请输入内容")
        values.forEach { (originalIndex, value) ->
            val validation = BarcodeValidator.validate(value, format)
            if (!validation.valid) return Output(errorIndex = originalIndex, errorMessage = validation.message)
        }
        var nextId = (existingItems.maxOfOrNull { it.id } ?: 0L) + 1L
        return Output(values.map { (_, value) -> CodeItem(nextId++, value, format, now) })
    }
}
