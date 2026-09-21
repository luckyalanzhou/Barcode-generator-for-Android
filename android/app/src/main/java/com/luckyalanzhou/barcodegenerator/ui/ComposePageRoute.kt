package com.luckyalanzhou.barcodegenerator.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 页面路由渲染器；页面键由状态层保存，路由元数据由 UI 层解释。 */
@Composable
internal fun ComposeNavigationHost(dependencies: ComposeAppShellDependencies, displayPage: AppRoute, dark: Boolean) {
    ComposePageRoute(dependencies, displayPage, dark)
}

@Composable
private fun ComposePageRoute(dependencies: ComposeAppShellDependencies, routePage: AppRoute, dark: Boolean) {
    key(routePage) {
        when (routePage) {
            AppRoute.Generate -> {
                val editorState by dependencies.viewModel.generateEditorState.collectAsStateWithLifecycle()
                val initialFormat = editorState.pendingFormat ?: editorState.formatName
                LaunchedEffect(routePage, initialFormat) {
                    dependencies.viewModel.clearPendingGenerateFormat()
                    dependencies.viewModel.updateGenerateFormat(initialFormat)
                }
                ComposeGeneratePage(
                    viewModel = dependencies.viewModel,
                    initialFormat = initialFormat,
                    dark = dark,
                    onCaptureText = dependencies.actions::captureText,
                    onNotice = dependencies.actions::notice,
                )
            }
            AppRoute.History -> {
                val dataState by dependencies.viewModel.dataState.collectAsStateWithLifecycle()
                val historyEntries = remember(dataState.items) {
                    dataState.items
                        .asSequence()
                        .filter { it.inHistory }
                        .map { it.copy() }
                        .groupBy { it.createdAt }
                        .toList()
                        .sortedByDescending { it.first }
                }
                HistoryComposePage(
                    entries = historyEntries,
                    dark = dark,
                    onClear = dependencies.actions::clearHistory,
                    onOpen = { batch -> dependencies.viewModel.openHistoryResult(batch) },
                    onEdit = dependencies.actions::editHistory,
                    onDelete = { batch ->
                        dependencies.actions.confirm("删除历史记录", "确定删除这条历史记录吗？", "删除") {
                            dependencies.viewModel.deleteHistoryBatch(batch)
                        }
                    },
                    timeText = ::formatHistoryTime,
                )
            }
            AppRoute.Favorites -> {
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                ComposeFavoritesPage(
                    viewModel = dependencies.viewModel,
                    dark = dark,
                    style = settings.style,
                    onClearAll = {
                        dependencies.actions.confirm(
                            "清空所有收藏",
                            "将删除全部收藏文件、文件夹层级和外部收藏文件，此操作不可恢复。",
                            "确定",
                        ) { dependencies.viewModel.clearFavoritesAndPersist() }
                    },
                    onShowSubfolderEditor = dependencies.actions::showSubfolderEditor,
                    onShowFolderEditor = dependencies.actions::showFolderEditor,
                    onShowMoveDialog = dependencies.actions::showMoveDialog,
                    onShowRenameDialog = dependencies.actions::showRenameDialog,
                    onConfirm = dependencies.actions::confirm,
                )
            }
            AppRoute.Results -> {
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                ComposeResultsPage(
                    viewModel = dependencies.viewModel,
                    settings = settings,
                    dark = dark,
                    onSaveFavorite = dependencies.actions::saveFavorite,
                    onShare = dependencies.actions::shareResult,
                )
            }
            AppRoute.Settings -> {
                ComposeSettingsPage(
                    settingsViewModel = dependencies.settingsViewModel,
                    dark = dark,
                    onApplyAppearance = dependencies.actions::applyAppearance,
                    onEnterLanShare = dependencies.actions::enterLanShare,
                    onRestoreFavorites = dependencies.actions::restoreFavorites,
                    onExportFavorites = dependencies.actions::exportFavorites,
                    onShareDebugLog = dependencies.actions::shareDebugLog,
                    onCheckForUpdates = dependencies.actions::checkForUpdates,
                    onNotice = dependencies.actions::notice,
                )
            }
            AppRoute.LanShare -> {
                ComposeLanSharePage(
                    viewModel = dependencies.lanShareViewModel,
                    dark = dark,
                    onOpenCamera = dependencies.actions::openLanShareCamera,
                    onOpenGallery = dependencies.actions::openLanShareGallery,
                    onOpenFiles = dependencies.actions::openLanShareFiles,
                    onSaveFile = dependencies.actions::saveLanShareFile,
                    onNotice = dependencies.actions::notice,
                    onCopyAddress = dependencies.actions::copyLanShareAddress,
                )
            }
        }
    }
}
