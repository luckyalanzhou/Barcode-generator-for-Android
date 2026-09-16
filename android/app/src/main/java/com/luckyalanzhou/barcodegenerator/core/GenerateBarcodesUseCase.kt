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
        val values = input.map(String::trim).filter(String::isNotEmpty)
        if (values.isEmpty()) return Output(errorIndex = 0, errorMessage = "请输入内容")
        values.forEachIndexed { index, value ->
            val validation = BarcodeValidator.validate(value, format)
            if (!validation.valid) return Output(errorIndex = index, errorMessage = validation.message)
        }
        var nextId = (existingItems.maxOfOrNull { it.id } ?: 0L) + 1L
        return Output(values.map { value -> CodeItem(nextId++, value, format, now) })
    }
}
