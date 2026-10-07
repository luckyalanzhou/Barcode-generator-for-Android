package com.luckyalanzhou.barcodegenerator.presentation.lanshare


import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.domain.isLanShareTiff
import com.luckyalanzhou.barcodegenerator.domain.LanShareMessage
import com.luckyalanzhou.barcodegenerator.domain.LanShareRealtimeState
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.domain.isLanShareImage
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.LanShareUploadSource
import com.luckyalanzhou.barcodegenerator.domain.LAN_SHARE_MAX_SESSION_MESSAGES
import com.luckyalanzhou.barcodegenerator.domain.lanSharePreviewCacheKey
import com.luckyalanzhou.barcodegenerator.domain.LAN_SHARE_PREVIEW_MAX_FILE_BYTES
import com.luckyalanzhou.barcodegenerator.domain.LAN_SHARE_PREVIEW_CACHE_MAX_BYTES
import com.luckyalanzhou.barcodegenerator.data.preview.LanShareImagePreviewDecoder
import com.luckyalanzhou.barcodegenerator.data.preview.LanShareTiffPreviewDecoder

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class LanShareUploadingFile(
    val id: String,
    val name: String,
    val size: Long,
    val uploadedBytes: Long,
    val startedAt: Long,
)

data class LanShareUiState(
    val session: LanShareSession? = null,
    val isHost: Boolean = false,
    val qrVisible: Boolean = false,
    val browserConnected: Boolean = false,
    val files: List<LanShareFile> = emptyList(),
    val uploadingFiles: List<LanShareUploadingFile> = emptyList(),
    val messages: List<LanShareMessage> = emptyList(),
    val ownFileIds: Set<String> = emptySet(),
    val previewFileIds: Set<String> = emptySet(),
    val pendingDownloadId: String? = null,
    val pendingUploadUri: android.net.Uri? = null,
    val pendingUploadTempFile: java.io.File? = null,
    val pendingUploadName: String? = null,
)

