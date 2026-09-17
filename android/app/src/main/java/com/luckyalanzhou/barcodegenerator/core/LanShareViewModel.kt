package com.luckyalanzhou.barcodegenerator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.update

data class LanShareUiState(
    val session: LanShareSession? = null,
    val isHost: Boolean = false,
    val qrVisible: Boolean = false,
    val browserConnected: Boolean = false,
    val files: List<LanShareFile> = emptyList(),
    val ownFileIds: Set<String> = emptySet(),
    val previewFiles: Map<String, java.io.File> = emptyMap(),
    val pendingDownloadId: String? = null,
    val pendingUploadUri: android.net.Uri? = null,
    val pendingUploadTempFile: java.io.File? = null,
    val pendingUploadName: String? = null,
)

sealed interface LanShareEvent {
    data class Error(val message: String) : LanShareEvent
    data class Notice(val message: String, val clearInput: Boolean = false) : LanShareEvent
}

/** 局域网分享展示状态；网络服务生命周期仍由 Activity 桥接层管理。 */
@HiltViewModel
class LanShareViewModel @Inject constructor(
    private val lanShareManager: LanShareManager,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LanShareUiState())
    val uiState: StateFlow<LanShareUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<LanShareEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<LanShareEvent> = _events.asSharedFlow()
    private var refreshJob: Job? = null
    private var autoRefreshJob: Job? = null

    fun isOnLocalNetwork(): Boolean = lanShareManager.isOnLocalNetwork()

    fun isRouterLanHost(host: String?): Boolean = lanShareManager.isRouterLanHost(host)

    fun localFile(id: String): java.io.File? = lanShareManager.localFile(id)

    fun startHostSession(): LanShareSession {
        val session = lanShareManager.start()
        _uiState.update {
            it.copy(
                session = session,
                isHost = true,
                qrVisible = true,
                browserConnected = false,
                files = lanShareManager.localFiles(),
                ownFileIds = emptySet(),
            )
        }
        return session
    }

    fun joinSession(session: LanShareSession) {
        stopAutoRefresh()
        lanShareManager.stop()
        _uiState.update { it.copy(session = session, isHost = false, qrVisible = false, browserConnected = false) }
    }

    fun joinSessionFromAddress(value: String): Boolean {
        val address = value.trim()
        if (!address.startsWith("http://", ignoreCase = true)) {
            _events.tryEmit(LanShareEvent.Error("这不是局域网分享地址"))
            return false
        }
        val uri = Uri.parse(address)
        if (uri.host.isNullOrBlank() || uri.port !in 1..65535 ||
            uri.query != null || uri.fragment != null || uri.userInfo != null ||
            !lanShareManager.isRouterLanHost(uri.host)
        ) {
            _events.tryEmit(LanShareEvent.Error("这不是局域网分享地址"))
            return false
        }
        val session = LanShareSession("${uri.scheme}://${uri.host}:${if (uri.port > 0) uri.port else 80}")
        joinSession(session)
        startAutoRefresh(session)
        refreshFiles(session)
        return true
    }

    fun restartHostSession(): LanShareSession {
        stopAutoRefresh()
        val session = lanShareManager.restart()
        _uiState.update {
            it.copy(
                session = session,
                isHost = true,
                qrVisible = true,
                browserConnected = false,
                files = lanShareManager.localFiles(),
            )
        }
        startAutoRefresh(session)
        return session
    }

    fun closeSession() {
        stopAutoRefresh()
        lanShareManager.stop(clearSharedFiles = true)
        _uiState.update {
            it.copy(
                session = null,
                isHost = false,
                qrVisible = false,
                browserConnected = false,
                files = emptyList(),
                ownFileIds = emptySet(),
                previewFiles = emptyMap(),
            )
        }
    }

    fun setQrVisible(visible: Boolean) {
        _uiState.update { it.copy(qrVisible = visible) }
    }

    fun clearOwnFileIds() {
        _uiState.update { it.copy(ownFileIds = emptySet()) }
    }

    fun addOwnFileId(id: String) {
        _uiState.update { it.copy(ownFileIds = it.ownFileIds + id) }
    }

    fun clearPreviewFiles() {
        _uiState.update { it.copy(previewFiles = emptyMap()) }
    }

    fun retainPreviewFiles(ids: Set<String>) {
        _uiState.update { it.copy(previewFiles = it.previewFiles.filterKeys { key -> key in ids }) }
    }

    fun addPreviewFiles(files: Map<String, java.io.File>) {
        _uiState.update { it.copy(previewFiles = it.previewFiles + files) }
    }

    fun refreshFiles(session: LanShareSession, showError: Boolean = true) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { lanShareManager.list(session) }
            _uiState.update { it.copy(browserConnected = lanShareManager.browserConnected()) }
            result.onSuccess { files ->
                val imageIds = files.filter { isLanShareImageName(it.name) }.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        files = files,
                        previewFiles = it.previewFiles.filterKeys { key -> key in imageIds },
                    )
                }
                val previews = fetchPreviews(session, files)
                if (_uiState.value.session == session && previews.isNotEmpty()) {
                    _uiState.update { it.copy(previewFiles = it.previewFiles + previews) }
                }
            }.onFailure {
                if (showError) _events.tryEmit(LanShareEvent.Error("无法连接到分享房间"))
            }
        }
    }

    fun startAutoRefresh(session: LanShareSession) {
        stopAutoRefresh()
        autoRefreshJob = viewModelScope.launch {
            while (isActive && _uiState.value.session == session) {
                delay(1_500L)
                if (!isActive || _uiState.value.session != session) break
                refreshFiles(session, showError = false)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    fun uploadFile(session: LanShareSession, uri: android.net.Uri, temporaryFile: java.io.File? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = lanShareManager.upload(session, uri)
                addOwnFileId(id)
                refreshFiles(session, showError = false)
                _events.emit(LanShareEvent.Notice("上传成功"))
            } catch (_: Exception) {
                _events.emit(LanShareEvent.Error("上传失败"))
            } finally {
                temporaryFile?.delete()
            }
        }
    }

    fun uploadText(session: LanShareSession, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = lanShareManager.uploadText(session, text)
                addOwnFileId(id)
                refreshFiles(session, showError = false)
                _events.emit(LanShareEvent.Notice("发送成功", clearInput = true))
            } catch (_: Exception) {
                _events.emit(LanShareEvent.Error("发送失败"))
            }
        }
    }

    fun downloadFile(session: LanShareSession, id: String, destination: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                lanShareManager.download(session, id, destination)
                _events.emit(LanShareEvent.Notice("下载完成"))
            } catch (_: Exception) {
                _events.emit(LanShareEvent.Error("下载失败"))
            }
        }
    }

    private suspend fun fetchPreviews(session: LanShareSession, files: List<LanShareFile>): Map<String, java.io.File> {
        val previewFolder = java.io.File(appContext.cacheDir, "lan-share-preview").apply { mkdirs() }
        val imageIds = files.filter { isLanShareImageName(it.name) }.map { it.id }.toSet()
        previewFolder.listFiles().orEmpty().filter { it.name !in imageIds }.forEach { it.delete() }
        var cachedBytes = previewFolder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
        return buildMap {
            files.filter { isLanShareImageName(it.name) && lanShareManager.localFile(it.id) == null }.forEach { file ->
                currentCoroutineContext().ensureActive()
                val preview = java.io.File(previewFolder, file.id)
                if (!preview.isFile && file.size <= 16L * 1024L * 1024L &&
                    cachedBytes + file.size <= 64L * 1024L * 1024L
                ) {
                    runCatching { lanShareManager.downloadPreview(session, file.id, preview) }
                    cachedBytes += preview.length()
                }
                if (preview.isFile) put(file.id, preview)
            }
        }
    }

    fun setPendingDownloadId(id: String?) {
        _uiState.update { it.copy(pendingDownloadId = id) }
    }

    fun setPendingUpload(uri: android.net.Uri?, temporaryFile: java.io.File?, name: String?) {
        _uiState.update {
            it.copy(
                pendingUploadUri = uri,
                pendingUploadTempFile = temporaryFile,
                pendingUploadName = name,
            )
        }
    }

    fun takePendingUpload(): Pair<android.net.Uri, java.io.File?>? {
        val current = _uiState.value
        val uri = current.pendingUploadUri ?: return null
        setPendingUpload(null, null, null)
        return uri to current.pendingUploadTempFile
    }

}
