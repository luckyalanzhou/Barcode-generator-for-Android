package com.luckyalanzhou.barcodegenerator.presentation.update

import com.luckyalanzhou.barcodegenerator.domain.UpdateFailure
import com.luckyalanzhou.barcodegenerator.domain.UpdateFailureCause
import androidx.lifecycle.SavedStateHandle
import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.ApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateLookupResult
import com.luckyalanzhou.barcodegenerator.presentation.shared.recordSafely
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 更新领域的协调器，负责检查、下载、校验以及一次性更新事件。 */
internal class UpdateCoordinator(
    private val updateDownloadGateway: ApkDownloadGateway,
    private val updateCatalogGateway: UpdateCatalogGateway,
    private val apkValidationGateway: ApkValidationGateway,
    private val logger: AppLogger,
    private val savedStateHandle: SavedStateHandle,
) {
    private val _uiState = MutableStateFlow(
        UpdateUiState(pendingInstallPath = savedStateHandle[PENDING_INSTALL_PATH]),
    )
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private val _downloadUiState = MutableStateFlow(UpdateDownloadUiState())
    val downloadUiState: StateFlow<UpdateDownloadUiState> = _downloadUiState.asStateFlow()

    private val _events = Channel<UpdateEvent>(Channel.BUFFERED)
    val events: Flow<UpdateEvent> = _events.receiveAsFlow()

    private val downloadGeneration = AtomicLong(0L)
    private var downloadJob: Job? = null
    private val activeCheck = AtomicReference<CompletableDeferred<UpdateCheckResult>?>(null)

    fun setStartupCheckStarted(value: Boolean) {
        _uiState.update { it.copy(startupCheckStarted = value) }
    }

    suspend fun checkForUpdates(joinExisting: Boolean = false): UpdateCheckResult {
        val request = CompletableDeferred<UpdateCheckResult>()
        while (!activeCheck.compareAndSet(null, request)) {
            val running = activeCheck.get() ?: continue
            if (joinExisting) return running.await()
            recordSafely("check skipped reason=in_progress", null, null)
            return UpdateCheckResult.InProgress
        }
        _uiState.update { it.copy(checking = true) }
        try {
            return fetchUpdate().also { request.complete(it) }
        } catch (failure: Throwable) {
            request.completeExceptionally(failure)
            throw failure
        } finally {
            _uiState.update { it.copy(checking = false) }
            activeCheck.compareAndSet(request, null)
        }
    }

    private suspend fun fetchUpdate(): UpdateCheckResult {
        try {
            recordSafely("check start", null, null)
            return when (val result = updateCatalogGateway.check()) {
                is UpdateLookupResult.Available -> {
                    recordSafely("check available version=${result.version}", null, null)
                    setAvailableUpdate(result.version, result.downloadUrl, result.expectedSize, result.expectedSha256, result.ignoreKey)
                    UpdateCheckResult.Available(
                        result.version,
                        result.downloadUrl,
                        result.expectedSize,
                        result.expectedSha256,
                        result.ignoreKey,
                    )
                }
                UpdateLookupResult.UpToDate -> {
                    recordSafely("check up_to_date", null, null)
                    clearAvailableUpdate()
                    UpdateCheckResult.UpToDate
                }
                is UpdateLookupResult.Failed -> {
                    recordSafely("check failed reason=${result.diagnostic}", null, null)
                    clearAvailableUpdate()
                    UpdateCheckResult.Failed(result.failure, result.httpStatus)
                }
            }
        } catch (error: CancellationException) {
            recordSafely("check cancelled", null, error)
            throw error
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (error: Exception) {
            recordSafely("check failed exception", error, error)
            return UpdateCheckResult.Failed(UpdateFailure.CheckFailed)
        }
    }

    fun setAvailableUpdate(version: String?, url: String?, expectedSize: Long?, sha256: String?, ignoreKey: String? = version) {
        _uiState.update {
            it.copy(
                availableVersion = version,
                availableUpdateKey = if (version == null) null else ignoreKey ?: version,
                availableUrl = url,
                expectedSize = expectedSize,
                sha256 = sha256,
            )
        }
    }

    fun clearAvailableUpdate() = setAvailableUpdate(null, null, null, null, null)

    fun setDialogShowing(value: Boolean) {
        _uiState.update { it.copy(dialogShowing = value) }
    }

    fun setDownloadRunning(value: Boolean) {
        _uiState.update { it.copy(downloadRunning = value) }
    }

    fun resetDownloadState() {
        _downloadUiState.value = UpdateDownloadUiState()
    }

    fun setDownloadProgress(progress: Int, indeterminate: Boolean, downloadedBytes: Long) {
        _downloadUiState.value = UpdateDownloadUiState(
            progress = progress.coerceIn(0, 100),
            indeterminate = indeterminate,
            downloadedBytes = downloadedBytes,
        )
    }

    suspend fun downloadUpdate(
        apkUrl: String,
        expectedSize: Long?,
        expectedSha256: String?,
    ): File = updateDownloadGateway.download(apkUrl, expectedSize, expectedSha256) { progress, indeterminate, downloadedBytes ->
        setDownloadProgress(progress, indeterminate, downloadedBytes)
    }

    fun validateDownloadedApk(file: File) {
        apkValidationGateway.validate(file)
    }

    fun startDownload(scope: CoroutineScope, apkUrl: String, expectedSize: Long?, expectedSha256: String?) {
        if (_uiState.value.downloadRunning) return
        val request = UpdateDownloadRequest(apkUrl, expectedSize, expectedSha256)
        val generation = downloadGeneration.incrementAndGet()
        logger.recordSafely("update", "download start operation=$generation expectedBytes=$expectedSize")
        resetDownloadState()
        _uiState.update { it.copy(downloadRunning = true, activeDownloadRequest = request) }
        downloadJob = scope.launch {
            try {
                val file = updateDownloadGateway.download(apkUrl, expectedSize, expectedSha256) { progress, indeterminate, downloadedBytes ->
                    if (downloadGeneration.get() == generation) setDownloadProgress(progress, indeterminate, downloadedBytes)
                }
                if (downloadGeneration.get() != generation) {
                    file.delete()
                    return@launch
                }
                _events.send(UpdateEvent.DownloadReady(file.absolutePath))
                logger.recordSafely("update", "download ready operation=$generation bytes=${file.length()}")
            } catch (cancelled: CancellationException) {
                logger.recordSafely(
                    "update",
                    "download cancelled operation=$generation",
                    primaryError = cancelled,
                )
                // 用户取消下载时不显示失败提示。
            } catch (error: Exception) {
                if (downloadGeneration.get() != generation) return@launch
                logger.recordSafely("update", "download failed", error, error)
                _events.send(
                    UpdateEvent.DownloadFailed(
                        apkUrl = apkUrl,
                        expectedSize = expectedSize,
                        expectedSha256 = expectedSha256,
                        reason = (error as? UpdateFailureCause)?.failure ?: UpdateFailure.DownloadFailed,
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
        updateDownloadGateway.cancel()
        downloadJob = null
        _uiState.update { it.copy(downloadRunning = false, activeDownloadRequest = null) }
    }

    fun clearActiveDownloadRequest(expected: UpdateDownloadRequest? = null) {
        _uiState.update { state ->
            if (expected == null || state.activeDownloadRequest == expected) {
                state.copy(activeDownloadRequest = null)
            } else {
                state
            }
        }
    }

    fun setPendingInstallPath(path: String?) {
        if (path == null) {
            savedStateHandle.remove<String>(PENDING_INSTALL_PATH)
        } else {
            savedStateHandle[PENDING_INSTALL_PATH] = path
        }
        _uiState.update { it.copy(pendingInstallPath = path) }
    }

    fun takePendingInstallPath(): String? {
        val path = _uiState.value.pendingInstallPath
        savedStateHandle.remove<String>(PENDING_INSTALL_PATH)
        _uiState.update { it.copy(pendingInstallPath = null) }
        return path
    }

    private companion object {
        const val PENDING_INSTALL_PATH = "update.pendingInstallPath"
    }

    private fun recordSafely(message: String, diagnosticError: Throwable?, primaryError: Throwable?) {
        logger.recordSafely("update", message, diagnosticError, primaryError)
    }
}
