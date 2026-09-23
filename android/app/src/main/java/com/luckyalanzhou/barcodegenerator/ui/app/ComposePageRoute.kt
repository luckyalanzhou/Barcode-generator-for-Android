package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.feature.results.*
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.*
import com.luckyalanzhou.barcodegenerator.ui.feature.generate.*
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.*
import com.luckyalanzhou.barcodegenerator.ui.feature.history.*
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckyalanzhou.barcodegenerator.presentation.BarcodeEvent
import kotlinx.coroutines.launch

/** 页面渲染器；页面键由状态层保存，路由元数据由 UI 层解释。 */
@Composable
internal fun ComposePageRenderer(dependencies: ComposeAppShellDependencies, displayPage: AppRoute, dark: Boolean) {
    ComposePageRoute(dependencies, displayPage, dark)
}

@Composable
private fun ComposePageRoute(dependencies: ComposeAppShellDependencies, routePage: AppRoute, dark: Boolean) {
    key(routePage) {
        when (routePage) {
            AppRoute.Generate -> {
                val editorState by dependencies.generateViewModel.uiState.collectAsStateWithLifecycle()
                val initialFormat = editorState.pendingFormat ?: editorState.formatName
                LaunchedEffect(routePage, initialFormat) {
                    dependencies.generateViewModel.clearPendingFormat()
                    dependencies.generateViewModel.updateFormat(initialFormat)
                }
                LaunchedEffect(dependencies.viewModel) {
                    dependencies.viewModel.events.collect { event ->
                        when (event) {
                            is BarcodeEvent.RecognizedText -> dependencies.generateViewModel.updateDraft(event.lines)
                            is BarcodeEvent.Notice -> dependencies.actions.notice(event.message)
                        }
                    }
                }
                GenerateContent(
                    editorState = editorState,
                    initialFormat = initialFormat,
                    dark = dark,
                    onDraftChanged = dependencies.generateViewModel::updateDraft,
                    onFormatChanged = dependencies.generateViewModel::updateFormat,
                    onGenerate = { values, format ->
                        dependencies.generateViewModel.updateDraft(values)
                        dependencies.generateViewModel.updateFormat(format)
                        val result = dependencies.viewModel.generateBarcodes(format)
                        if (!result.isValid) {
                            val message = result.errorMessage
                            dependencies.actions.notice(
                                if (message == "请输入内容") message else "第 ${result.errorIndex + 1} 行：$message",
                            )
                        }
                    },
                    onCaptureText = dependencies.actions::captureText,
                    onNotice = dependencies.actions::notice,
                )
            }
            AppRoute.History -> {
                val historyState by dependencies.viewModel.dataState.collectAsStateWithLifecycle()
                HistoryScreen(
                    dataState = historyState,
                    dark = dark,
                    onClear = dependencies.actions::clearHistory,
                    onOpen = dependencies.viewModel::openHistoryResult,
                    onEdit = dependencies.actions::editHistory,
                    onDelete = { batch ->
                        dependencies.actions.confirm("删除历史记录", "确定删除这条历史记录吗？", "删除") {
                            dependencies.viewModel.deleteHistoryBatch(batch)
                        }
                    },
                )
            }
            AppRoute.Favorites -> {
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                val favoriteData by dependencies.viewModel.dataState.collectAsStateWithLifecycle()
                val favoriteSearch by dependencies.viewModel.favoriteSearchState.collectAsStateWithLifecycle()
                val favoriteTree by dependencies.viewModel.favoriteTreeUiState.collectAsStateWithLifecycle()
                val favoriteQuery by dependencies.viewModel.favoritePageQuery.collectAsStateWithLifecycle()
                FavoritesContent(
                    favoritesState = favoriteData,
                    searchState = favoriteSearch,
                    treeState = favoriteTree,
                    query = favoriteQuery,
                    savedListPosition = dependencies.viewModel.favoriteListPosition(),
                    dark = dark,
                    style = settings.style,
                    onQueryChange = dependencies.viewModel::updateFavoritePageQuery,
                    onSyncFavoriteTree = dependencies.viewModel::syncFavoriteTree,
                    onSearchFavoriteContent = dependencies.viewModel::searchFavoriteContent,
                    onUpdateFavoriteSearch = dependencies.viewModel::updateFavoriteSearch,
                    onRememberListPosition = dependencies.viewModel::rememberFavoriteListPosition,
                    onLoadMoreGroups = dependencies.viewModel::loadMoreFavoriteGroups,
                    onToggleFolder = dependencies.viewModel::toggleFavoriteFolder,
                    onOpenGroup = dependencies.viewModel::openFavoriteGroup,
                    onRenameFolder = dependencies.viewModel::renameFavoriteFolderAndPersist,
                    onDeleteFolder = dependencies.viewModel::deleteFavoriteFolderAndPersist,
                    onDeleteGroup = { dependencies.viewModel.deleteFavoriteGroupAndPersist(it.id) },
                    onClearAll = {
                        dependencies.actions.confirm(
                            "清空所有收藏",
                            "将清空应用内收藏和文件夹层级。此操作无法撤销。",
                            "确定",
                        ) { dependencies.viewModel.clearFavoritesAndPersist() }
                    },
                    onShowSubfolderEditor = dependencies.actions::showSubfolderEditor,
                    onShowFolderEditor = dependencies.actions::showFolderEditor,
                    onEdit = dependencies.actions::editFavorite,
                    onShowMoveDialog = dependencies.actions::showMoveDialog,
                    onShowRenameDialog = dependencies.actions::showRenameDialog,
                    onConfirm = dependencies.actions::confirm,
                )
            }
            AppRoute.Results -> {
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                val resultState by dependencies.viewModel.resultUiState.collectAsStateWithLifecycle()
                ResultsContent(
                    resultState = resultState,
                    settings = settings,
                    dark = dark,
                    onEdit = dependencies.viewModel::editCurrentResult,
                    onSaveFavorite = dependencies.actions::saveFavorite,
                    onShare = dependencies.actions::shareResult,
                    loadBarcodeImage = { item, isDark, density ->
                        dependencies.viewModel.loadOrCreateBarcodeImage(item, settings.style, isDark, density)
                    },
                )
            }
            AppRoute.Settings -> {
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                SettingsContent(
                    settings = settings,
                    dark = dark,
                    onPersist = { next ->
                        val viewModel = dependencies.settingsViewModel
                        val currentStyle = viewModel.style
                        val schemeChanged = currentStyle.colorScheme != next.scheme
                        viewModel.updateStyle(
                            currentStyle.copy(
                                textSize = next.textSize,
                                barHeight = next.barHeight.toInt(),
                                barWidth = next.barWidth,
                                margin = next.margin.toInt(),
                                showFormat = next.showFormat,
                                colorScheme = next.scheme,
                            ),
                        )
                        val saveJob = viewModel.save()
                        if (schemeChanged) scope.launch {
                            saveJob.join()
                            dependencies.actions.applyAppearance()
                        }
                    },
                    onOcrMaskChange = { dependencies.settingsViewModel.setOcrMaskPersisted(it) },
                    onEnterLanShare = dependencies.actions::enterLanShare,
                    onRestoreFavorites = dependencies.actions::restoreFavorites,
                    onExportFavorites = dependencies.actions::exportFavorites,
                    onShareDebugLog = dependencies.actions::shareDebugLog,
                    onCheckForUpdates = dependencies.actions::checkForUpdates,
                    onNotice = dependencies.actions::notice,
                )
            }
            AppRoute.LanShare -> {
                LanShareScreen(
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
