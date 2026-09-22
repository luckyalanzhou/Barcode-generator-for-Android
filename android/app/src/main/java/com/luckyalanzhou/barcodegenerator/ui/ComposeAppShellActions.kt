package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.BarcodeViewModel
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.UpdateUiState
import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import com.luckyalanzhou.barcodegenerator.domain.FavoriteGroup
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile
import com.luckyalanzhou.barcodegenerator.ui.dialogs.captureText
import com.luckyalanzhou.barcodegenerator.ui.dialogs.checkForUpdates
import com.luckyalanzhou.barcodegenerator.ui.dialogs.restoreFavoritesImport

/** Compose 根层可发出的动作；具体由宿主适配系统能力和暂存的旧 UI 流程。 */
internal interface ComposeAppShellActions {
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
    override fun navigateTo(route: AppRoute) = viewModel.navigateTo(route)

    override fun selectTab(index: Int, fromSwipe: Boolean) {
        if (viewModel.uiState.value.page == AppRoute.LanShare) closeLanShare()
        viewModel.selectMainTab(index, fromSwipe)
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

    override fun captureText() = this@composeAppShellActions.captureText()
    override fun notice(message: String) = this@composeAppShellActions.toast(message)
    override fun clearHistory() = this@composeAppShellActions.confirmClearCompose(false)

    override fun editHistory(batch: List<CodeItem>) {
        if (batch.size == 1) this@composeAppShellActions.showItemEditorCompose(batch.first())
        else this@composeAppShellActions.showHistoryBatchPickerCompose(batch)
    }

    override fun editFavorite(group: FavoriteGroup) {
        viewModel.loadFavoriteGroupForEditing(group) { batch ->
            if (batch.size == 1) this@composeAppShellActions.showItemEditorCompose(batch.first())
            else this@composeAppShellActions.showHistoryBatchPickerCompose(batch)
        }
    }

    override fun showSubfolderEditor(parent: String) = this@composeAppShellActions.showSubfolderEditorCompose(parent)
    override fun showFolderEditor(initial: String, onSaved: (String) -> Unit) =
        this@composeAppShellActions.showFolderEditorCompose(initial, onSaved = onSaved)
    override fun showMoveDialog(group: FavoriteGroup) = this@composeAppShellActions.showFavoriteMoveDialogCompose(group)
    override fun showRenameDialog(group: FavoriteGroup) = this@composeAppShellActions.showFavoriteRenameDialogCompose(group)
    override fun confirm(title: String, message: String, positive: String, onConfirm: () -> Unit) =
        this@composeAppShellActions.showComposeConfirmDialog(title, message, positive, onConfirm)
    override fun saveFavorite() = this@composeAppShellActions.saveResultAsFavoriteCompose()
    override fun shareResult() = this@composeAppShellActions.shareResultPage()
    override fun applyAppearance() = this@composeAppShellActions.applyAppearance()
    override fun enterLanShare() = this@composeAppShellActions.enterLanShare()
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