internal fun LanShareUiState.withoutPendingTransfers() = copy(
    uploadingFiles = emptyList(), pendingDownloadId = null,
    pendingUploadUri = null, pendingUploadTempFile = null, pendingUploadName = null,
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
    private val refreshGuard = LanShareRefreshGuard(_uiState)
    private val _events = Channel<LanShareEvent>(Channel.BUFFERED)
    val events: Flow<LanShareEvent> = _events.receiveAsFlow()
    private var refreshJob: Job? = null
    private data class ActiveUploadTask(val job: Job, val temporaryFile: java.io.File?)
    private val activeUploadTasks = ConcurrentHashMap<String, ActiveUploadTask>()
    @Volatile
    private var previewFilesById: Map<String, java.io.File> = emptyMap()
    private val previewDecodeSlots = Semaphore(2)
    private val decodedPreviews = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            lanShareGateway.observeRealtimeState().collect(::applyRealtimeState)
        }
    }

    fun isOnLocalNetwork(): Boolean = lanShareGateway.isOnLocalNetwork()

    fun isRouterLanHost(host: String?): Boolean = lanShareGateway.isRouterLanHost(host)

    fun startHostSession(): LanShareSession {
        invalidateRoomRefresh()
        previewFilesById = emptyMap()
        val session = lanShareGateway.start()
        _uiState.update {
            it.copy(
                session = session,
                isHost = true,
                qrVisible = true,
                browserConnected = lanShareGateway.browserConnected(),
                files = lanShareGateway.localFiles(),
                messages = lanShareGateway.localMessages(),
                ownFileIds = emptySet(),
                previewFileIds = emptySet(),
            )
        }
        return session
    }

    fun joinSession(session: LanShareSession) {
        invalidateRoomRefresh()
        lanShareGateway.stop()
        previewFilesById = emptyMap()
        _uiState.update {
            it.copy(
                session = session,
                isHost = false,
                qrVisible = false,
                browserConnected = false,
                files = emptyList(),
                messages = emptyList(),
                ownFileIds = emptySet(),
                previewFileIds = emptySet(),
            )
        }
    }

    fun joinSessionFromAddress(value: String): Boolean {
        val session = LanShareSession.fromShareUrl(value)
        if (session == null || !lanShareGateway.isRouterLanHost(session.baseUrl.toUri().host)) {
            _events.trySend(LanShareEvent.Error("分享地址无效，请检查地址或扫描二维码"))
            return false
        }
        joinSession(session)
        refreshFiles(session)
        return true
    }

    fun closeSession() {
        invalidateRoomRefresh()
        previewFilesById = emptyMap()
        lanShareGateway.stop(clearSharedFiles = true)
        _uiState.update {
            it.copy(
                session = null,
                isHost = false,
                qrVisible = false,
                browserConnected = false,
                files = emptyList(),
                messages = emptyList(),
                ownFileIds = emptySet(),
                previewFileIds = emptySet(),
            )
        }
    }

    fun setQrVisible(visible: Boolean) {
        _uiState.update { it.copy(qrVisible = visible) }
    }

    fun refreshFiles(session: LanShareSession, showError: Boolean = true) {
        if (!refreshGuard.isCurrent(session, refreshGuard.currentGeneration())) return
        if (refreshJob?.isActive == true) return
        val ticket = refreshGuard.currentGeneration()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { lanShareGateway.list(session) to lanShareGateway.localMessages() }
            currentCoroutineContext().ensureActive()
            if (!refreshGuard.isCurrent(session, ticket)) return@launch
            result.onSuccess { (files, messages) ->
                if (!refreshGuard.isCurrent(session, ticket)) return@onSuccess
                val imageIds = files.filter { isLanShareImage(it.name, it.mimeType) }.map { it.id }.toSet()
                previewFilesById = previewFilesById.filterKeys { it in imageIds }
                refreshGuard.update(session, ticket) {
                    it.copy(
                        files = files,
                        messages = messages,
                        previewFileIds = previewFilesById.keys,
                    )
                }
                val previews = fetchPreviews(session, files)
                currentCoroutineContext().ensureActive()
                if (previews.isNotEmpty()) {
                    if (!refreshGuard.isCurrent(session, ticket)) return@onSuccess
                    previewFilesById = previewFilesById + previews
                    refreshGuard.update(session, ticket) {
                        it.copy(previewFileIds = previewFilesById.keys)
                    }
                }
            }.onFailure {
                if (showError && refreshGuard.isCurrent(session, ticket)) {
                    _events.trySend(LanShareEvent.Error("无法连接到分享房间"))
                }
            }
        }
    }

    private fun invalidateRoomRefresh() {
        refreshGuard.invalidate()
        refreshJob?.cancel()
        refreshJob = null
        decodedPreviews.evictAll()
        activeUploadTasks.entries.toList().forEach { (id, task) ->
            task.job.cancel()
            lanShareGateway.cancelUpload(id)
        }
        _uiState.value.pendingUploadTempFile?.delete()
        _uiState.update { it.withoutPendingTransfers() }
    }

    private fun applyRealtimeState(snapshot: LanShareRealtimeState) {
        _uiState.update { state ->
            if (!state.isHost) state else state.copy(
                browserConnected = snapshot.browserConnected,
                files = snapshot.files,
                messages = snapshot.messages,
            )
        }
    }

    /** Resolves and decodes the image source off the UI thread; Compose only receives a Bitmap. */
    suspend fun loadImagePreview(file: LanShareFile): Bitmap? = loadImagePreview(file, 768)

    suspend fun loadFullImagePreview(file: LanShareFile): Bitmap? =
        loadImagePreview(file, LanShareImagePreviewDecoder.MAX_DECODE_EDGE)

    private suspend fun loadImagePreview(file: LanShareFile, maxEdge: Int): Bitmap? {
        if (!isLanShareImage(file.name, file.mimeType)) return null
        val session = _uiState.value.session ?: return null
        val ticket = refreshGuard.currentGeneration()
        val key = "${session.baseUrl}|${file.id}|${file.modifiedAt}|${file.size}|$maxEdge"
        decodedPreviews.get(key)?.let { return it }
        return previewDecodeSlots.withPermit { withContext(Dispatchers.IO) {
            if (!refreshGuard.isCurrent(session, ticket)) return@withContext null
            val localFile = lanShareGateway.localFile(file.id)
            val source = (localFile ?: previewFilesById[file.id])?.takeIf(java.io.File::isFile)
                ?: return@withContext null
            val bitmap = if (localFile != null && isLanShareTiff(file.name, file.mimeType)) {
                LanShareTiffPreviewDecoder.decode(source, maxEdge)
            } else {
                LanShareImagePreviewDecoder.decode(source, maxEdge)
            }
            if (refreshGuard.isCurrent(session, ticket)) {
                bitmap?.let { decodedPreviews.put(key, it) }
                bitmap
            } else {
                bitmap?.recycle()
                null
            }
        } }
    }

    fun uploadFile(session: LanShareSession, uri: android.net.Uri, temporaryFile: java.io.File? = null) {
        val ticket = refreshGuard.currentGeneration()
        val uploadId = UUID.randomUUID().toString()
        val job = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                if (!refreshGuard.isCurrent(session, ticket)) return@launch
                val source = createUploadSource(uri)
                val uploading = LanShareUploadingFile(
                    id = uploadId,
                    name = source.name,
                    size = source.size,
                    uploadedBytes = 0L,
                    startedAt = System.currentTimeMillis(),
                )
                refreshGuard.update(session, ticket) {
                    it.copy(uploadingFiles = it.uploadingFiles + uploading)
                }
                currentCoroutineContext().ensureActive()
                var lastProgressPercent = 0
                val id = lanShareGateway.upload(session, uploadId, source) { uploadedBytes, totalBytes ->
                    val percent = if (totalBytes <= 0L) 0 else
                        ((uploadedBytes.coerceIn(0L, totalBytes) * 100L) / totalBytes).toInt()
                    if (percent != lastProgressPercent) {
                        lastProgressPercent = percent
                        refreshGuard.update(session, ticket) { state ->
                            state.copy(uploadingFiles = state.uploadingFiles.map { current ->
                                if (current.id == uploadId) current.copy(uploadedBytes = uploadedBytes) else current
                            })
                        }
                    }
                }
                if (!refreshGuard.isCurrent(session, ticket)) return@launch
                val completedFile = LanShareFile(
                    id = id,
                    name = source.name,
                    size = source.size,
                    modifiedAt = System.currentTimeMillis(),
                    sender = "app",
                    mimeType = source.mimeType,
                )
                refreshGuard.update(session, ticket) { state ->
                    state.copy(
                        files = (state.files.filterNot { it.id == id } + completedFile)
                            .sortedBy(LanShareFile::modifiedAt),
                        ownFileIds = state.ownFileIds + id,
                    )
                }
                if (!_uiState.value.isHost) refreshFiles(session, showError = false)
                if (refreshGuard.isCurrent(session, ticket)) _events.send(LanShareEvent.Notice("上传成功"))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (refreshGuard.isCurrent(session, ticket)) _events.send(LanShareEvent.Error("上传失败"))
            }
        }
        val task = ActiveUploadTask(job, temporaryFile)
        activeUploadTasks[uploadId] = task
        job.invokeOnCompletion {
            activeUploadTasks.remove(uploadId, task)
            temporaryFile?.delete()
            _uiState.update { state ->
                state.copy(uploadingFiles = state.uploadingFiles.filterNot { it.id == uploadId })
            }
        }
        job.start()
    }

    fun cancelUpload(uploadId: String) {
        lanShareGateway.cancelUpload(uploadId)
        val task = activeUploadTasks.remove(uploadId)
        task?.job?.cancel(CancellationException("用户取消上传"))
        task?.temporaryFile?.delete()
        _uiState.update { state ->
            state.copy(uploadingFiles = state.uploadingFiles.filterNot { it.id == uploadId })
        }
    }

    fun sendText(session: LanShareSession, text: String) {
        if (!_uiState.value.isHost) {
            _events.trySend(LanShareEvent.Error("文字消息需由 App 创建分享房间后发送"))
            return
        }
        val ticket = refreshGuard.currentGeneration()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!refreshGuard.isCurrent(session, ticket)) return@launch
                val sentMessage = lanShareGateway.sendLocalMessage(text)
                if (!refreshGuard.isCurrent(session, ticket)) return@launch
                refreshGuard.update(session, ticket) { state ->
                    state.copy(messages = (state.messages + sentMessage)
                        .distinctBy(LanShareMessage::id)
                        .sortedBy(LanShareMessage::createdAt)
                        .takeLast(LAN_SHARE_MAX_SESSION_MESSAGES))
                }
                if (refreshGuard.isCurrent(session, ticket)) {
                    _events.send(LanShareEvent.Notice("发送成功", clearInput = true))
                }
            } catch (_: Exception) {
                if (refreshGuard.isCurrent(session, ticket)) _events.send(LanShareEvent.Error("发送失败"))
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
                        temporary.inputStream().use { it.copyTo(output, DOWNLOAD_DESTINATION_COPY_BUFFER_SIZE) }
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
        val previewFolder = java.io.File(appContext.cacheDir, "lan-share-preview").apply { mkdirs() }.canonicalFile
        val imageKeys = files.filter { isLanShareImage(it.name, it.mimeType) }
            .map { previewCacheKey(session, it.id) }
            .toSet()
        previewFolder.listFiles().orEmpty().filter { it.name !in imageKeys }.forEach { it.delete() }
        var cachedBytes = previewFolder.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
        return buildMap {
            files.filter { isLanShareImage(it.name, it.mimeType) && lanShareGateway.localFile(it.id) == null }.forEach { file ->
                currentCoroutineContext().ensureActive()
                val preview = java.io.File(previewFolder, previewCacheKey(session, file.id)).canonicalFile
                require(preview.parentFile == previewFolder) { "图片预览路径无效" }
                val remainingCacheBytes = (LAN_SHARE_PREVIEW_CACHE_MAX_BYTES - cachedBytes).coerceAtLeast(0L)
                val maxPreviewBytes = minOf(LAN_SHARE_PREVIEW_MAX_FILE_BYTES, remainingCacheBytes)
                if (!preview.isFile && maxPreviewBytes > 0L) {
                    runCatching { lanShareGateway.downloadPreview(session, file.id, preview, maxPreviewBytes) }
                    if (preview.isFile && preview.length() in 1..maxPreviewBytes) {
                        cachedBytes += preview.length()
                    } else {
                        preview.delete()
                    }
                }
                if (preview.isFile && preview.length() > 0L) put(file.id, preview)
            }
        }
    }

    private fun previewCacheKey(session: LanShareSession, fileId: String): String =
        lanSharePreviewCacheKey("thumbnail-v2\u0000${session.baseUrl}\u0000$fileId")

    private fun createUploadSource(uri: Uri): LanShareUploadSource {
        val name = appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
            } else null
        } ?: "附件"
        val size = appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        return LanShareUploadSource(
            name = name,
            size = size,
            mimeType = appContext.contentResolver.getType(uri),
        ) { appContext.contentResolver.openInputStream(uri) }
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

    private companion object {
        const val DOWNLOAD_DESTINATION_COPY_BUFFER_SIZE = 128 * 1024
    }

    override fun onCleared() {
        closeSession()
        super.onCleared()
    }

}
