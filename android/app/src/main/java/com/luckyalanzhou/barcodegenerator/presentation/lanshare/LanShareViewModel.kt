package com.luckyalanzhou.barcodegenerator.presentation.lanshare


import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.isLanShareImageName
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val lanShareGateway: LanShareGateway,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LanShareUiState())
    val uiState: StateFlow<LanShareUiState> = _uiState.asStateFlow()
    private val _events = Channel<LanShareEvent>(Channel.BUFFERED)
    val events: Flow<LanShareEvent> = _events.receiveAsFlow()
    private var refreshJob: Job? = null
    private var autoRefreshJob: Job? = null

    fun isOnLocalNetwork(): Boolean = lanShareGateway.isOnLocalNetwork()

    fun isRouterLanHost(host: String?): Boolean = lanShareGateway.isRouterLanHost(host)

    fun localFile(id: String): java.io.File? = lanShareGateway.localFile(id)

    fun startHostSession(): LanShareSession {
        val session = lanShareGateway.start()
        _uiState.update {
            it.copy(
                session = session,
                isHost = true,
                qrVisible = true,
                browserConnected = false,
                files = lanShareGateway.localFiles(),
                ownFileIds = emptySet(),
            )
        }
        return session
    }

    fun joinSession(session: LanShareSession) {
        stopAutoRefresh()
        lanShareGateway.stop()
        _uiState.update { it.copy(session = session, isHost = false, qrVisible = false, browserConnected = false) }
    }

    fun joinSessionFromAddress(value: String): Boolean {
        val session = LanShareSession.fromShareUrl(value)
        if (session == null || !lanShareGateway.isRouterLanHost(session.baseUrl.toUri().host)) {
            _events.trySend(LanShareEvent.Error("分享地址无效或缺少访问码，请扫描新二维码"))
            return false
        }
        joinSession(session)
        startAutoRefresh(session)
        refreshFiles(session)
        return true
    }

    fun restartHostSession(): LanShareSession {
        stopAutoRefresh()
        val session = lanShareGateway.restart()
        _uiState.update {
            it.copy(
                session = session,
                isHost = true,
                qrVisible = true,
                browserConnected = false,
                files = lanShareGateway.localFiles(),
            )
        }
        startAutoRefresh(session)
        return session
    }

    fun closeSession() {
        stopAutoRefresh()
        lanShareGateway.stop(clearSharedFiles = true)
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
            val result = runCatching { lanShareGateway.list(session) }
            _uiState.update { it.copy(browserConnected = lanShareGateway.browserConnected()) }
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
                if (showError) _events.trySend(LanShareEvent.Error("无法连接到分享房间"))
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
                val id = lanShareGateway.upload(session, createUploadSource(uri))
                addOwnFileId(id)
                refreshFiles(session, showError = false)
                _events.send(LanShareEvent.Notice("上传成功"))
            } catch (_: Exception) {
                _events.send(LanShareEvent.Error("上传失败"))
            } finally {
                temporaryFile?.delete()
            }
        }
    }

    fun uploadText(session: LanShareSession, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = lanShareGateway.uploadText(session, text)
                addOwnFileId(id)
                refreshFiles(session, showError = false)
                _events.send(LanShareEvent.Notice("发送成功", clearInput = true))
            } catch (_: Exception) {
                _events.send(LanShareEvent.Error("发送失败"))
            }
        }
    }

    fun downloadFile(session: LanShareSession, id: String, destination: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val temporary = java.io.File.createTempFile("lan-download-", ".part", appContext.cacheDir)
                try {
                    lanShareGateway.downloadToFile(session, id, temporary)
                    appContext.contentResolver.openOutputStream(destination)?.use { output ->
                        temporary.inputStream().use { it.copyTo(output) }
                    } ?: error("无法写入文件")
                } finally {
                    temporary.delete()
                }
                _events.send(LanShareEvent.Notice("下载完成"))
            } catch (_: Exception) {
                _events.send(LanShareEvent.Error("下载失败"))
            }
        }
    }

    private suspend fun fetchPreviews(session: LanShareSession, files: List<LanShareFile>): Map<String, java.io.File> {
        val previewFolder = java.io.File(appContext.cacheDir, "lan-share-preview").apply { mkdirs() }
        val imageIds = files.filter { isLanShareImageName(it.name) }.map { it.id }.toSet()
        previewFolder.listFiles().orEmpty().filter { it.name !in imageIds }.forEach { it.delete() }
        var cachedBytes = previewFolder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
        return buildMap {
            files.filter { isLanShareImageName(it.name) && lanShareGateway.localFile(it.id) == null }.forEach { file ->
                currentCoroutineContext().ensureActive()
                val preview = java.io.File(previewFolder, file.id)
                if (!preview.isFile && file.size <= 16L * 1024L * 1024L &&
                    cachedBytes + file.size <= 64L * 1024L * 1024L
                ) {
                    runCatching { lanShareGateway.downloadPreview(session, file.id, preview) }
                    cachedBytes += preview.length()
                }
                if (preview.isFile) put(file.id, preview)
            }
        }
    }

    private fun createUploadSource(uri: Uri): LanShareUploadSource {
        val name = appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
            } else null
        } ?: "附件"
        val size = appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        return LanShareUploadSource(name = name, size = size) {
            appContext.contentResolver.openInputStream(uri)
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
