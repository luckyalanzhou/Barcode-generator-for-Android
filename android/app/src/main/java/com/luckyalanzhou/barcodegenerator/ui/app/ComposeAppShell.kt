package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.presentation.update.UpdateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesViewModel
import com.luckyalanzhou.barcodegenerator.presentation.results.ResultsViewModel
import com.luckyalanzhou.barcodegenerator.presentation.navigation.AppNavigationViewModel
import com.luckyalanzhou.barcodegenerator.presentation.history.HistoryViewModel
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataViewModel
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareQrBitmapFactory
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrViewModel
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrEvent
import com.luckyalanzhou.barcodegenerator.presentation.generate.OcrDraftResult
import com.luckyalanzhou.barcodegenerator.domain.MAX_GENERATE_INPUT_ROWS
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.animation.rememberContextMenuMotion
import com.luckyalanzhou.barcodegenerator.ui.component.glass.*
import com.luckyalanzhou.barcodegenerator.ui.component.menu.LocalLongPressMenuHost
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.tree.FavoritesRowsProjectionCache
import com.luckyalanzhou.barcodegenerator.ui.feature.favorites.FavoritesManagementMenu

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import com.luckyalanzhou.barcodegenerator.ui.component.ContextMenuGestureSession
import com.luckyalanzhou.barcodegenerator.ui.component.contextMenuGestures

internal data class ComposeAppShellDependencies(
    val navigationViewModel: AppNavigationViewModel,
    val cameraOcrViewModel: CameraOcrViewModel,
    val generateViewModel: GenerateViewModel,
    val settingsViewModel: SettingsViewModel,
    val lanShareViewModel: () -> LanShareViewModel,
    val lanShareQrBitmapFactory: LanShareQrBitmapFactory,
    val updateViewModel: UpdateViewModel,
    val favoritesViewModel: FavoritesViewModel,
    val historyViewModel: () -> HistoryViewModel,
    val resultsViewModel: ResultsViewModel,
    val libraryDataViewModel: LibraryDataViewModel,
    val actions: ComposeAppShellActions,
)

internal fun MainActivity.buildComposeShell() {
    val activity = this
    setContentView(
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ComposeAppShell(
                    dependencies = ComposeAppShellDependencies(
                        navigationViewModel = activity.navigationViewModel,
                        cameraOcrViewModel = activity.cameraOcrViewModel,
                        generateViewModel = activity.generateViewModel,
                        settingsViewModel = activity.settingsViewModel,
                        lanShareViewModel = { activity.lanShareViewModel },
                        lanShareQrBitmapFactory = activity.lanShareQrBitmapFactory,
                        updateViewModel = activity.updateViewModel,
                        favoritesViewModel = activity.favoritesViewModel,
                        historyViewModel = { activity.historyViewModel },
                        resultsViewModel = activity.resultsViewModel,
                        libraryDataViewModel = activity.libraryDataViewModel,
                        actions = activity.composeAppShellActions(),
                    ),
                )
            }
        },
    )
    composeShellReady = true
}

