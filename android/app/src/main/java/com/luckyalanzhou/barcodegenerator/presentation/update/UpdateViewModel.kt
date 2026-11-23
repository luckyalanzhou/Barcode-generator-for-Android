package com.luckyalanzhou.barcodegenerator.presentation.update

import com.luckyalanzhou.barcodegenerator.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.ApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateFailure
import com.luckyalanzhou.barcodegenerator.presentation.UpdateCheckResult
import com.luckyalanzhou.barcodegenerator.presentation.UpdateDownloadUiState
import com.luckyalanzhou.barcodegenerator.presentation.UpdateEvent
import com.luckyalanzhou.barcodegenerator.presentation.UpdateUiState
import com.luckyalanzhou.barcodegenerator.presentation.UpdateDownloadRequest
import com.luckyalanzhou.barcodegenerator.presentation.shared.recordSafely
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

private const val MANUAL_UPDATE_CHECK_TIMEOUT_MILLIS = 18_000L

/** 更新页状态与操作的所有者，独立于条码、历史和收藏状态。 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    updateDownloadGateway: ApkDownloadGateway,
    updateCatalogGateway: UpdateCatalogGateway,
    apkValidationGateway: ApkValidationGateway,
    private val logger: AppLogger,
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val updateFacade = UpdateFacade(
        updateDownloadGateway,
        updateCatalogGateway,
        apkValidationGateway,
        logger,
        savedStateHandle,
    )

    val uiState: StateFlow<UpdateUiState> = updateFacade.uiState
    val downloadUiState: StateFlow<UpdateDownloadUiState> = updateFacade.downloadUiState
    val events: Flow<UpdateEvent> = updateFacade.events
    private val manualCheckResultChannel = Channel<UpdateCheckResult>(Channel.BUFFERED)
    val manualCheckResults: Flow<UpdateCheckResult> = manualCheckResultChannel.receiveAsFlow()
    private val _manualCheckResult = MutableStateFlow<UpdateCheckResult?>(null)
    val manualCheckResult: StateFlow<UpdateCheckResult?> = _manualCheckResult.asStateFlow()
    private val ignoreOperation = AtomicLong(0L)

    fun setStartupCheckStarted(value: Boolean) = updateFacade.setStartupCheckStarted(value)
    /** 并发检查时等待并重试，确保手动检查不会被静默启动检查吞掉。 */
    suspend fun checkForUpdates(): UpdateCheckResult {
        while (true) {
            when (val result = updateFacade.checkForUpdates()) {
                UpdateCheckResult.InProgress -> uiState.first { !it.checking }
                else -> return result
            }
        }
    }
    /** 手动更新检查归 ViewModel 持有，Activity 重建后仍可消费完成结果。 */
    fun startManualCheck(showCompletionNotice: Boolean = true) =
        startManualCheck(viewModelScope, showCompletionNotice)

    internal fun startManualCheck(scope: CoroutineScope, showCompletionNotice: Boolean = true) {
        if (_manualCheckResult.value == UpdateCheckResult.InProgress) return
        _manualCheckResult.value = UpdateCheckResult.InProgress
        scope.launch {
            // The UI deadline must also hold when a blocking connection takes time to unwind.
            val request = scope.async { checkForUpdates() }
            val result = try {
                withTimeoutOrNull(MANUAL_UPDATE_CHECK_TIMEOUT_MILLIS) { request.await() }
                    ?: UpdateCheckResult.Failed(UpdateFailure.CheckFailed)
            } catch (cancelled: CancellationException) {
                _manualCheckResult.value = null
                throw cancelled
            } finally {
                request.cancel()
            }
            _manualCheckResult.value = result
            if (result is UpdateCheckResult.Available && uiState.value.availableUpdateKey == result.ignoreKey) {
                setDialogShowing(true)
            }
            if (showCompletionNotice || result is UpdateCheckResult.Available) {
                manualCheckResultChannel.send(result)
            }
        }
    }
    /** 静默启动检查由 ViewModel 持有，Activity 因配置变化销毁时请求仍可完成。 */
    fun startStartupCheck() = startStartupCheck(viewModelScope)

    internal fun startStartupCheck(scope: CoroutineScope) {
        if (uiState.value.startupCheckStarted) return
        setStartupCheckStarted(true)
        scope.launch {
            while (true) {
                when (val result = checkForUpdates()) {
                    UpdateCheckResult.InProgress -> uiState.first { !it.checking }
                    is UpdateCheckResult.Available -> {
                        val storedIgnoredVersion = try {
                            settingsRepository.getIgnoredUpdateVersion()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (interrupted: InterruptedException) {
                            Thread.currentThread().interrupt()
                            logger.recordSafely("update", "ignored version read interrupted", interrupted, interrupted)
                            null
                        } catch (error: Exception) {
                            logger.recordSafely("update", "ignored version read failed", error, error)
                            null
                        }
                        // 旧版只存版本名；首次命中时迁移为当前构建身份，兼容已有忽略记录。
                        val ignoredVersion = if (storedIgnoredVersion == result.version && result.ignoreKey != result.version) {
                            try {
                                settingsRepository.setIgnoredUpdateVersion(result.ignoreKey)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                logger.recordSafely("update", "ignored version migration failed", error, error)
                            }
                            result.ignoreKey
                        } else {
                            storedIgnoredVersion
                        }
                        // 设置读取可能等待；手动检查期间若当前版本已被替换或清空，不展示旧启动结果。
                        if (uiState.value.availableUpdateKey == result.ignoreKey && ignoredVersion != result.ignoreKey) {
                            setDialogShowing(true)
                        }
                        return@launch
                    }
                    UpdateCheckResult.UpToDate -> return@launch
                    is UpdateCheckResult.Failed -> return@launch
                }
            }
        }
    }

    /** 先确认版本已持久化，再关闭弹窗，避免失败后仍声称已忽略。 */
    fun ignoreAvailableUpdate(onIgnored: () -> Unit = {}, onFailure: (Int) -> Unit) =
        ignoreAvailableUpdate(viewModelScope, onIgnored, onFailure)

    internal fun ignoreAvailableUpdate(
        scope: CoroutineScope,
        onIgnored: () -> Unit = {},
        onFailure: (Int) -> Unit,
    ) {
        val version = uiState.value.availableVersion ?: return
        val ignoreKey = uiState.value.availableUpdateKey ?: version
        val operation = ignoreOperation.incrementAndGet()
        logger.recordSafely("update", "ignore start operation=$operation version=$version")
        scope.launch {
            try {
                settingsRepository.setIgnoredUpdateVersion(ignoreKey)
            } catch (cancelled: CancellationException) {
                logger.recordSafely("update", "ignore cancelled operation=$operation", primaryError = cancelled)
                throw cancelled
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.recordSafely("update", "ignore interrupted operation=$operation", interrupted, interrupted)
                onFailure(R.string.ignore_update_failed)
                return@launch
            } catch (error: Exception) {
                logger.recordSafely("update", "ignore failed operation=$operation", error, error)
                onFailure(R.string.ignore_update_failed)
                return@launch
            }
            if (ignoreOperation.get() != operation || uiState.value.availableUpdateKey != ignoreKey) {
                logger.recordSafely("update", "ignore result stale operation=$operation version=$version")
                return@launch
            }
            setDialogShowing(false)
            onIgnored()
            logger.recordSafely("update", "ignore success operation=$operation version=$version")
        }
    }

    fun setAvailableUpdate(version: String?, url: String?, expectedSize: Long?, sha256: String?, ignoreKey: String? = version) =
        updateFacade.setAvailableUpdate(version, url, expectedSize, sha256, ignoreKey)
    fun clearAvailableUpdate() = updateFacade.clearAvailableUpdate()
    fun setDialogShowing(value: Boolean) = updateFacade.setDialogShowing(value)
    fun setDownloadRunning(value: Boolean) = updateFacade.setDownloadRunning(value)
    fun resetDownloadState() = updateFacade.resetDownloadState()
    fun setDownloadProgress(progress: Int, indeterminate: Boolean, downloadedBytes: Long) =
        updateFacade.setDownloadProgress(progress, indeterminate, downloadedBytes)
    suspend fun downloadUpdate(apkUrl: String, expectedSize: Long?, expectedSha256: String?): File =
        updateFacade.downloadUpdate(apkUrl, expectedSize, expectedSha256)
    fun validateDownloadedApk(file: File) = updateFacade.validateDownloadedApk(file)
    fun startDownload(apkUrl: String, expectedSize: Long?, expectedSha256: String?) =
        updateFacade.startDownload(viewModelScope, apkUrl, expectedSize, expectedSha256)
    fun cancelDownload() = updateFacade.cancelDownload()
    fun clearActiveDownloadRequest(expected: UpdateDownloadRequest? = null) =
        updateFacade.clearActiveDownloadRequest(expected)
    fun setPendingInstallPath(path: String?) = updateFacade.setPendingInstallPath(path)
    fun takePendingInstallPath(): String? = updateFacade.takePendingInstallPath()
}
