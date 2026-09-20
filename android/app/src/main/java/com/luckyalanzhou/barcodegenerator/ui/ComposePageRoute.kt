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
                ComposeFavoritesPage(
                    viewModel = dependencies.viewModel,
                    dark = dark,
                    onShowSubfolderEditor = dependencies.actions::showSubfolderEditor,
                    onShowFolderEditor = dependencies.actions::showFolderEditor,
                    onShowMoveDialog = dependencies.actions::showMoveDialog,
                    onShowRenameDialog = dependencies.actions::showRenameDialog,
                    onConfirm = dependencies.actions::confirm,
                )
            }
            AppRoute.FavoriteDetail -> {
                val resultState by dependencies.viewModel.resultUiState.collectAsStateWithLifecycle()
                val group = resultState.selectedFavoriteGroup
                if (group == null) {
                    LaunchedEffect(Unit) { dependencies.viewModel.navigateTo(AppRoute.Favorites) }
                } else {
                    val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                    ComposeFavoriteDetailPage(dependencies.viewModel, settings, dark, group)
                }
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
                    onFeatureSelfTest = dependencies.actions::featureSelfTest,
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
            AppRoute.BetaTestCenter -> {
                BetaTestCenterComposePage(
                    dark = dark,
                    entries = dependencies.betaTestEntries,
                    viewModel = dependencies.viewModel,
                    onNavigate = { pageName -> dependencies.viewModel.navigateTo(AppRoute.fromPage(pageName)) },
                    onShareDebugLog = dependencies.actions::shareDebugLog,
                )
            }
        }
    }
}
