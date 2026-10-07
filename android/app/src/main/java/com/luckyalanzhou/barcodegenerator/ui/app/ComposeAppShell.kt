package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.presentation.update.UpdateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesViewModel
import com.luckyalanzhou.barcodegenerator.presentation.results.ResultsViewModel
import com.luckyalanzhou.barcodegenerator.presentation.navigation.AppNavigationViewModel
import com.luckyalanzhou.barcodegenerator.presentation.history.HistoryViewModel
import com.luckyalanzhou.barcodegenerator.presentation.shared.BarcodeItemViewModel
import com.luckyalanzhou.barcodegenerator.presentation.shared.LibraryDataViewModel
import com.luckyalanzhou.barcodegenerator.presentation.camera.CameraOcrViewModel
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.theme.*
import com.luckyalanzhou.barcodegenerator.ui.animation.ComposeAnimationConfig
import com.luckyalanzhou.barcodegenerator.ui.component.menu.LocalLongPressMenuHost
import com.luckyalanzhou.barcodegenerator.ui.component.menu.TabLongPressMenuState

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.flow.collect
import com.luckyalanzhou.barcodegenerator.ui.component.ContextMenuGestureSession
import com.luckyalanzhou.barcodegenerator.ui.component.contextMenuGestures

internal data class ComposeAppShellDependencies(
    val navigationViewModel: AppNavigationViewModel,
    val cameraOcrViewModel: CameraOcrViewModel,
    val generateViewModel: GenerateViewModel,
    val settingsViewModel: SettingsViewModel,
    val lanShareViewModel: LanShareViewModel,
    val updateViewModel: UpdateViewModel,
    val favoritesViewModel: FavoritesViewModel,
    val historyViewModel: HistoryViewModel,
    val resultsViewModel: ResultsViewModel,
    val barcodeItemViewModel: BarcodeItemViewModel,
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
                        lanShareViewModel = activity.lanShareViewModel,
                        updateViewModel = activity.updateViewModel,
                        favoritesViewModel = activity.favoritesViewModel,
                        historyViewModel = activity.historyViewModel,
                        resultsViewModel = activity.resultsViewModel,
                        barcodeItemViewModel = activity.barcodeItemViewModel,
                        libraryDataViewModel = activity.libraryDataViewModel,
                        actions = activity.composeAppShellActions(),
                    ),
                )
            }
        },
    )
    composeShellReady = true
}

