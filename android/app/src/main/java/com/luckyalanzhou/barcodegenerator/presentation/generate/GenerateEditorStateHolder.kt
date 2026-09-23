package com.luckyalanzhou.barcodegenerator

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Shared editor snapshot lets generation and the UI-facing GenerateViewModel stay in sync. */
@Singleton
class GenerateEditorStateHolder @Inject constructor() {
    private val mutableState = MutableStateFlow(GenerateEditorState())
    val state: StateFlow<GenerateEditorState> = mutableState.asStateFlow()

    fun updateDraft(values: List<String>) = mutableState.update { it.copy(inputDraft = values.toList()) }
    fun updateFormat(format: String) = mutableState.update { it.copy(formatName = format) }
    fun setPendingFormat(format: String?) = mutableState.update { it.copy(pendingFormat = format) }
    fun clearPendingFormat() = setPendingFormat(null)
}
