package com.luckyalanzhou.barcodegenerator.presentation.generate

import com.luckyalanzhou.barcodegenerator.presentation.*

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase

/** 负责保存生成页草稿与格式，并调用领域用例校验输入、生成条码数据。 */
@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val editor: GenerateEditorStateHolder,
    private val generateBarcodesUseCase: GenerateBarcodesUseCase,
) : ViewModel() {
    val uiState: StateFlow<GenerateEditorState> = editor.state
    /** 输入或行顺序变化时更新可恢复的草稿。 */
    fun updateDraft(values: List<String>) = editor.updateDraft(values)

    /** 保存用户当前选择的条码格式，供页面重建后恢复。 */
    fun updateFormat(format: String) = editor.updateFormat(format)

    /** 消费编辑流程中的临时格式，避免旧值影响下一次新建。 */
    fun clearPendingFormat() = editor.clearPendingFormat()

    /** 先保存本次输入与格式，再根据现有条码集合分配编号并返回生成结果。 */
    fun generate(values: List<String>, format: String, existingItems: List<CodeItem>): GenerateBarcodesUseCase.Output {
        editor.updateDraft(values)
        editor.updateFormat(format)
        return generateBarcodesUseCase.execute(values, format, existingItems)
    }
}
