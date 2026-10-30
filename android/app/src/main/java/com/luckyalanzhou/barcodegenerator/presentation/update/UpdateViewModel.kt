package com.luckyalanzhou.barcodegenerator.presentation.update

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.ApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.presentation.UpdateCheckResult
import com.luckyalanzhou.barcodegenerator.presentation.UpdateDownloadUiState
import com.luckyalanzhou.barcodegenerator.presentation.UpdateEvent
import com.luckyalanzhou.barcodegenerator.presentation.UpdateUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 更新页状态与操作的所有者，独立于条码、历史和收藏状态。 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    updateDownloadGateway: ApkDownloadGateway,
    updateCatalogGateway: UpdateCatalogGateway,
    apkValidationGateway: ApkValidationGateway,
    logger: AppLogger,
    savedStateHandle: SavedStateHandle,
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

    fun setStartupCheckStarted(value: Boolean) = updateFacade.setStartupCheckStarted(value)
    suspend fun checkForUpdates(): UpdateCheckResult = updateFacade.checkForUpdates()
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
                        setDialogShowing(true)
                        return@launch
                    }
                    UpdateCheckResult.UpToDate -> return@launch
                    is UpdateCheckResult.Failed -> return@launch
                }
            }
        }
    }

    fun setAvailableUpdate(version: String?, url: String?, expectedSize: Long?, sha256: String?) =
        updateFacade.setAvailableUpdate(version, url, expectedSize, sha256)
    fun clearAvailableUpdate() = updateFacade.clearAvailableUpdate()
    fun setDialogShowing(value: Boolean) = updateFacade.setDialogShowing(value)
    fun setDownloadRunning(value: Boolean) = updateFacade.setDownloadRunning(value)
    fun resetDownloadState() = updateFacade.resetDownloadState()
    fun setDownloadProgress(progress: Int, indeterminate: Boolean, status: String) =
        updateFacade.setDownloadProgress(progress, indeterminate, status)
    suspend fun downloadUpdate(apkUrl: String, expectedSize: Long?, expectedSha256: String?): File =
        updateFacade.downloadUpdate(apkUrl, expectedSize, expectedSha256)
    fun validateDownloadedApk(file: File) = updateFacade.validateDownloadedApk(file)
    fun startDownload(apkUrl: String, expectedSize: Long?, expectedSha256: String?) =
        updateFacade.startDownload(viewModelScope, apkUrl, expectedSize, expectedSha256)
    fun cancelDownload() = updateFacade.cancelDownload()
    fun setPendingInstallPath(path: String?) = updateFacade.setPendingInstallPath(path)
    fun takePendingInstallPath(): String? = updateFacade.takePendingInstallPath()
}
