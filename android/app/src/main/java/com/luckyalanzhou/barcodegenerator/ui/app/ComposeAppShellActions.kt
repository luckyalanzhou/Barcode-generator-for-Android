package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*

import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.presentation.UpdateUiState
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.ui.dialogs.captureText
import com.luckyalanzhou.barcodegenerator.ui.dialogs.checkForUpdates
import com.luckyalanzhou.barcodegenerator.ui.dialogs.restoreFavoritesImport
import com.luckyalanzhou.barcodegenerator.ui.dialogs.showComposeConfirmDialog as showComposeConfirmDialogImpl
import com.luckyalanzhou.barcodegenerator.ui.feature.results.shareResultPage
import com.luckyalanzhou.barcodegenerator.ui.feature.results.saveResultPage
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.*
import com.luckyalanzhou.barcodegenerator.ui.feature.editor.showItemEditorCompose
import com.luckyalanzhou.barcodegenerator.ui.feature.history.*
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.*
import com.luckyalanzhou.barcodegenerator.ui.support.logging.shareDebugLog
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog

/** Compose 根层可发出的动作；具体由宿主适配系统能力和暂存的旧 UI 流程。 */
internal interface ComposeAppShellActions {
    val resultExportAction: ResultExportAction?
    fun navigateTo(route: AppRoute)
    fun selectTab(index: Int, fromSwipe: Boolean)
    fun syncBarcodeDisplaySettings(isResults: Boolean)
    fun ensureLanShare()
    fun showUpdateDialog(update: UpdateUiState)
    fun captureText()
    fun notice(message: String)
    fun clearHistory()
    fun editHistory(batch: List<CodeItem>)
    fun editFavorite(group: FavoriteGroup)
    fun showSubfolderEditor(parent: String)
    fun showFolderEditor(initial: String, onSaved: (String) -> Unit)
    fun showMoveDialog(group: FavoriteGroup)
    fun showRenameDialog(group: FavoriteGroup)
    fun confirm(title: String, message: String, positive: String, onConfirm: () -> Unit)
    fun saveFavorite()
    fun shareResult()
    fun saveResult()
    fun updateResultImageWidth(width: Int)
    fun applyAppearance()
    fun enterLanShare()
    fun restoreFavorites()
    fun exportFavorites()
    fun checkForUpdates()
    fun openLanShareCamera()
    fun openLanShareGallery()
    fun openLanShareFiles()
    fun saveLanShareFile(file: LanShareFile)
    fun copyLanShareAddress(address: String)
    fun shareDebugLog()
}

