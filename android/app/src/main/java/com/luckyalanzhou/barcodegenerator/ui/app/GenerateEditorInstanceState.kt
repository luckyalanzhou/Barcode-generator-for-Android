package com.luckyalanzhou.barcodegenerator.ui.app

import android.content.Context
import android.os.Bundle
import com.luckyalanzhou.barcodegenerator.presentation.GenerateEditorState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Activity 实例状态适配器：小草稿放 Bundle，大草稿写入应用私有临时文件。 */
internal object GenerateEditorInstanceState {
    internal const val INPUTS_KEY = "generate.inputDraft"
    internal const val FORMAT_KEY = "generate.formatName"
    internal const val FILE_SAVED_KEY = "generate.inputDraft.fileSaved"
    internal const val FILE_ID_KEY = "generate.inputDraft.fileId"
    internal const val MAX_SAVED_DRAFT_BYTES = 24 * 1024

    /** 异步恢复的大草稿数据；旧版共享文件恢复时 ID 保持为空，直到 Activity 保存新实例状态。 */
    internal data class RestoredLargeDraft(
        val inputs: List<String>,
        val formatName: String,
        val draftId: String?,
    )

    /** 从私有文件读取大草稿只在后台 dispatcher 执行；不修改传入的 Bundle 或旧版共享文件身份。 */
    suspend fun restoreLargeDraft(
        context: Context,
        savedState: Bundle?,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): RestoredLargeDraft? {
        if (savedState?.getBoolean(FILE_SAVED_KEY) != true) return null
        val savedFileId = savedState.getString(FILE_ID_KEY)
        if (savedFileId != null && !GenerateEditorDraftStore.isValidDraftId(savedFileId)) return null

        return withContext(ioDispatcher) {
            val store = savedFileId?.let {
                GenerateEditorDraftStore(context.noBackupFilesDir, it)
            } ?: GenerateEditorDraftStore(context.noBackupFilesDir)
            store.restore()?.let { draft ->
                RestoredLargeDraft(draft.inputs, draft.formatName, savedFileId)
            }
        }
    }

    /** Activity 在异步恢复期间再次保存时，原草稿引用必须继续指向仍可恢复的源文件。 */
    fun copyPendingDraftState(source: Bundle, destination: Bundle) {
        destination.remove(INPUTS_KEY)
        destination.remove(FORMAT_KEY)
        destination.remove(FILE_SAVED_KEY)
        destination.remove(FILE_ID_KEY)
        source.getStringArrayList(INPUTS_KEY)?.let { destination.putStringArrayList(INPUTS_KEY, ArrayList(it)) }
        source.getString(FORMAT_KEY)?.let { destination.putString(FORMAT_KEY, it) }
        if (source.containsKey(FILE_SAVED_KEY)) {
            destination.putBoolean(FILE_SAVED_KEY, source.getBoolean(FILE_SAVED_KEY))
        }
        source.getString(FILE_ID_KEY)?.let { destination.putString(FILE_ID_KEY, it) }
    }

    fun save(
        context: Context,
        state: GenerateEditorState,
        outState: Bundle,
        draftId: String? = null,
        prePersistedDraft: GenerateEditorDraftSnapshot? = null,
        autosavingDraftId: String? = null,
    ): Boolean {
        val existingDraftId = draftId?.takeIf(GenerateEditorDraftStore::isValidDraftId)
        outState.remove(INPUTS_KEY)
        outState.remove(FORMAT_KEY)
        outState.remove(FILE_SAVED_KEY)
        outState.remove(FILE_ID_KEY)

        if (state.inputDraft.size > GenerateEditorDraftStore.MAX_SAVED_ROWS) return false

        val preSaved = prePersistedDraft?.takeIf { it.matches(state) }
        if (preSaved != null) {
            outState.putBoolean(FILE_SAVED_KEY, true)
            outState.putString(FILE_ID_KEY, preSaved.draftId)
            return true
        }

        val encodedBytes = state.inputDraft.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 8L } +
            state.formatName.toByteArray(Charsets.UTF_8).size
        if (encodedBytes > GenerateEditorDraftStore.MAX_DRAFT_FILE_BYTES) return false
        if (encodedBytes <= MAX_SAVED_DRAFT_BYTES) {
            outState.putStringArrayList(INPUTS_KEY, ArrayList(state.inputDraft))
            outState.putString(FORMAT_KEY, state.formatName)
            existingDraftId?.let { outState.putString(FILE_ID_KEY, it) }
            return true
        }

        val fileId = existingDraftId?.takeUnless { it == autosavingDraftId }
            ?: GenerateEditorDraftStore.newDraftId()
        if (!GenerateEditorDraftStore(context.noBackupFilesDir, fileId).save(state)) return false
        outState.putBoolean(FILE_SAVED_KEY, true)
        outState.putString(FILE_ID_KEY, fileId)
        return true
    }

    fun restore(
        context: Context,
        savedState: Bundle?,
        restore: (List<String>, String) -> Unit,
    ): Boolean = restore(context, savedState, restore, {})

    fun restore(
        context: Context,
        savedState: Bundle?,
        restore: (List<String>, String) -> Unit,
        onDraftFileIdRestored: (String?) -> Unit = {},
    ): Boolean {
        val legacyStore = GenerateEditorDraftStore(context.noBackupFilesDir)
        if (savedState == null) return false

        if (savedState.getBoolean(FILE_SAVED_KEY)) {
            val savedFileId = savedState.getString(FILE_ID_KEY)
            if (savedFileId != null && !GenerateEditorDraftStore.isValidDraftId(savedFileId)) return false
            val store = savedFileId?.let { GenerateEditorDraftStore(context.noBackupFilesDir, it) } ?: legacyStore
            val draft = store.restore() ?: return false
            restore(draft.inputs, draft.formatName)
            if (savedFileId != null) {
                onDraftFileIdRestored(savedFileId)
            } else {
                // 旧 Activity 状态只带布尔标记；恢复后迁移共享旧文件，避免后续实例互相覆盖。
                val migratedId = GenerateEditorDraftStore.newDraftId()
                val migrated = GenerateEditorDraftStore(context.noBackupFilesDir, migratedId)
                if (migrated.save(GenerateEditorState(draft.inputs, draft.formatName))) {
                    savedState.putString(FILE_ID_KEY, migratedId)
                    legacyStore.delete()
                    onDraftFileIdRestored(migratedId)
                } else {
                    onDraftFileIdRestored(null)
                }
            }
            return true
        }

        val inputs = savedState.getStringArrayList(INPUTS_KEY) ?: return false
        val format = savedState.getString(FORMAT_KEY) ?: return false
        val encodedBytes = inputs.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 8L } +
            format.toByteArray(Charsets.UTF_8).size
        if (inputs.size > GenerateEditorDraftStore.MAX_SAVED_ROWS || encodedBytes > MAX_SAVED_DRAFT_BYTES) return false
        savedState.getString(FILE_ID_KEY)?.takeIf(GenerateEditorDraftStore::isValidDraftId)?.let {
            GenerateEditorDraftStore(context.noBackupFilesDir, it).delete()
        }
        restore(inputs.toList(), format)
        onDraftFileIdRestored(null)
        return true
    }
}
