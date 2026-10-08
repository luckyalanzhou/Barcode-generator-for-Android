package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.canonicalBarcodeFormatName
import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.ui.dialogs.*
import com.luckyalanzhou.barcodegenerator.ui.feature.results.*
import com.luckyalanzhou.barcodegenerator.ui.feature.settings.*
import com.luckyalanzhou.barcodegenerator.ui.feature.generate.*
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.*
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.content.*
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesTreeState
import com.luckyalanzhou.barcodegenerator.ui.feature.history.*
import com.luckyalanzhou.barcodegenerator.ui.feature.lanshare.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrEvent
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareEvent
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import android.os.Handler
import android.os.Looper
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
                val barcodeData by dependencies.favoritesViewModel.dataState.collectAsStateWithLifecycle()
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                val isPreparingResult by dependencies.resultsViewModel.isPreparingResult.collectAsStateWithLifecycle()
                val density = LocalDensity.current.density
                val initialFormat = canonicalBarcodeFormatName(editorState.pendingFormat ?: editorState.formatName)
                LaunchedEffect(routePage, initialFormat) {
                    dependencies.generateViewModel.clearPendingFormat()
                    dependencies.generateViewModel.updateFormat(initialFormat)
                }
                LaunchedEffect(dependencies.cameraOcrViewModel) {
                    dependencies.cameraOcrViewModel.events.collect { event ->
                        when (event) {
                            is CameraOcrEvent.RecognizedText -> dependencies.generateViewModel.updateDraft(event.lines)
                            is CameraOcrEvent.Notice -> dependencies.actions.notice(event.message)
                        }
                    }
                }
                GenerateContent(
                    inputDraft = editorState.inputDraft,
                    initialFormat = initialFormat,
                    dark = dark,
                    isPreparingResult = isPreparingResult,
                    isDataReady = barcodeData.isReady,
                    onDraftChanged = dependencies.generateViewModel::updateDraft,
                    onFormatChanged = dependencies.generateViewModel::updateFormat,
                    onGenerate = { values, format ->
                        val result = dependencies.generateViewModel.generate(values, format, barcodeData.items)
                        if (!result.isValid) {
                            val message = result.errorMessage
                            dependencies.actions.notice(
                                if (message == "请输入内容") message else "第 ${result.errorIndex + 1} 行：$message",
                            )
                        } else {
                            dependencies.resultsViewModel.commitGeneratedBarcodes(
                                items = result.items,
                                style = settings.style,
                                dark = dark,
                                density = density,
                                onNotice = dependencies.actions::notice,
                                onNavigateToResults = { dependencies.actions.navigateTo(AppRoute.Results) },
                            )
                        }
                    },
                    onCaptureText = dependencies.actions::captureText,
                    onNotice = dependencies.actions::notice,
                )
            }
            AppRoute.History -> {
                val historyState by dependencies.historyViewModel.dataState.collectAsStateWithLifecycle()
                val refreshGeneration by dependencies.historyViewModel.refreshGeneration.collectAsStateWithLifecycle()
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                val density = LocalDensity.current.density
                HistoryScreen(
                    items = historyState.items,
                    refreshGeneration = refreshGeneration,
                    dark = dark,
                    onOpen = { batch ->
                        dependencies.resultsViewModel.showHistoryResult(
                            batch = batch,
                            style = settings.style,
                            dark = dark,
                            density = density,
                            onNotice = dependencies.actions::notice,
                            onNavigateToResults = { dependencies.actions.navigateTo(AppRoute.Results) },
                        )
                    },
                    onEdit = dependencies.actions::editHistory,
                    onDelete = { batch ->
                        dependencies.actions.confirm("删除历史记录", "确定删除这条历史记录吗？", "删除") {
                            dependencies.historyViewModel.deleteHistoryBatch(batch)
                        }
                    },
                )
            }
            AppRoute.Favorites -> {
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                val favoriteData by dependencies.favoritesViewModel.dataState.collectAsStateWithLifecycle()
                val favoriteSearch by dependencies.favoritesViewModel.searchState.collectAsStateWithLifecycle()
                val favoriteSearchStatus by dependencies.favoritesViewModel.searchStatus.collectAsStateWithLifecycle()
                val favoriteQuery by dependencies.favoritesViewModel.query.collectAsStateWithLifecycle()
                val favoriteTree by dependencies.favoritesViewModel.treeState.collectAsStateWithLifecycle()
                FavoritesContent(
                    state = FavoritesContentState(
                        favorites = FavoritesListContentState(
                            items = favoriteData.items,
                            groups = favoriteData.groups,
                            folders = favoriteData.folders,
                            isReady = favoriteData.isReady,
                        ),
                        searchResults = FavoritesListContentState(
                            items = favoriteSearch.items,
                            groups = favoriteSearch.groups,
                            folders = favoriteSearch.folders,
                            isReady = favoriteSearch.isReady,
                        ),
                        search = FavoritesSearchContentState(
                            query = favoriteSearchStatus.query,
                            busy = favoriteSearchStatus.busy,
                            failed = favoriteSearchStatus.failed,
                        ),
                        tree = FavoritesTreeState(
                            collapsedFolders = favoriteTree.collapsedFolders,
                            knownFolders = favoriteTree.knownFolders,
                            searchAutoExpandSuppressed = favoriteTree.searchAutoExpandSuppressed,
                        ),
                        query = favoriteQuery,
                        savedListPosition = dependencies.favoritesViewModel.position(),
                    ),
                    scrollToTopEvents = dependencies.favoritesViewModel.scrollToTopEvents,
                    dark = dark,
                    style = settings.style,
                    onQueryChange = dependencies.favoritesViewModel::updateQuery,
                    onSearchFavoriteContent = dependencies.favoritesViewModel::searchFavoriteContent,
                    onSyncFavoriteTree = dependencies.favoritesViewModel::syncTree,
                    onUpdateFavoriteSearch = dependencies.favoritesViewModel::updateSearch,
                    onRememberListPosition = dependencies.favoritesViewModel::rememberPosition,
                    onLoadMoreGroups = dependencies.favoritesViewModel::loadMoreFavoriteGroups,
                    onToggleFolder = dependencies.favoritesViewModel::toggleFolder,
                    onOpenGroup = { group, style, isDark, density ->
                        dependencies.resultsViewModel.cancelFavoriteGroupRendering()
                        dependencies.favoritesViewModel.loadFavoriteGroupContent(
                            group = group,
                            onLoaded = { content ->
                                dependencies.resultsViewModel.openFavoriteGroup(
                                    content = content,
                                    style = style,
                                    dark = isDark,
                                    density = density,
                                    isCurrent = dependencies.favoritesViewModel::isFavoriteGroupCurrent,
                                    onNavigateToResults = { dependencies.actions.navigateTo(AppRoute.Results) },
                                    onNotice = dependencies.actions::notice,
                                )
                            },
                            onNotice = dependencies.actions::notice,
                        )
                    },
                    onRenameFolder = dependencies.favoritesViewModel::renameFavoriteFolder,
                    onDeleteFolder = dependencies.favoritesViewModel::deleteFavoriteFolder,
                    onDeleteGroup = { dependencies.favoritesViewModel.deleteFavoriteGroup(it.id) },
                    onClearAll = {
                        dependencies.actions.confirm(
                            "清空所有收藏",
                            "将清空应用内收藏和文件夹层级。此操作无法撤销。",
                            "确定",
                        ) { dependencies.favoritesViewModel.clearFavorites() }
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
                val resultState by dependencies.resultsViewModel.resultUiState.collectAsStateWithLifecycle()
                ResultsContent(
                    exportAction = dependencies.actions.resultExportAction,
                    state = ResultsContentState(
                        items = resultState.items,
                        isRestoring = resultState.isRestoring,
                        restoreFailed = resultState.restoreFailed,
                        hasSavedFavoriteFile = resultState.hasSavedFavoriteFile,
                    ),
                    style = settings.style,
                    dark = dark,
                    onEdit = {
                        DebugLog.actionStarted("result_edit", "count=${resultState.items.size}")
                        dependencies.resultsViewModel.editCurrentResult {
                            // Finish the toolbar click before replacing the root page.
                            // The result toolbar is inside the composition being removed;
                            // posting avoids a re-entrant composition transition.
                            Handler(Looper.getMainLooper()).post {
                                DebugLog.actionSucceeded("result_edit", "to=Generate")
                                dependencies.actions.navigateTo(AppRoute.Generate)
                            }
                        }
                    },
                    onSaveFavorite = dependencies.actions::saveFavorite,
                    onShare = dependencies.actions::shareResult,
                    onSave = dependencies.actions::saveResult,
                    onImageWidthChanged = dependencies.actions::updateResultImageWidth,
                    loadBarcodeImage = { item, isDark, density ->
                        dependencies.resultsViewModel.loadOrCreateBarcodeImage(item, settings.style, isDark, density)
                    },
                )
            }
            AppRoute.Settings -> {
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                val update by dependencies.updateViewModel.uiState.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                SettingsContent(
                    checkingForUpdates = update.checking,
                    settings = SettingsContentState(style = settings.style, ocrMask = settings.ocrMask),
                    dark = dark,
                    onStyleChange = { next ->
                        val viewModel = dependencies.settingsViewModel
                        val currentStyle = viewModel.style
                        val schemeChanged = currentStyle.colorScheme != next.colorScheme
                        viewModel.updateStyle(next)
                        val saveJob = viewModel.save()
                        if (schemeChanged) scope.launch {
                            saveJob.join()
                            dependencies.actions.applyAppearance()
                        }
                    },
                    onOcrMaskChange = { dependencies.settingsViewModel.setOcrMaskPersisted(it) },
                    onEnterLanShare = dependencies.actions::enterLanShare,
                    onShareDebugLog = dependencies.actions::shareDebugLog,
                    onCheckForUpdates = dependencies.actions::checkForUpdates,
                    onNotice = dependencies.actions::notice,
                )
            }
            AppRoute.LanShare -> {
                val viewModel = dependencies.lanShareViewModel
                val lanState by viewModel.uiState.collectAsStateWithLifecycle()
                val lanContentState = remember(lanState) {
                    LanShareContentState(
                        session = lanState.session,
                        isHost = lanState.isHost,
                        qrVisible = lanState.qrVisible,
                        browserConnected = lanState.browserConnected,
                        files = lanState.files,
                        uploadingFiles = lanState.uploadingFiles.map { upload ->
                            LanShareUploadingContent(
                                id = upload.id,
                                name = upload.name,
                                size = upload.size,
                                uploadedBytes = upload.uploadedBytes,
                                startedAt = upload.startedAt,
                            )
                        },
                        messages = lanState.messages,
                        ownFileIds = lanState.ownFileIds,
                        previewFileIds = lanState.previewFileIds,
                        pendingUploadName = lanState.pendingUploadName,
                    )
                }
                var clearInputGeneration by remember { mutableIntStateOf(0) }
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is LanShareEvent.Error -> dependencies.actions.notice(event.message)
                            is LanShareEvent.Notice -> {
                                if (event.clearInput) clearInputGeneration += 1
                                dependencies.actions.notice(event.message)
                            }
                        }
                    }
                }
                LanShareScreen(
                    lanState = lanContentState,
                    dark = dark,
                    clearInputGeneration = clearInputGeneration,
                    onOpenCamera = dependencies.actions::openLanShareCamera,
                    onOpenGallery = dependencies.actions::openLanShareGallery,
                    onOpenFiles = dependencies.actions::openLanShareFiles,
                    onSaveFile = dependencies.actions::saveLanShareFile,
                    onCopyAddress = dependencies.actions::copyLanShareAddress,
                    createQrBitmap = dependencies.lanShareQrBitmapFactory::create,
                    onSetQrVisible = viewModel::setQrVisible,
                    onCancelUpload = viewModel::cancelUpload,
                    onLoadImagePreview = viewModel::loadImagePreview,
                    onLoadFullImagePreview = viewModel::loadFullImagePreview,
                    onSend = { text ->
                        val state = viewModel.uiState.value
                        if (state.pendingUploadUri != null) {
                            viewModel.takePendingUpload()?.let { (uri, temporaryFile) ->
                                state.session?.let { session -> viewModel.uploadFile(session, uri, temporaryFile) }
                            }
                        } else text.takeIf { it.isNotBlank() }?.let { value ->
                            state.session?.let { session -> viewModel.sendText(session, value) }
                        }
                    },
                )
            }
        }
    }
}