/** Activity 只负责把 Android 系统能力适配到 Compose 动作边界。 */
internal fun MainActivity.composeAppShellActions(): ComposeAppShellActions = object : ComposeAppShellActions {
    private inline fun traceAction(name: String, details: String = "", block: () -> Unit) {
        DebugLog.actionStarted(name, details)
        try {
            block()
            DebugLog.actionSucceeded(name, details)
        } catch (error: Throwable) {
            DebugLog.actionFailed(name, error, details)
            throw error
        }
    }

    override val resultExportAction get() = this@composeAppShellActions.resultExportAction
    override fun navigateTo(route: AppRoute) = traceAction("navigate", "to=${route.pageName}") {
        if (route != AppRoute.Results) {
                resultsViewModel.cancelPendingResults()
                favoritesViewModel.cancelPendingGroupLoad()
            }
            navigationViewModel.navigateTo(route)
        }

    override fun selectTab(index: Int, fromSwipe: Boolean) = traceAction("tab_select", "index=$index swipe=$fromSwipe") {
        val routes = listOf(AppRoute.Generate, AppRoute.History, AppRoute.Favorites, AppRoute.Settings)
        if (index !in routes.indices) return@traceAction
        val currentPage = navigationViewModel.uiState.value.page
        val reselected = isTabReselection(currentPage, routes[index], fromSwipe)
        if (currentPage == AppRoute.LanShare) closeLanShare()
        resultsViewModel.cancelPendingResults()
        favoritesViewModel.cancelPendingGroupLoad()
        if (index == 1 && reselected) historyViewModel.refreshHistory()
        if (index == 2 && reselected) {
            favoritesViewModel.collapseAllFolders()
            favoritesViewModel.requestScrollToTop()
        }
        if (index == 3 && navigationViewModel.uiState.value.page != AppRoute.Settings) {
            val current = navigationViewModel.uiState.value.page
            val returnPage = current.takeIf { it.mainTabIndex != null && it != AppRoute.Settings }
                ?: resultsViewModel.resultUiState.value.returnPage
            navigationViewModel.updateSettingsReturnPage(returnPage)
        }
        if (index == 0) resultsViewModel.prepareMainGenerateTab()
        navigationViewModel.navigateTo(routes[index], fromSwipe)
    }

    override fun syncBarcodeDisplaySettings(isResults: Boolean) = this@composeAppShellActions.syncBarcodeDisplaySettings(isResults)
    override fun ensureLanShare() = this@composeAppShellActions.enterLanShare()

    override fun showUpdateDialog(update: UpdateUiState) {
        val latest = update.availableVersion
        val downloadUrl = update.availableUrl
        if (latest != null && downloadUrl != null) {
            showUpdateAvailableDialogCompose(
                latest = latest,
                downloadUrl = downloadUrl,
                expectedSize = update.expectedSize,
                expectedSha256 = update.sha256,
            )
        }
    }

    override fun captureText() = traceAction("capture_text") { this@composeAppShellActions.captureText() }
    override fun notice(message: String) = this@composeAppShellActions.toast(message)
    override fun clearHistory() = traceAction("history_clear_dialog") { this@composeAppShellActions.confirmClearCompose(false) }

    override fun editHistory(batch: List<CodeItem>) {
        traceAction("history_edit", "count=${batch.size}") {
            window.decorView.post {
                if (batch.size == 1) showItemEditorCompose(batch.first(), barcodeItemViewModel::updateBarcodeItem)
                else showHistoryBatchPickerCompose(batch)
            }
        }
    }

    override fun editFavorite(group: FavoriteGroup) {
        traceAction("favorite_edit", "groupId=${group.id}") {
            resultsViewModel.cancelFavoriteGroupRendering()
            favoritesViewModel.loadFavoriteGroupContent(
                group = group,
                onLoaded = { content ->
                    DebugLog.actionSucceeded("favorite_edit", "groupId=${group.id} loaded=${content.items.size}")
                    resultsViewModel.prepareFavoriteGroupForEditing(content) { batch ->
                        window.decorView.post {
                            if (!isFinishing && !isDestroyed) {
                                if (batch.size == 1) showItemEditorCompose(batch.first(), barcodeItemViewModel::updateBarcodeItem)
                                else showHistoryBatchPickerCompose(batch)
                            }
                        }
                    }
                },
                onNotice = { message ->
                    DebugLog.record("action", "favorite_edit notice groupId=${group.id} message=${message.take(80)}")
                    this@composeAppShellActions.toast(message)
                },
            )
        }
    }

    override fun showSubfolderEditor(parent: String) = this@composeAppShellActions.showSubfolderEditorCompose(
        dataState = favoritesViewModel.dataState.value,
        parent = parent,
        onCreateFolder = { favoritesViewModel.createFavoriteFolder(it) },
    )
    override fun showFolderEditor(initial: String, onSaved: (String) -> Unit) =
        this@composeAppShellActions.showFolderEditorCompose(favoritesViewModel.dataState.value, initial, onSaved = onSaved)
    override fun showMoveDialog(group: FavoriteGroup) = this@composeAppShellActions.showFavoriteMoveDialogCompose(
        group = group,
        dataState = favoritesViewModel.dataState.value,
        onMove = favoritesViewModel::moveFavoriteGroup,
        onNavigateFavorites = { navigationViewModel.navigateTo(AppRoute.Favorites) },
    )
    override fun showRenameDialog(group: FavoriteGroup) = this@composeAppShellActions.showFavoriteRenameDialogCompose(
        group = group,
        onRename = favoritesViewModel::renameFavoriteGroup,
        onNavigateFavorites = { navigationViewModel.navigateTo(AppRoute.Favorites) },
    )
    override fun confirm(title: String, message: String, positive: String, onConfirm: () -> Unit) =
        this@composeAppShellActions.showComposeConfirmDialogImpl(title, message, positive, onConfirm)
    override fun saveFavorite() {
        DebugLog.actionStarted("favorite_save_dialog", "resultCount=${resultsViewModel.resultUiState.value.items.size}")
        this@composeAppShellActions.saveResultAsFavoriteCompose(
            resultState = resultsViewModel.resultUiState.value,
            dataState = favoritesViewModel.dataState.value,
            onSave = { itemIds, editingGroupId, targetGroupId, folder, name ->
                favoritesViewModel.saveResultAsFavorite(itemIds, editingGroupId, targetGroupId, folder, name)
            },
            onCreateFolder = favoritesViewModel::createFavoriteFolder,
            onSaved = {
                DebugLog.actionSucceeded("favorite_save_dialog", "saved=true")
                resultsViewModel.clearSelectedFavoriteGroup()
                navigationViewModel.navigateTo(AppRoute.Favorites)
            },
        )
    }
    override fun shareResult() = traceAction("result_share") { this@composeAppShellActions.shareResultPage() }
    override fun saveResult() = traceAction("result_save") { this@composeAppShellActions.saveResultPage() }
    override fun updateResultImageWidth(width: Int) { resultImageContentWidthPx = width }
    override fun applyAppearance() = this@composeAppShellActions.applyAppearance()
    override fun enterLanShare() = traceAction("lan_share_enter") { this@composeAppShellActions.enterLanShare() }
    override fun restoreFavorites() = this@composeAppShellActions.restoreFavoritesImport()
    override fun exportFavorites() = this@composeAppShellActions.createFavoritesExportCompose()
    override fun checkForUpdates() = this@composeAppShellActions.checkForUpdates(silent = false)
    override fun openLanShareCamera() = this@composeAppShellActions.openLanShareCamera()
    override fun openLanShareGallery() = this@composeAppShellActions.openLanShareGallery()
    override fun openLanShareFiles() = this@composeAppShellActions.openLanShareFiles()
    override fun saveLanShareFile(file: LanShareFile) = this@composeAppShellActions.saveLanShareFile(file)

    override fun copyLanShareAddress(address: String) {
        (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
            .setPrimaryClip(android.content.ClipData.newPlainText("局域网传输地址", address))
        this@composeAppShellActions.toast("已复制局域网传输地址")
    }

    override fun shareDebugLog() = this@composeAppShellActions.shareDebugLog()
}
