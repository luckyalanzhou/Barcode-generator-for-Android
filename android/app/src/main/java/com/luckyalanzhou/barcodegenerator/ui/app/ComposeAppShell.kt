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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

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

/** Navigation Compose owns the back stack; the ViewModel remains the app-facing route state. */
@Composable
internal fun ComposeAppShell(dependencies: ComposeAppShellDependencies) {
    val appUiState by dependencies.navigationViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by dependencies.updateViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val initialPage = remember(navController) { appUiState.page }
    val initialBackStack = remember(navController) {
        when (initialPage) {
            AppRoute.Results -> listOf(
                initialPage.returnDestination(dependencies),
                AppRoute.Results,
            )
            AppRoute.Settings -> listOf(
                appUiState.settingsReturnPage.asMainDestination(),
                AppRoute.Settings,
            )
            AppRoute.LanShare -> listOf(
                appUiState.settingsReturnPage.asMainDestination(),
                AppRoute.Settings,
                AppRoute.LanShare,
            )
            else -> listOf(initialPage)
        }
    }
    val destinationName = backStackEntry?.destination?.route
    val destination = destinationName?.let(AppRoute::fromPage)
    var previousDestination by remember { mutableStateOf(destination) }

    LaunchedEffect(navController, initialBackStack) {
        val restoredRoute = navController.currentBackStackEntryFlow.first().destination.route
        if (restoredRoute == initialBackStack.first().pageName) {
            initialBackStack.drop(1).forEach { route ->
                navController.navigate(route.pageName) { launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(navController) {
        // Hold queued activity/UI navigation requests until NavHost has installed its graph.
        navController.currentBackStackEntryFlow.first()
        dependencies.navigationViewModel.navigationRequests.collect { request ->
            navController.navigateToAppRoute(request.route, request.topLevelDestination)
        }
    }
    LaunchedEffect(destinationName) {
        destinationName?.let { pageName ->
            dependencies.navigationViewModel.syncNavigationStateFromUi(AppRoute.fromPage(pageName))
        }
    }
    LaunchedEffect(destination) {
        val activeDestination = destination ?: return@LaunchedEffect
        if (previousDestination == AppRoute.LanShare && activeDestination != AppRoute.LanShare) {
            dependencies.actions.closeLanShare()
        }
        previousDestination = activeDestination
        dependencies.actions.syncBarcodeDisplaySettings(activeDestination == AppRoute.Results)
        if (activeDestination == AppRoute.LanShare && dependencies.lanShareViewModel.uiState.value.session == null) {
            dependencies.actions.ensureLanShare()
        }
    }
    LaunchedEffect(Unit) {
        dependencies.libraryDataViewModel.persistenceFailures.collect {
            dependencies.actions.notice("数据保存失败，请稍后重试")
        }
    }
    LaunchedEffect(updateUiState.dialogShowing, updateUiState.availableVersion, updateUiState.availableUrl) {
        if (updateUiState.dialogShowing && updateUiState.availableVersion != null && updateUiState.availableUrl != null) {
            dependencies.actions.showUpdateDialog(updateUiState)
        }
    }

    AppTheme(settingsUiState.style.colorScheme) {
        val dark = LocalResolvedAppAppearance.current.isDark
        val colors = LocalAppColorScheme.current
        Box(Modifier.fillMaxSize().background(colors.surfaces.background)) {
            NavHost(
                navController = navController,
                startDestination = initialBackStack.first().pageName,
                modifier = Modifier.fillMaxSize(),
            ) {
                AppRoute.entries.forEach { route ->
                    composable(route.pageName) {
                        AppRouteDestination(
                            dependencies = dependencies,
                            route = route,
                            selectedTab = appUiState.selectedTab,
                            dark = dark,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRouteDestination(
    dependencies: ComposeAppShellDependencies,
    route: AppRoute,
    selectedTab: Int,
    dark: Boolean,
) {
    val colors = LocalAppColorScheme.current
    val dimensions = LocalAppDimensions.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                top = if (route == AppRoute.Results) 0.dp else dimensions.pageTopPadding,
                bottom = dimensions.pageBottomPadding,
            ),
    ) {
        Column(Modifier.fillMaxWidth().weight(1f)) {
            if (route.chromeVisible) {
                Text(
                    text = route.title,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    color = colors.text.primary,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = dimensions.pageHorizontalPadding)) {
                ComposePageRenderer(dependencies, route, dark)
            }
        }
        if (route.chromeVisible) {
            BarcodeComposeBottomTabBar(
                selectedIndex = route.mainTabIndex ?: selectedTab,
                dark = dark,
                onTabSelected = { index, fromSwipe -> dependencies.actions.selectTab(index, fromSwipe) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.pageHorizontalPadding).height(dimensions.bottomTabBarHeight),
            )
        }
    }
}

private fun AppRoute.returnDestination(dependencies: ComposeAppShellDependencies): AppRoute =
    dependencies.resultsViewModel.resultUiState.value.returnPage.asMainDestination()

private fun AppRoute.asMainDestination(): AppRoute =
    takeIf { it.mainTabIndex != null && it != AppRoute.Settings } ?: AppRoute.Generate

private fun NavHostController.navigateToAppRoute(route: AppRoute, topLevelDestination: Boolean) {
    val target = route.pageName
    val current = currentDestination?.route
    if (current == target) return

    if (current == AppRoute.LanShare.pageName && route == AppRoute.Settings) {
        if (!popBackStack(target, inclusive = false)) navigate(target)
        return
    }

    if (topLevelDestination) {
        val startDestinationId = graph.findStartDestination().id
        navigate(target) {
            popUpTo(startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(target) { launchSingleTop = true }
    }
}
