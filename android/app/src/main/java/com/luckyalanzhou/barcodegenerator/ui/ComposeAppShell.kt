package com.luckyalanzhou.barcodegenerator

import com.luckyalanzhou.barcodegenerator.ui.AppRoute

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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalConfiguration

/** Compose 根层所需的状态和事件边界；Activity 只在入口处组装这些依赖。 */
internal data class ComposeAppShellDependencies(
    val viewModel: BarcodeViewModel,
    val settingsViewModel: SettingsViewModel,
    val lanShareViewModel: LanShareViewModel,
    val actions: ComposeAppShellActions,
    val betaTestEntries: List<BetaTestEntry>,
)

/** 创建唯一的 Compose 根节点；业务状态仍由 MainActivity/ViewModel 保存。 */
internal fun MainActivity.buildComposeShell() {
    val activity = this

    setContentView(
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ComposeAppShell(
                    dependencies = ComposeAppShellDependencies(
                        viewModel = activity.viewModel,
                        settingsViewModel = activity.settingsViewModel,
                        lanShareViewModel = activity.lanShareViewModel,
                        actions = activity.composeAppShellActions(),
                        betaTestEntries = activity.betaTestEntries(),
                    ),
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
    dependencies: ComposeAppShellDependencies,
) {
    val appUiState by dependencies.viewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by dependencies.viewModel.updateUiState.collectAsStateWithLifecycle()
    val fireworksVisible by dependencies.viewModel.fireworksVisible.collectAsStateWithLifecycle()
    val uiMode = LocalConfiguration.current.uiMode
    val dark = settingsUiState.style.colorScheme == "dark" ||
        (settingsUiState.style.colorScheme == "system" &&
            (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)
    val background = Color(if (dark) 0xff000000.toInt() else 0xfff2f2f7.toInt())
    val chromeVisible = AppRoute.fromPage(appUiState.page).chromeVisible
    val animation = rememberComposeAnimationConfig()

    LaunchedEffect(appUiState.page) {
        dependencies.actions.syncBarcodeDisplaySettings(appUiState.page == "results")
        if (appUiState.page == "lanShare" && dependencies.lanShareViewModel.uiState.value.session == null) {
            dependencies.actions.ensureLanShare()
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
        dependencies.actions.showUpdateDialog(updateUiState)
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
                        ComposeNavigationHost(dependencies, targetPage, dark)
                    }
                }
            }

            if (chromeVisible) {
                BarcodeComposeBottomTabBar(
                    selectedIndex = appUiState.selectedTab,
                    dark = dark,
                    onTabSelected = dependencies.actions::selectTab,
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

/** 页面路由渲染器；页面键由状态层保存，路由元数据由 UI 层解释。 */
@Composable
private fun ComposeNavigationHost(dependencies: ComposeAppShellDependencies, displayPage: String, dark: Boolean) {
    ComposePageRoute(dependencies, routeForPage(displayPage), dark)
}

@Composable
private fun ComposePageRoute(dependencies: ComposeAppShellDependencies, routePage: String, dark: Boolean) {
    key(routePage) {
        when (routePage) {
            "generate" -> {
                val initialFormat = remember(routePage) {
                    dependencies.viewModel.generateEditorState.value.pendingFormat
                        ?: dependencies.viewModel.generateEditorState.value.formatName
                }
                LaunchedEffect(routePage) {
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
            "history" -> {
                val dataState by dependencies.viewModel.dataState.collectAsStateWithLifecycle()
                val historyEntries = dataState.items
                    .filter { it.inHistory }
                    .map { it.copy() }
                    .groupBy { it.createdAt }
                    .toList()
                    .sortedByDescending { it.first }
                HistoryComposePage(
                    entries = historyEntries,
                    dark = dark,
                    onClear = dependencies.actions::clearHistory,
                    onOpen = { batch ->
                        dependencies.viewModel.openHistoryResult(batch)
                    },
                    onEdit = dependencies.actions::editHistory,
                    onDelete = { batch ->
                        dependencies.viewModel.deleteHistoryBatch(batch)
                    },
                    timeText = ::formatHistoryTime,
                )
            }
            "favorites" -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
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
            }
            "favoriteDetail" -> {
                val resultState by dependencies.viewModel.resultUiState.collectAsStateWithLifecycle()
                val group = resultState.selectedFavoriteGroup
                if (group == null) {
                    LaunchedEffect(Unit) {
                        dependencies.viewModel.navigateTo("favorites")
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                        ComposeFavoriteDetailPage(dependencies.viewModel, settings, dark, group)
                    }
                }
            }
            "results" -> {
                val settings by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
                ComposeResultsPage(
                    viewModel = dependencies.viewModel,
                    settings = settings,
                    dark = dark,
                    onSaveFavorite = dependencies.actions::saveFavorite,
                    onShare = dependencies.actions::shareResult,
                )
            }
            "settings" -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                ) {
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
            }
            "lanShare" -> {
                // 文件传输页自行管理消息区滚动，输入卡片固定在系统导航栏上方。
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
            // Beta 测试中心也直接作为 Compose 内容路由，不再嵌套旧 AndroidView。
            "betaTestCenter" -> {
                BetaTestCenterComposePage(dark, dependencies.betaTestEntries, dependencies.actions::shareDebugLog)
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

