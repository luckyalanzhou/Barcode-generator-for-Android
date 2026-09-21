package com.luckyalanzhou.barcodegenerator

import android.net.Uri
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.NavigationRoute as AppRoute
import java.io.File

data class AppUiState(
    val page: AppRoute = AppRoute.Generate,
    val selectedTab: Int = 0,
    val settingsReturnPage: AppRoute = AppRoute.Generate,
)

data class BarcodeDataState(
    val items: List<CodeItem> = emptyList(),
    val groups: List<FavoriteGroup> = emptyList(),
    val folders: List<String> = emptyList(),
    val isReady: Boolean = false,
)

data class GenerateEditorState(
    val inputDraft: List<String> = emptyList(),
    val pendingFormat: String? = null,
    val formatName: String = "Code 128-B",
)

data class ResultUiState(
    val items: List<CodeItem> = emptyList(),
    val showingHistoryResult: Boolean = false,
    val returnPage: AppRoute = AppRoute.Generate,
    val selectedFavoriteGroup: FavoriteGroup? = null,
)

data class FavoriteTreeUiState(
    val collapsedFolders: Set<String> = emptySet(),
    val initialized: Boolean = false,
    val knownFolders: Set<String> = emptySet(),
    val collapsedBeforeSearch: Set<String>? = null,
)

data class CameraCaptureState(
    val requestCode: Int = 43,
    val outputUri: Uri? = null,
    val outputFile: File? = null,
    val startedAtMillis: Long = 0L,
)

data class UpdateUiState(
    val startupCheckStarted: Boolean = false,
    val availableVersion: String? = null,
    val availableUrl: String? = null,
    val expectedSize: Long? = null,
    val sha256: String? = null,
    val dialogShowing: Boolean = false,
    val downloadRunning: Boolean = false,
    val pendingInstallPath: String? = null,
)

data class UpdateDownloadUiState(
    val progress: Int = 0,
    val indeterminate: Boolean = false,
    val status: String = "准备下载…",
)

sealed interface UpdateEvent {
    data class DownloadReady(val filePath: String) : UpdateEvent
    data class DownloadFailed(
        val apkUrl: String,
        val expectedSize: Long?,
        val expectedSha256: String?,
        val reason: String,
    ) : UpdateEvent
}

sealed interface UpdateCheckResult {
    data class Available(
        val version: String,
        val downloadUrl: String,
        val expectedSize: Long?,
        val expectedSha256: String?,
    ) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data class Failed(val reason: String) : UpdateCheckResult
}

sealed interface BarcodeEvent {
    data class RecognizedText(val lines: List<String>) : BarcodeEvent
    data class Notice(val message: String) : BarcodeEvent
}