/**
 * 应用主界面外壳：以导航状态作为页面唯一来源，统一协调页面切换、系统返回、Tab 栏和长按菜单。
 * 页面可保存自身滚动状态；网络、持久化和系统选择器等副作用通过依赖回调交给上层处理。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ComposeAppShell(dependencies: ComposeAppShellDependencies) {
    val localizedResources = LocalResources.current
    val appUiState by dependencies.navigationViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by dependencies.updateViewModel.uiState.collectAsStateWithLifecycle()
    val currentRoute = appUiState.page
    val pageTransition = updateTransition(currentRoute, label = "pageTransition")
    val chromeVisible = currentRoute.chromeVisible
    val bottomTabVisible = chromeVisible || currentRoute == AppRoute.AppearanceTheme
    val pageStateHolder = rememberSaveableStateHolder()
    val favoritesRowsProjectionCache = remember { FavoritesRowsProjectionCache() }
    val effects = rememberVisualEffectsPolicy(
        liquidGlassEnabled = settingsUiState.liquidGlassEnabled,
    )
    var focusToRestore by remember { mutableStateOf<(() -> Unit)?>(null) }
    val tabMenu = remember { TabMenuPresentation<TabLongPressMenuState> { focusToRestore = it.restoreFocus } }
    val menuGesture = remember { ContextMenuGestureSession() }
    // 相机识别属于跨页面动作；常驻监听可在生成 Tab 未选中时也更新生成草稿，不触发导航。
    CameraOcrEventCollector(
        events = dependencies.cameraOcrViewModel.events,
        onRecognizedText = { lines, expectedDraft ->
            dependencies.generateViewModel.applyOcrResultIfDraftUnchanged(expectedDraft, lines)
        },
        onNavigateToGenerate = {
            if (dependencies.navigationViewModel.uiState.value.page != AppRoute.Generate) {
                dependencies.actions.selectTab(0, fromSwipe = false)
            }
        },
        onNotice = dependencies.actions::notice,
    )
    fun showMenu(state: TabLongPressMenuState) {
        if (state.anchorBoundsOnScreen.width <= 0f || state.anchorBoundsOnScreen.height <= 0f || state.actions.isEmpty()) return
        menuGesture.open()
        menuGesture.sourceBounds = state.anchorBoundsOnScreen
        tabMenu.show(state)
    }
    LaunchedEffect(tabMenu.menu) {
        if (tabMenu.menu == null) {
            val restore = focusToRestore
            focusToRestore = null
            restore?.invoke()
        }
    }
    val menuMotion = rememberContextMenuMotion(tabMenu.menu, tabMenu.open, tabMenu.ready,
        tabMenu.actionClosing, effects.reduceMotion) { showing ->
        if (tabMenu.menu === showing) tabMenu.closed()
    }
    val tabMenuProgress = menuMotion.reveal
    fun dismissMenu(action: (() -> Unit)? = null) {
        if (!tabMenu.open) return // 退场中的额外触摸不能清除确认高亮或覆盖待执行操作。
        menuGesture.close(preserveConfirmation = action != null)
        // 若菜单在首帧动画前关闭，不等待尚未开始的退场动画，立即完成关闭。
        tabMenu.dismiss(immediately = tabMenuProgress.value <= 0f, action = action)
    }
    val tabEnterOffset = with(LocalDensity.current) { 20.dp.roundToPx() }
    val windowSize = LocalWindowInfo.current.containerSize
    LaunchedEffect(currentRoute, windowSize) { dismissMenu() }
    LaunchedEffect(currentRoute) {
        dependencies.actions.syncBarcodeDisplaySettings(currentRoute == AppRoute.Results)
        if (currentRoute == AppRoute.LanShare) {
            val viewModel = dependencies.lanShareViewModel()
            val lanState = viewModel.uiState.value
            if (lanState.session == null && !lanState.isStartingHost && !lanState.hostSessionInvalidated) {
                dependencies.actions.ensureLanShare()
            }
        }
    }
    LaunchedEffect(Unit) {
        dependencies.libraryDataViewModel.persistenceFailures.collect {
            dependencies.actions.notice(localizedResources.getString(R.string.data_save_failed))
        }
    }
    LaunchedEffect(dependencies.settingsViewModel) {
        dependencies.settingsViewModel.persistenceFailures.collect {
            dependencies.actions.notice(localizedResources.getString(R.string.settings_save_failed))
        }
    }
    BackHandler(enabled = tabMenu.menu != null) {
        dismissMenu()
    }
    LaunchedEffect(updateUiState.dialogShowing, updateUiState.availableVersion, updateUiState.availableUrl) {
        if (updateUiState.dialogShowing && updateUiState.availableVersion != null && updateUiState.availableUrl != null) {
            dependencies.actions.showUpdateDialog(updateUiState)
        }
    }

    AppTheme(settingsUiState.style.colorScheme, effects) {
        val dark = LocalResolvedAppAppearance.current.isDark
        LaunchedEffect(dark) { dismissMenu() }
        val colors = LocalAppColorScheme.current
        val dimensions = LocalAppDimensions.current
        val extendsToBottom = currentRoute.drawsBehindSystemNavigation
        val navigationInsets = WindowInsets.navigationBars
        val navigationBottomInset = navigationInsets.asPaddingValues().calculateBottomPadding()
        val bottomChromeInset = navigationBottomInset + dimensions.pageBottomPadding
        val imeInsets = WindowInsets.ime
        val insetDensity = LocalDensity.current
        val imeBottomPx = imeInsets.getBottom(insetDensity)
        val imeSourceBottomPx = WindowInsets.imeAnimationSource.getBottom(insetDensity)
        val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(insetDensity)
        val imeVisible = imeBottomPx > 0
        val generateWithKeyboard = currentRoute == AppRoute.Generate && imeVisible
        val generateInsetProgress = if (currentRoute == AppRoute.Generate) {
            generateImeBottomInsetProgress(imeBottomPx, imeSourceBottomPx, imeTargetBottomPx)
        } else 1f
        val shellInsets = pageShellInsets(currentRoute, generateWithKeyboard, dimensions.pageBottomPadding)
        val contentBottomPadding = if (currentRoute == AppRoute.Generate) {
            dimensions.pageBottomPadding * generateInsetProgress
        } else shellInsets.contentBottomPadding
        val backdrop = rememberGlassBackdrop()
        val kyantTabBackdrop = rememberLayerBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalLiquidGlassBackdrop provides kyantTabBackdrop,
            LocalLongPressMenuHost provides ::showMenu) {
        Box(Modifier.fillMaxSize().background(colors.surfaces.background)
            .contextMenuGestures(menuGesture, onDismiss = { dismissMenu() }, onAction = { dismissMenu(it) })) {
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val blurPx = if (android.os.Build.VERSION.SDK_INT >= 31) {
                        menuBackdropBlurPx(tabMenuProgress.value, density, effects.opaqueGlass || effects.reduceMotion)
                    } else 0f
                    compositingStrategy = if (blurPx > .1f) {
                        androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    } else {
                        androidx.compose.ui.graphics.CompositingStrategy.Auto
                    }
                    renderEffect = if (blurPx > .1f) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else null
                }.background(colors.surfaces.background).then(if (tabMenu.menu != null) Modifier
                    .clearAndSetSemantics { }
                    .focusProperties { canFocus = false }
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    } else Modifier),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (currentRoute == AppRoute.Results) Modifier else Modifier.statusBarsPadding())
                        .windowInsetsPadding(
                            if (extendsToBottom) navigationInsets.only(WindowInsetsSides.Horizontal) else navigationInsets,
                        )
                        .padding(
                            top = if (currentRoute == AppRoute.Results) 0.dp else dimensions.pageTopPadding,
                        ),
                ) {
                    Column(Modifier.fillMaxSize()
                        .padding(bottom = contentBottomPadding)
                        .then(if (bottomTabVisible && !effects.opaqueGlass && android.os.Build.VERSION.SDK_INT >= 33)
                            Modifier.layerBackdrop(kyantTabBackdrop) else Modifier)
                        .then(if (bottomTabVisible && !effects.opaqueGlass) Modifier.recordGlassBackdrop(
                            backdrop,
                            enabled = android.os.Build.VERSION.SDK_INT < 33 || tabMenu.menu != null,
                        ) else Modifier)
                        .background(colors.surfaces.background)) {
                        if (chromeVisible) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(
                                    if (currentRoute == AppRoute.Settings) dimensions.settingsHeaderHeight else dimensions.pageHeaderHeight,
                                ),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Text(
                                    text = stringResource(currentRoute.titleResId),
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.text.primary,
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                )
                                if (currentRoute == AppRoute.Favorites) {
                                    FavoritesManagementMenu(
                                        dark = dark,
                                        onImport = dependencies.actions::restoreFavorites,
                                        onExport = dependencies.actions::exportFavorites,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(end = dimensions.pageHorizontalPadding),
                                    )
                                }
                            }
                        }

                        val pageBottomInset = if (currentRoute == AppRoute.Generate) {
                            pageContentBottomInset(currentRoute, dimensions.bottomTabBarHeight) * generateInsetProgress
                        } else if (currentRoute.mainTabIndex != null) {
                            pageContentBottomInset(currentRoute, dimensions.bottomTabBarHeight)
                        } else pageTransitionBottomInset(
                            pageTransition.currentState, pageTransition.targetState, dimensions.bottomTabBarHeight,
                        )
                        Box(Modifier.fillMaxWidth().weight(1f)
                                .padding(bottom = pageBottomInset)
                                .then(if (currentRoute == AppRoute.Favorites || currentRoute == AppRoute.History) Modifier.imePadding() else Modifier)
                                .clipToBounds()
                                .background(colors.surfaces.background)) {
                        AppPageTransitionHost(
                            targetRoute = currentRoute,
                            transition = pageTransition,
                            transitionSpec = {
                                when (if (effects.reduceMotion) AppPageTransitionKind.NONE else appPageTransitionKind(initialState, targetState, appUiState.tabChangeFromSwipe)) {
                                    AppPageTransitionKind.NONE -> EnterTransition.None togetherWith ExitTransition.None using null
                                    AppPageTransitionKind.TAB_SELECTION ->
                                        (slideInVertically(
                                            animationSpec = tween(
                                                ComposeAnimationConfig.tabSelectionEnterDurationMillis,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) { tabEnterOffset } + fadeIn(tween(ComposeAnimationConfig.tabSelectionEnterDurationMillis))
                                            togetherWith fadeOut(tween(70))).apply {
                                            targetContentZIndex = 1f
                                        } using null
                                    AppPageTransitionKind.SECONDARY_PAGE ->
                                        fadeIn(tween(ComposeAnimationConfig.pageFadeInDurationMillis)) togetherWith
                                            fadeOut(tween(ComposeAnimationConfig.pageFadeOutDurationMillis)) using SizeTransform(clip = false)
                                }
                            }
                        ) { targetPage ->
                            // 标题保持原位，只让页面主体从下方向上进入，避免标题在切页时重叠。
                            AppPageContent(
                                route = targetPage,
                                dependencies = dependencies,
                                dark = dark,
                                pageStateHolder = pageStateHolder,
                                horizontalPadding = dimensions.pageHorizontalPadding,
                                listBottomPadding = if (imeVisible && (targetPage == AppRoute.Favorites || targetPage == AppRoute.History)) {
                                    8.dp
                                } else pageListBottomPadding(
                                    targetPage,
                                    dimensions.bottomTabBarHeight,
                                    bottomChromeInset.takeIf { extendsToBottom } ?: 0.dp,
                                ),
                                favoritesRowsProjectionCache = favoritesRowsProjectionCache,
                            )
                        }
                        }
                    }
                    if (bottomTabVisible) {
                        if (extendsToBottom && effects.opaqueGlass) {
                            // Keep the system gesture area continuous with the page canvas. The
                            // opaque capsule has its own surface in TabLiquidGlassScene.
                            val solidTabBottomExtension = navigationBottomInset + shellInsets.tabBarBottomPadding +
                                (dimensions.bottomTabBarHeight - 64.dp).coerceAtLeast(0.dp) / 2f
                            Box(
                                Modifier.align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(solidTabBottomExtension)
                                    .background(colors.surfaces.background),
                            )
                        }
                    }
                }
            }
            if (bottomTabVisible) {
                // Keep the Tab bar visible while its page backdrop is blurred by a long-press menu.
                // The full-screen menu overlay above it still owns touch and accessibility focus.
                BarcodeComposeBottomTabBar(
                    selectedIndex = appUiState.selectedTab,
                    dark = dark,
                    showTabLabels = settingsUiState.bottomTabLabelsVisible,
                    onTabSelected = { index, fromSwipe -> dependencies.actions.selectTab(index, fromSwipe) },
                    onGeneratePhotoFill = dependencies.actions::captureText,
                    onHistoryClear = dependencies.actions::clearHistory,
                    onFavoritesImport = dependencies.actions::restoreFavorites,
                    onFavoritesExport = dependencies.actions::exportFavorites,
                    onFavoritesClear = dependencies.actions::clearFavorites,
                    onCheckForUpdates = dependencies.actions::checkForUpdates,
                    onLongPressActionMenuRequested = ::showMenu,
                    showSelectionIndicator = tabMenu.menu == null,
                    hideTabItems = tabMenu.menu?.tabAnchor == true,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .windowInsetsPadding(navigationInsets)
                        .padding(bottom = shellInsets.tabBarBottomPadding)
                        .padding(horizontal = dimensions.pageHorizontalPadding).height(dimensions.bottomTabBarHeight)
                        .then(if (tabMenu.menu != null) Modifier.clearAndSetSemantics { }
                            .focusProperties { canFocus = false } else Modifier),
                )
            }
            tabMenu.menu?.let { menuState ->
                TabLongPressActionOverlay(
                    state = menuState,
                    progress = tabMenuProgress,
                    sourceProgress = menuMotion.source,
                    panelProgress = menuMotion.panel,
                    opacity = menuMotion.opacity,
                    actionExit = menuMotion.actionExit,
                    actionClosing = tabMenu.actionClosing,
                    interactive = tabMenu.open,
                    gesture = menuGesture,
                    onMeasured = { menuGesture.ready = true; tabMenu.measured() },
                    onDismiss = { dismissMenu() },
                    onAction = { dismissMenu(it) },
                )
            }
        }
        }
    }
}

@Composable
private fun AppPageContent(
    route: AppRoute,
    dependencies: ComposeAppShellDependencies,
    dark: Boolean,
    pageStateHolder: SaveableStateHolder,
    horizontalPadding: Dp,
    listBottomPadding: Dp,
    favoritesRowsProjectionCache: FavoritesRowsProjectionCache,
) {
    val colors = LocalAppColorScheme.current
    Box(
        Modifier.fillMaxSize()
            .background(colors.surfaces.background)
            .padding(horizontal = horizontalPadding)
            .clipToBounds(),
    ) {
        pageStateHolder.SaveableStateProvider(route.pageName) {
            ComposePageRenderer(dependencies, route, dark, favoritesRowsProjectionCache, listBottomPadding)
        }
    }
}

/** 在应用 Shell 生命周期内消费拍照识别事件，不依赖生成页是否当前显示。 */
@Composable
internal fun CameraOcrEventCollector(
    events: Flow<CameraOcrEvent>,
    onRecognizedText: (List<String>, List<String>?) -> OcrDraftResult,
    onNavigateToGenerate: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val localizedResources = LocalResources.current
    val currentOnRecognizedText by rememberUpdatedState(onRecognizedText)
    val currentOnNavigateToGenerate by rememberUpdatedState(onNavigateToGenerate)
    val currentOnNotice by rememberUpdatedState(onNotice)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is CameraOcrEvent.RecognizedText -> {
                    val applied = currentOnRecognizedText(event.lines, event.expectedDraft)
                    currentOnNavigateToGenerate()
                    currentOnNotice(
                        when (applied) {
                            OcrDraftResult.Appended -> localizedResources.getString(R.string.ocr_added_lines)
                            OcrDraftResult.DraftChanged -> localizedResources.getString(R.string.ocr_draft_changed)
                            OcrDraftResult.CapacityExceeded ->
                                localizedResources.getString(R.string.input_capacity_exceeded, MAX_GENERATE_INPUT_ROWS)
                        },
                    )
                }
                is CameraOcrEvent.Notice -> currentOnNotice(
                    event.rowLimit?.let { localizedResources.getString(event.messageResId, it) }
                        ?: localizedResources.getString(event.messageResId),
                )
            }
        }
    }
}
