package com.luckyalanzhou.barcodegenerator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalConfiguration

/** 创建唯一的 Compose 根节点；业务状态仍由 MainActivity/ViewModel 保存。 */
internal fun MainActivity.buildComposeShell() {
    val activity = this

    setContentView(
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ComposeAppShell(
                    activity = activity,
                    onTabSelected = { index ->
                        if (activity.viewModel.uiState.value.page == "lanShare") activity.closeLanShare()
                        activity.viewModel.selectMainTab(index)
                    },
                    onSyncBarcodeDisplaySettings = activity::syncBarcodeDisplaySettings,
                    onEnsureLanShare = activity::enterLanShare,
                    onShowUpdateDialog = { update ->
                        val latest = update.availableVersion
                        val downloadUrl = update.availableUrl
                        if (latest != null && downloadUrl != null) {
                            activity.showUpdateAvailableDialogCompose(
                                latest = latest,
                                downloadUrl = downloadUrl,
                                expectedSize = update.expectedSize,
                                expectedSha256 = update.sha256,
                            )
                        }
                    },
                )
            }
        }
    )
    composeShellReady = true
}

/**
 * 应用根壳层。
 *
 * 标题、系统栏边距、页面内容和底部导航均由 Compose 统一管理。
 */
@Composable
internal fun ComposeAppShell(
    activity: MainActivity,
    onTabSelected: (Int) -> Unit,
    onSyncBarcodeDisplaySettings: (Boolean) -> Unit,
    onEnsureLanShare: () -> Unit,
    onShowUpdateDialog: (UpdateUiState) -> Unit,
) {
    val appUiState by activity.viewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by activity.settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by activity.viewModel.updateUiState.collectAsStateWithLifecycle()
    val fireworksVisible by activity.viewModel.fireworksVisible.collectAsStateWithLifecycle()
    val uiMode = LocalConfiguration.current.uiMode
    val dark = settingsUiState.style.colorScheme == "dark" ||
        (settingsUiState.style.colorScheme == "system" &&
            (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)
    val background = Color(if (dark) 0xff000000.toInt() else 0xfff2f2f7.toInt())
    val chromeVisible = appUiState.chromeVisible
    val animation = rememberComposeAnimationConfig()

    LaunchedEffect(appUiState.page) {
        onSyncBarcodeDisplaySettings(appUiState.page == "results")
        if (appUiState.page == "lanShare" && activity.lanShareViewModel.uiState.value.session == null) {
            onEnsureLanShare()
        }
    }

    LaunchedEffect(
        updateUiState.dialogShowing,
        updateUiState.availableVersion,
        updateUiState.availableUrl,
    ) {
        if (updateUiState.dialogShowing &&
            updateUiState.availableVersion != null &&
            updateUiState.availableUrl != null
        ) {
            onShowUpdateDialog(updateUiState)
        }
    }

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = Color(0xffb8ccff),
            onPrimary = Color(0xff10224a),
            secondary = Color(0xffb8ccff),
            tertiary = Color(0xffb8ccff),
            background = background,
            surface = Color(0xff1c1c1e),
        )
    } else {
        lightColorScheme(
            primary = Color(0xff2864d7),
            onPrimary = Color.White,
            secondary = Color(0xff2864d7),
            tertiary = Color(0xff2864d7),
            background = background,
            surface = Color(0xfffbfcff),
        )
    }
    MaterialTheme(colorScheme = colorScheme) {
        Box(Modifier.fillMaxSize().background(background)) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
            ) {
            AnimatedContent(
                targetState = appUiState.page,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = animation.settleSpring(),
                        initialOffsetY = { it / 10 },
                    ) + fadeIn(tween(animation.pageFadeInDurationMillis))) togetherWith
                        (slideOutVertically(
                            animationSpec = animation.settleSpring(),
                            targetOffsetY = { -it / 14 },
                        ) + fadeOut(tween(animation.pageFadeOutDurationMillis))) using
                        SizeTransform(clip = false)
                },
                label = "pageUpTransition",
            ) { targetPage ->
                val targetRoute = AppRoute.fromPage(targetPage)
                val targetChromeVisible = targetRoute.chromeVisible
                Column(Modifier.fillMaxSize()) {
                    if (targetChromeVisible) {
                        Text(
                            text = targetRoute.title,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            color = if (dark) Color(0xfff2f4f8) else Color(0xff182230),
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        ComposeNavigationHost(activity, targetPage, dark)
                    }
                }
            }

            if (chromeVisible) {
                BarcodeComposeBottomTabBar(
                    selectedIndex = appUiState.selectedTab,
                    dark = dark,
                    onTabSelected = onTabSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                )
            }
        }
            if (fireworksVisible) {
                ComposeFireworksOverlay()
            }
        }
    }
}

private fun routeForPage(page: String): String = AppRoute.fromPage(page).pageName

