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
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.blur
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
    val chromeVisible = currentRoute.chromeVisible
    val pageStateHolder = rememberSaveableStateHolder()
    var tabMenuState by remember { mutableStateOf<TabLongPressMenuState?>(null) }
    val tabMenuBackdropBlur by animateDpAsState(
        targetValue = if (tabMenuState == null) 0.dp else 20.dp,
        animationSpec = tween(190, easing = FastOutSlowInEasing),
        label = "tab-menu-background-blur",
    )
    val tabEnterOffset = with(LocalDensity.current) { 20.dp.roundToPx() }
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
    BackHandler(enabled = tabMenuState != null) {
        tabMenuState = null
    }
    LaunchedEffect(updateUiState.dialogShowing, updateUiState.availableVersion, updateUiState.availableUrl) {
        if (updateUiState.dialogShowing && updateUiState.availableVersion != null && updateUiState.availableUrl != null) {
            dependencies.actions.showUpdateDialog(updateUiState)
        }
    }

    AppTheme(settingsUiState.style.colorScheme) {
        val dark = LocalResolvedAppAppearance.current.isDark
        val colors = LocalAppColorScheme.current
        val dimensions = LocalAppDimensions.current
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(colors.surfaces.background)) {
            Box(
                modifier = Modifier.fillMaxSize().blur(tabMenuBackdropBlur),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(
                            top = if (currentRoute == AppRoute.Results) 0.dp else dimensions.pageTopPadding,
                            bottom = dimensions.pageBottomPadding,
                        ),
                ) {
                    Column(Modifier.fillMaxSize().recordGlassBackdrop(backdrop).background(colors.surfaces.background)) {
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

                        AnimatedContent(
                            targetState = currentRoute,
                            // Keep scrollable content behind the floating navigation rail;
                            // main-tab lists reserve a trailing inset so their final items remain reachable.
                            modifier = Modifier.fillMaxWidth().weight(1f)
                                .background(colors.surfaces.background),
                            transitionSpec = {
                                when (appPageTransitionKind(initialState, targetState, appUiState.tabChangeFromSwipe)) {
                                    AppPageTransitionKind.NONE -> EnterTransition.None togetherWith ExitTransition.None using null
                                    AppPageTransitionKind.TAB_SWIPE, AppPageTransitionKind.TAB_SELECTION ->
                                        (slideInVertically(
                                            animationSpec = tween(
                                                ComposeAnimationConfig.tabSelectionEnterDurationMillis,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        ) { tabEnterOffset } togetherWith ExitTransition.None).apply {
                                            targetContentZIndex = 1f
                                        } using SizeTransform(clip = true)
                                    AppPageTransitionKind.SECONDARY_PAGE ->
                                        fadeIn(tween(ComposeAnimationConfig.pageFadeInDurationMillis)) togetherWith
                                            fadeOut(tween(ComposeAnimationConfig.pageFadeOutDurationMillis)) using SizeTransform(clip = false)
                                }
                            },
                            label = "pageTransition",
                        ) { targetPage ->
                            // Keep the title stationary while only the page body enters from below.
                            Box(
                                Modifier.fillMaxSize()
                                    .background(colors.surfaces.background)
                                    .padding(horizontal = dimensions.pageHorizontalPadding),
                            ) {
                                pageStateHolder.SaveableStateProvider(targetPage.pageName) {
                                    ComposePageRenderer(dependencies, targetPage, dark)
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
                            onLongPressActionMenuRequested = { tabMenuState = it },
                            showSelectionIndicator = tabMenuState == null,
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .padding(horizontal = dimensions.pageHorizontalPadding).height(dimensions.bottomTabBarHeight),
                        )
                    }
                }
            }
            tabMenuState?.let { menuState ->
                TabLongPressActionOverlay(
                    state = menuState,
                    onDismiss = { tabMenuState = null },
                )
            }
        }
        }
    }
}