/** Single source of truth for pages: AppUiState drives rendering; no NavController race. */
@Composable
internal fun ComposeAppShell(dependencies: ComposeAppShellDependencies) {
    val appUiState by dependencies.navigationViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by dependencies.updateViewModel.uiState.collectAsStateWithLifecycle()
    val currentRoute = appUiState.page
    val pageTransition = updateTransition(currentRoute, label = "pageTransition")
    val chromeVisible = currentRoute.chromeVisible
    val pageStateHolder = rememberSaveableStateHolder()
    val effects = rememberVisualEffectsPolicy(settingsUiState.style)
    var focusToRestore by remember { mutableStateOf<(() -> Unit)?>(null) }
    val tabMenu = remember { TabMenuPresentation<TabLongPressMenuState> { focusToRestore = it.restoreFocus } }
    val menuGesture = remember { ContextMenuGestureSession() }
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
    val tabMenuProgress = animateFloatAsState(
        targetValue = if (tabMenu.open && tabMenu.ready) 1f else 0f,
        animationSpec = tween(if (effects.reduceMotion) 0 else if (tabMenu.menu?.tabAnchor == false) 240 else 190, easing = FastOutSlowInEasing),
        finishedListener = { if (it == 0f) tabMenu.closed() },
        label = "tab-menu-presentation",
    )
    fun dismissMenu(action: (() -> Unit)? = null) {
        menuGesture.close()
        // A dismiss may precede the first animated frame; do not wait for a non-existent exit.
        tabMenu.dismiss(immediately = tabMenuProgress.value <= 0f, action = action)
    }
    val tabEnterOffset = with(LocalDensity.current) { 20.dp.roundToPx() }
    val windowSize = LocalWindowInfo.current.containerSize
    LaunchedEffect(currentRoute, windowSize) { dismissMenu() }
    LaunchedEffect(currentRoute) {
        dependencies.actions.syncBarcodeDisplaySettings(currentRoute == AppRoute.Results)
        if (currentRoute == AppRoute.LanShare && dependencies.lanShareViewModel.uiState.value.session == null) {
            dependencies.actions.ensureLanShare()
        }
    }
    LaunchedEffect(Unit) {
        dependencies.libraryDataViewModel.persistenceFailures.collect {
            dependencies.actions.notice("数据保存失败，请稍后重试")
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

    AppTheme(settingsUiState.style.colorScheme) {
        val dark = LocalResolvedAppAppearance.current.isDark
        LaunchedEffect(dark) { dismissMenu() }
        val colors = LocalAppColorScheme.current
        val dimensions = LocalAppDimensions.current
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalVisualEffectsPolicy provides effects,
            LocalLongPressMenuHost provides ::showMenu) {
        Box(Modifier.fillMaxSize().background(colors.surfaces.background)
            .contextMenuGestures(menuGesture, onDismiss = { dismissMenu() }, onAction = { dismissMenu(it) })) {
            Box(
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val blurPx = menuBackdropBlurPx(tabMenuProgress.value, density, effects.opaqueGlass)
                    renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31 && blurPx > .1f) {
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
                        .navigationBarsPadding()
                        .padding(
                            top = if (currentRoute == AppRoute.Results) 0.dp else dimensions.pageTopPadding,
                            bottom = dimensions.pageBottomPadding,
                        ),
                ) {
                    Column(Modifier.fillMaxSize()
                        .then(if (chromeVisible && !effects.opaqueGlass) Modifier.recordGlassBackdrop(backdrop) else Modifier)
                        .background(colors.surfaces.background)) {
                        if (chromeVisible) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(
                                    if (currentRoute == AppRoute.Settings) dimensions.settingsHeaderHeight else dimensions.pageHeaderHeight,
                                ),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Text(
                                    text = currentRoute.title,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.text.primary,
                                    fontSize = 25.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        // A stationary parent clips both incoming and outgoing animated layers.
                        // Retain the outgoing page's inset until it is disposed, including Generate.
                        Box(Modifier.fillMaxWidth().weight(1f)
                                .padding(bottom = pageTransitionBottomInset(pageTransition.currentState,
                                    pageTransition.targetState, dimensions.bottomTabBarHeight))
                                .clipToBounds()
                                .background(colors.surfaces.background)) {
                        pageTransition.AnimatedContent(
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                when (if (effects.reduceMotion) AppPageTransitionKind.NONE else appPageTransitionKind(initialState, targetState, appUiState.tabChangeFromSwipe)) {
                                    AppPageTransitionKind.NONE -> EnterTransition.None togetherWith ExitTransition.None using null
                                    AppPageTransitionKind.TAB_SWIPE, AppPageTransitionKind.TAB_SELECTION ->
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
                            },
                        ) { targetPage ->
                            // Keep the title stationary while only the page body enters from below.
                            Box(
                                Modifier.fillMaxSize()
                                    .background(colors.surfaces.background)
                                    .padding(
                                        horizontal = dimensions.pageHorizontalPadding,
                                    )
                                    .clipToBounds(),
                            ) {
                                pageStateHolder.SaveableStateProvider(targetPage.pageName) {
                                    ComposePageRenderer(dependencies, targetPage, dark)
                                }
                            }
                        }
                        }
                    }
                    if (chromeVisible) {
                        BarcodeComposeBottomTabBar(
                            selectedIndex = appUiState.selectedTab,
                            dark = dark,
                            onTabSelected = { index, fromSwipe -> dependencies.actions.selectTab(index, fromSwipe) },
                            onHistoryClear = dependencies.actions::clearHistory,
                            onFavoritesImport = dependencies.actions::restoreFavorites,
                            onFavoritesExport = dependencies.actions::exportFavorites,
                            onCheckForUpdates = dependencies.actions::checkForUpdates,
                            onLongPressActionMenuRequested = ::showMenu,
                            showSelectionIndicator = tabMenu.menu == null,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .padding(horizontal = dimensions.pageHorizontalPadding).height(dimensions.bottomTabBarHeight),
                        )
                    }
                }
            }
            tabMenu.menu?.let { menuState ->
                TabLongPressActionOverlay(
                    state = menuState,
                    progress = tabMenuProgress,
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