/** Navigation Compose 容器；页面业务仍由现有兼容层提供，逐步迁移期间保持返回目标不变。 */
@Composable
private fun ComposeNavigationHost(activity: MainActivity, displayPage: String, dark: Boolean) {
    val initialRoute = remember(displayPage) { routeForPage(displayPage) }
    val navController = rememberNavController()
    val targetRoute = routeForPage(displayPage)

    LaunchedEffect(targetRoute) {
        if (navController.currentDestination?.route != targetRoute) {
            navController.navigate(targetRoute) {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = initialRoute) {
        composable("generate") { ComposePageRoute(activity, "generate", dark) }
        composable("history") { ComposePageRoute(activity, "history", dark) }
        composable("favorites") { ComposePageRoute(activity, "favorites", dark) }
        composable("favoriteDetail") { ComposePageRoute(activity, "favoriteDetail", dark) }
        composable("results") { ComposePageRoute(activity, "results", dark) }
        composable("settings") { ComposePageRoute(activity, "settings", dark) }
        composable("lanShare") { ComposePageRoute(activity, "lanShare", dark) }
        composable("betaTestCenter") { ComposePageRoute(activity, "betaTestCenter", dark) }
    }
}

@Composable
private fun ComposePageRoute(activity: MainActivity, routePage: String, dark: Boolean) {
    key(routePage) {
        when (routePage) {
            "generate" -> {
                val initialFormat = remember(routePage) {
                    activity.viewModel.generateEditorState.value.pendingFormat
                        ?: activity.viewModel.generateEditorState.value.formatName
                }
                LaunchedEffect(routePage) {
                    activity.viewModel.clearPendingGenerateFormat()
                    activity.viewModel.updateGenerateFormat(initialFormat)
                }
                ComposeGeneratePage(
                    viewModel = activity.viewModel,
                    initialFormat = initialFormat,
                    dark = dark,
                    onCaptureText = activity::captureText,
                    onNotice = activity::toast,
                )
            }
            "history" -> {
                val dataState by activity.viewModel.dataState.collectAsStateWithLifecycle()
                val historyEntries = dataState.items
                    .filter { it.inHistory }
                    .map { it.copy() }
                    .groupBy { it.createdAt }
                    .toList()
                    .sortedByDescending { it.first }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    HistoryComposePage(
                        entries = historyEntries,
                        dark = dark,
                        onClear = { activity.confirmClearCompose(false) },
                        onOpen = { batch ->
                            activity.viewModel.openHistoryResult(batch)
                        },
                        onEdit = { batch ->
                            if (batch.size == 1) activity.showItemEditorCompose(batch.first())
                            else activity.showHistoryBatchPickerCompose(batch)
                        },
                        onDelete = { batch ->
                            activity.viewModel.deleteHistoryBatch(batch)
                        },
                        timeText = ::formatHistoryTime,
                    )
                }
            }
            "favorites" -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    ComposeFavoritesPage(
                        viewModel = activity.viewModel,
                        dark = dark,
                        onShowSubfolderEditor = activity::showSubfolderEditorCompose,
                        onShowFolderEditor = { initial, onSaved -> activity.showFolderEditorCompose(initial, onSaved = onSaved) },
                        onShowMoveDialog = activity::showFavoriteMoveDialogCompose,
                        onShowRenameDialog = activity::showFavoriteRenameDialogCompose,
                        onConfirm = { title, message, positive, onConfirm ->
                            activity.showComposeConfirmDialog(title, message, positive, onConfirm)
                        },
                    )
                }
            }
            "favoriteDetail" -> {
                val resultState by activity.viewModel.resultUiState.collectAsStateWithLifecycle()
                val group = resultState.selectedFavoriteGroup
                if (group == null) {
                    LaunchedEffect(Unit) {
                        activity.viewModel.navigateTo("favorites")
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        val settings by activity.settingsViewModel.uiState.collectAsStateWithLifecycle()
                        ComposeFavoriteDetailPage(activity.viewModel, settings, dark, group)
                    }
                }
            }
            "results" -> {
                val settings by activity.settingsViewModel.uiState.collectAsStateWithLifecycle()
                ComposeResultsPage(
                    viewModel = activity.viewModel,
                    settings = settings,
                    dark = dark,
                    onSaveFavorite = activity::saveResultAsFavoriteCompose,
                    onShare = activity::shareResultPage,
                )
            }
            "settings" -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    ComposeSettingsPage(
                        settingsViewModel = activity.settingsViewModel,
                        dark = dark,
                        onApplyAppearance = activity::applyAppearance,
                        onEnterLanShare = activity::enterLanShare,
                        onRestoreFavorites = activity::restoreFavoritesImport,
                        onExportFavorites = activity::createFavoritesExportCompose,
                        onFeatureSelfTest = activity::showFeatureSelfTestDialog,
                        onCheckForUpdates = { activity.checkForUpdates(silent = false) },
                        onNotice = activity::toast,
                    )
                }
            }
            "lanShare" -> {
                // 文件传输页自行管理消息区滚动，输入卡片固定在系统导航栏上方。
                ComposeLanSharePage(
                    viewModel = activity.lanShareViewModel,
                    dark = dark,
                    onOpenCamera = activity::openLanShareCamera,
                    onOpenGallery = activity::openLanShareGallery,
                    onOpenFiles = activity::openLanShareFiles,
                    onSaveFile = activity::saveLanShareFile,
                    onNotice = activity::toast,
                    onCopyAddress = { address ->
                        (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .setPrimaryClip(android.content.ClipData.newPlainText("局域网传输地址", address))
                        activity.toast("已复制局域网传输地址")
                    },
                )
            }
            // Beta 测试中心也直接作为 Compose 内容路由，不再嵌套旧 AndroidView。
            "betaTestCenter" -> {
                BetaTestCenterComposePage(dark, activity.betaTestEntries(), activity::shareDebugLog)
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

