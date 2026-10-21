package com.luckyalanzhou.barcodegenerator.ui.app

import android.os.Bundle
import com.luckyalanzhou.barcodegenerator.presentation.GenerateEditorState

/** Activity 的实例状态适配器：只保存小型生成草稿，避免进程重建时丢失输入。 */
internal object GenerateEditorInstanceState {
    private const val INPUTS_KEY = "generate.inputDraft"
    private const val FORMAT_KEY = "generate.formatName"
    internal const val MAX_SAVED_DRAFT_BYTES = 24 * 1024
    private const val MAX_SAVED_ROWS = 1_000

    fun save(state: GenerateEditorState, outState: Bundle): Boolean {
        val encodedBytes = state.inputDraft.sumOf { it.toByteArray(Charsets.UTF_8).size } +
            state.inputDraft.size * 8
        if (state.inputDraft.size > MAX_SAVED_ROWS || encodedBytes > MAX_SAVED_DRAFT_BYTES) {
            return false
        }
        outState.putStringArrayList(INPUTS_KEY, ArrayList(state.inputDraft))
        outState.putString(FORMAT_KEY, state.formatName)
        return true
    }

    fun restore(savedState: Bundle?, restore: (List<String>, String) -> Unit): Boolean {
        val inputs = savedState?.getStringArrayList(INPUTS_KEY) ?: return false
        val format = savedState.getString(FORMAT_KEY) ?: return false
        val encodedBytes = inputs.sumOf { it.toByteArray(Charsets.UTF_8).size } + inputs.size * 8
        if (inputs.size > MAX_SAVED_ROWS || encodedBytes > MAX_SAVED_DRAFT_BYTES) return false
        restore(inputs.toList(), format)
        return true
    }
}
