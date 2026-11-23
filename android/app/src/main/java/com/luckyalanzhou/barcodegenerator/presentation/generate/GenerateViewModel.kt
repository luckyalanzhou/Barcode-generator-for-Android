package com.luckyalanzhou.barcodegenerator.presentation.generate

import com.luckyalanzhou.barcodegenerator.domain.BarcodeInputError
import com.luckyalanzhou.barcodegenerator.presentation.*

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase

data class GenerateValidationError(val rowIndex: Int, val reason: BarcodeInputError, val attempt: Long = 0)

/** 负责保存生成页草稿与格式，并调用领域用例校验输入、生成条码数据。 */
@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val editor: GenerateEditorStateHolder,
    private val generateBarcodesUseCase: GenerateBarcodesUseCase,
) : ViewModel() {
    val uiState: StateFlow<GenerateEditorState> = editor.state
    private val _validationError = MutableStateFlow<GenerateValidationError?>(null)
    val validationError: StateFlow<GenerateValidationError?> = _validationError.asStateFlow()
    private var validationAttempt = 0L
    /** 输入或行顺序变化时更新可恢复的草稿。 */
    fun updateDraft(values: List<String>) {
        _validationError.value = null
        editor.updateDraft(values)
    }

    /** OCR 结果与拍摄开始时的草稿做原子比较；冲突时保留用户较新的输入。 */
    internal fun applyOcrResultIfDraftUnchanged(
        expectedDraft: List<String>?,
        recognizedLines: List<String>,
    ): OcrDraftResult {
        val applied = editor.appendOcrIfUnchanged(expectedDraft, recognizedLines)
        if (applied == OcrDraftResult.Appended) _validationError.value = null
        return applied
    }

    /** 保存用户当前选择的条码格式，供页面重建后恢复。 */
    fun updateFormat(format: String) {
        _validationError.value = null
        editor.updateFormat(format)
    }

    /** Activity 进程恢复时一次性恢复输入与格式，不恢复编辑流程中的临时格式。 */
    fun restoreSavedDraft(values: List<String>, format: String) = editor.restoreDraft(values, format)

    /** 消费编辑流程中的临时格式，避免旧值影响下一次新建。 */
    fun clearPendingFormat() = editor.clearPendingFormat()

    /** 先保存本次输入与格式，再根据现有条码集合分配编号并返回生成结果。 */
    fun generate(values: List<String>, format: String, existingItems: List<CodeItem>): GenerateBarcodesUseCase.Output {
        editor.updateDraft(values)
        editor.updateFormat(format)
        val result = generateBarcodesUseCase.execute(values, format, existingItems)
        _validationError.value = if (!result.isValid && result.errorIndex in values.indices)
            GenerateValidationError(result.errorIndex, checkNotNull(result.error), ++validationAttempt) else null
        return result
    }
}
