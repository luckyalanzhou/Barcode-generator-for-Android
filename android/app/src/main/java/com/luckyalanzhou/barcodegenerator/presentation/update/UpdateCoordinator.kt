package com.luckyalanzhou.barcodegenerator.presentation.update

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 更新领域的协调器，负责检查、下载、校验以及一次性更新事件。 */
class UpdateCoordinator(
    private val updateDownloadService: UpdateDownloadService,
    private val updateCheckService: UpdateCheckService,
    private val apkUpdateValidator: ApkUpdateValidator,
    private val logger: AppLogger,
) {
    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _downloadUiState = MutableStateFlow(UpdateDownloadUiState())
    val downloadUiState: StateFlow<UpdateDownloadUiState> = _downloadUiState.asStateFlow()

    private val _events = MutableSharedFlow<UpdateEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<UpdateEvent> = _events.asSharedFlow()

    private val downloadGeneration = AtomicLong(0L)
    private var downloadJob: Job? = null

    fun setStartupCheckStarted(value: Boolean) {
        _uiState.update { it.copy(startupCheckStarted = value) }
    }

    suspend fun checkForUpdates(): UpdateCheckResult = updateCheckService.check().also { result ->
        if (result is UpdateCheckResult.Available) {
            setAvailableUpdate(result.version, result.downloadUrl, result.expectedSize, result.expectedSha256)
        } else {
            clearAvailableUpdate()
        }
    }

    fun setAvailableUpdate(version: String?, url: String?, expectedSize: Long?, sha256: String?) {
        _uiState.update {
            it.copy(
                availableVersion = version,
                availableUrl = url,
                expectedSize = expectedSize,
                sha256 = sha256,
            )
        }
    }

    fun clearAvailableUpdate() = setAvailableUpdate(null, null, null, null)

    fun setDialogShowing(value: Boolean) {
        _uiState.update { it.copy(dialogShowing = value) }
    }

    fun setDownloadRunning(value: Boolean) {
        _uiState.update { it.copy(downloadRunning = value) }
    }

    fun resetDownloadState() {
        _downloadUiState.value = UpdateDownloadUiState()
    }

    fun setDownloadProgress(progress: Int, indeterminate: Boolean, status: String) {
        _downloadUiState.value = UpdateDownloadUiState(
            progress = progress.coerceIn(0, 100),
            indeterminate = indeterminate,
            status = status,
        )
    }

    suspend fun downloadUpdate(
        apkUrl: String,
        expectedSize: Long?,
        expectedSha256: String?,
    ): File = updateDownloadService.download(apkUrl, expectedSize, expectedSha256) { progress, indeterminate, status ->
        setDownloadProgress(progress, indeterminate, status)
    }

    fun validateDownloadedApk(file: File) {
        apkUpdateValidator.validate(file)
    }

    fun startDownload(scope: CoroutineScope, apkUrl: String, expectedSize: Long?, expectedSha256: String?) {
        if (_uiState.value.downloadRunning) return
        val generation = downloadGeneration.incrementAndGet()
        resetDownloadState()
        setDownloadRunning(true)
        downloadJob = scope.launch {
            try {
                val file = downloadUpdate(apkUrl, expectedSize, expectedSha256)
                _events.emit(UpdateEvent.DownloadReady(file.absolutePath))
            } catch (_: CancellationException) {
                // 用户取消下载时不显示失败提示。
            } catch (error: Exception) {
                logger.record("update", "download failed", error)
                _events.emit(
                    UpdateEvent.DownloadFailed(
                        apkUrl = apkUrl,
                        expectedSize = expectedSize,
                        expectedSha256 = expectedSha256,
                        reason = error.message ?: "未知错误",
                    )
                )
            } finally {
                if (downloadGeneration.get() == generation) {
                    setDownloadRunning(false)
                    downloadJob = null
                }
            }
        }
    }

    fun cancelDownload() {
        downloadGeneration.incrementAndGet()
        downloadJob?.cancel()
        downloadJob = null
        setDownloadRunning(false)
    }

    fun setPendingInstallPath(path: String?) {
        _uiState.update { it.copy(pendingInstallPath = path) }
    }

    fun takePendingInstallPath(): String? {
        val path = _uiState.value.pendingInstallPath
        _uiState.update { it.copy(pendingInstallPath = null) }
        return path
    }
}
