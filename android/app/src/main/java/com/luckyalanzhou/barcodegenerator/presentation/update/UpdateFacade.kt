package com.luckyalanzhou.barcodegenerator.presentation.update

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.ApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.presentation.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 更新功能的组合边界；UI 通过 UpdateViewModel 访问它。 */
internal class UpdateFacade(
    updateDownloadGateway: ApkDownloadGateway,
    updateCatalogGateway: UpdateCatalogGateway,
    apkValidationGateway: ApkValidationGateway,
    logger: AppLogger,
) {
    private val coordinator = UpdateCoordinator(
        updateDownloadGateway,
        updateCatalogGateway,
        apkValidationGateway,
        logger,
    )

    val uiState: StateFlow<UpdateUiState> = coordinator.uiState
    val downloadUiState: StateFlow<UpdateDownloadUiState> = coordinator.downloadUiState
    val events: SharedFlow<UpdateEvent> = coordinator.events

    fun setStartupCheckStarted(value: Boolean) = coordinator.setStartupCheckStarted(value)
    suspend fun checkForUpdates(): UpdateCheckResult = coordinator.checkForUpdates()
    fun setAvailableUpdate(version: String?, url: String?, expectedSize: Long?, sha256: String?) =
        coordinator.setAvailableUpdate(version, url, expectedSize, sha256)
    fun clearAvailableUpdate() = coordinator.clearAvailableUpdate()
    fun setDialogShowing(value: Boolean) = coordinator.setDialogShowing(value)
    fun setDownloadRunning(value: Boolean) = coordinator.setDownloadRunning(value)
    fun resetDownloadState() = coordinator.resetDownloadState()
    fun setDownloadProgress(progress: Int, indeterminate: Boolean, status: String) =
        coordinator.setDownloadProgress(progress, indeterminate, status)
    suspend fun downloadUpdate(apkUrl: String, expectedSize: Long?, expectedSha256: String?): File =
        coordinator.downloadUpdate(apkUrl, expectedSize, expectedSha256)
    fun validateDownloadedApk(file: File) = coordinator.validateDownloadedApk(file)
    fun startDownload(scope: CoroutineScope, apkUrl: String, expectedSize: Long?, expectedSha256: String?) =
        coordinator.startDownload(scope, apkUrl, expectedSize, expectedSha256)
    fun cancelDownload() = coordinator.cancelDownload()
    fun setPendingInstallPath(path: String?) = coordinator.setPendingInstallPath(path)
    fun takePendingInstallPath(): String? = coordinator.takePendingInstallPath()
}
