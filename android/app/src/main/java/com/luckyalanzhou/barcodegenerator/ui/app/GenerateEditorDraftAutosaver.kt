package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.presentation.GenerateEditorState
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 大草稿在 Activity 活跃期间异步预存，生命周期保存可直接引用最近完成的快照。 */
internal class GenerateEditorDraftAutosaver(
    private val noBackupFilesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val draftId: String = GenerateEditorDraftStore.newDraftId(),
) {
    private val stateLock = Any()
    private var latestRevision = 0L
    private var persisted: GenerateEditorDraftSnapshot? = null

    fun observe(scope: CoroutineScope, states: Flow<GenerateEditorState>): Job = scope.launch {
        val pending = Channel<VersionedPayload>(Channel.CONFLATED)
        val collector = launch {
            states
                .map { DraftPayload(it.inputDraft, it.formatName) }
                .distinctUntilChanged { previous, current ->
                    previous.inputs === current.inputs && previous.formatName == current.formatName
                }
                .collect { payload ->
                    val versioned = synchronized(stateLock) {
                        if (persisted?.let { it.inputs === payload.inputs && it.formatName == payload.formatName } == true) {
                            null
                        } else {
                            latestRevision += 1L
                            persisted = null
                            VersionedPayload(payload, latestRevision)
                        }
                    }
                    if (versioned != null) pending.send(versioned)
                }
        }
        try {
            for (first in pending) {
                var payload = first
                while (true) {
                    val next = withTimeoutOrNull(debounceMillis) { pending.receive() } ?: break
                    payload = next
                }
                val draft = payload.payload
                val snapshot = withContext(ioDispatcher) {
                    val encodedBytes = draft.inputs.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 8L } +
                        draft.formatName.toByteArray(Charsets.UTF_8).size
                    if (draft.inputs.size > GenerateEditorDraftStore.MAX_SAVED_ROWS ||
                        encodedBytes <= GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES ||
                        encodedBytes > GenerateEditorDraftStore.MAX_DRAFT_FILE_BYTES
                    ) {
                        null
                    } else {
                        val state = GenerateEditorState(draft.inputs, formatName = draft.formatName)
                        if (GenerateEditorDraftStore(noBackupFilesDir, draftId).save(state)) {
                            GenerateEditorDraftSnapshot(draftId, draft.inputs, draft.formatName, encodedBytes)
                        } else {
                            null
                        }
                    }
                }
                if (snapshot != null) {
                    synchronized(stateLock) {
                        if (latestRevision == payload.revision) {
                            persisted = snapshot
                        }
                    }
                }
            }
        } finally {
            collector.cancel()
            pending.close()
        }
    }

    fun snapshotFor(state: GenerateEditorState): GenerateEditorDraftSnapshot? = synchronized(stateLock) {
        persisted?.takeIf { it.matches(state) }
    }

    fun ownsDraftId(value: String?): Boolean = value != null && value == draftId

    private data class DraftPayload(val inputs: List<String>, val formatName: String)
    private data class VersionedPayload(val payload: DraftPayload, val revision: Long)

    private companion object {
        const val DEBOUNCE_MILLIS = 350L
    }
}

/** 只由异步存储成功后创建；保存 Activity 状态时可安全引用该文件。 */
internal data class GenerateEditorDraftSnapshot(
    val draftId: String,
    val inputs: List<String>,
    val formatName: String,
    val encodedBytes: Long,
) {
    fun matches(state: GenerateEditorState): Boolean =
        GenerateEditorDraftStore.isValidDraftId(draftId) &&
            encodedBytes > GenerateEditorInstanceState.MAX_SAVED_DRAFT_BYTES &&
            encodedBytes <= GenerateEditorDraftStore.MAX_DRAFT_FILE_BYTES &&
            inputs.size <= GenerateEditorDraftStore.MAX_SAVED_ROWS &&
            state.inputDraft === inputs && state.formatName == formatName
}
