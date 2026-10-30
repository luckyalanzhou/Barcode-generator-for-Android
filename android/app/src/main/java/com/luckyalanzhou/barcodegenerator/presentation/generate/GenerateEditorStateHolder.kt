package com.luckyalanzhou.barcodegenerator.presentation.generate

import com.luckyalanzhou.barcodegenerator.presentation.*

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 通过共享编辑器快照保持生成逻辑与面向界面的 GenerateViewModel 同步。 */
@Singleton
class GenerateEditorStateHolder @Inject constructor() {
    private val mutableState = MutableStateFlow(GenerateEditorState())
    val state: StateFlow<GenerateEditorState> = mutableState.asStateFlow()

    fun updateDraft(values: List<String>) = mutableState.update { it.copy(inputDraft = values.toList()) }
    fun updateFormat(format: String) = mutableState.update { it.copy(formatName = format) }
    fun setPendingFormat(format: String?) = mutableState.update { it.copy(pendingFormat = format) }
    /** 原子恢复编辑器中需要持久保留的部分；待处理的导航状态不会恢复。 */
    fun restoreDraft(values: List<String>, format: String) = mutableState.update {
        it.copy(inputDraft = values.toList(), pendingFormat = null, formatName = format)
    }
    /** 在结果页被替换前，原子地准备生成页编辑器状态。 */
    fun beginEditing(values: List<String>, format: String?) = mutableState.update {
        it.copy(
            inputDraft = values.toList(),
            pendingFormat = format,
            formatName = format ?: it.formatName,
        )
    }
    fun clearPendingFormat() = setPendingFormat(null)
}
