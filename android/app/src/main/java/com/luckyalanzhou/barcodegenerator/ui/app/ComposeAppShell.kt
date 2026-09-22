package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.BarcodeViewModel
import com.luckyalanzhou.barcodegenerator.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.ui.rememberComposeAnimationConfig

import com.luckyalanzhou.barcodegenerator.ui.AppRoute

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.flow.collect

/** Compose 根层所需的状态和事件边界；Activity 只在入口处组装这些依赖。 */
internal data class ComposeAppShellDependencies(
    val viewModel: BarcodeViewModel,
    val generateViewModel: GenerateViewModel,
    val settingsViewModel: SettingsViewModel,
    val lanShareViewModel: LanShareViewModel,
    val actions: ComposeAppShellActions,
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
                        generateViewModel = activity.generateViewModel,
                        settingsViewModel = activity.settingsViewModel,
                        lanShareViewModel = activity.lanShareViewModel,
                        actions = activity.composeAppShellActions(),
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
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
        ?.let(AppRoute::fromPage)
        ?: appUiState.page
    val chromeVisible = currentRoute.chromeVisible
    val animation = rememberComposeAnimationConfig()

    // UI host owns navigation. The legacy AppUiState remains a read-only mirror
    // while page-specific state is migrated in subsequent phases.
    LaunchedEffect(navController, dependencies.viewModel) {
        navigateAppRoute(navController, appUiState.page)
        dependencies.viewModel.syncNavigationStateFromUi(appUiState.page)
        dependencies.viewModel.navigationEvents.collect { request ->
            navigateAppRoute(navController, request.route)
            dependencies.viewModel.syncNavigationStateFromUi(request.route, request.fromTabSwipe)
        }
    }

    LaunchedEffect(currentRoute) {
        dependencies.actions.syncBarcodeDisplaySettings(currentRoute == AppRoute.Results)
        if (currentRoute == AppRoute.LanShare && dependencies.lanShareViewModel.uiState.value.session == null) {
            dependencies.actions.ensureLanShare()
        }
    }

    LaunchedEffect(Unit) {
        dependencies.viewModel.persistenceFailures.collect {
            dependencies.actions.notice("数据保存失败，请稍后重试")
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

    AppTheme(settingsUiState.style.colorScheme) {
        val dark = LocalResolvedAppAppearance.current.isDark
        val rootThemeColors = LocalAppColorScheme.current
        val dimensions = LocalAppDimensions.current
        val background = rootThemeColors.surfaces.background
        Box(Modifier.fillMaxSize().background(background)) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    start = dimensions.pageHorizontalPadding,
                    end = dimensions.pageHorizontalPadding,
                    top = dimensions.pageTopPadding,
                    bottom = dimensions.pageBottomPadding,
                ),
            ) {
            if (chromeVisible) {
                Text(
                    text = currentRoute.title,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    color = LocalAppColorScheme.current.text.primary,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            NavHost(
                navController = navController,
                startDestination = AppRoute.Generate.pageName,
                modifier = Modifier.fillMaxWidth().weight(1f),
                enterTransition = {
                    appRouteTransitions(
                        initialState = initialState,
                        targetState = targetState,
                        tabSwipe = appUiState.tabChangeFromSwipe,
                        animation = animation,
                    ).first
                },
                exitTransition = {
                    appRouteTransitions(
                        initialState = initialState,
                        targetState = targetState,
                        tabSwipe = appUiState.tabChangeFromSwipe,
                        animation = animation,
                    ).second
                },
                popEnterTransition = {
                    appRouteTransitions(
                        initialState = initialState,
                        targetState = targetState,
                        tabSwipe = false,
                        animation = animation,
                    ).first
                },
                popExitTransition = {
                    appRouteTransitions(
                        initialState = initialState,
                        targetState = targetState,
                        tabSwipe = false,
                        animation = animation,
                    ).second
                },
            ) {
                AppRoute.entries.forEach { destination ->
                    composable(destination.pageName) {
                        ComposeNavigationHost(dependencies, destination, dark)
                    }
                }
            }

            if (chromeVisible) {
                BarcodeComposeBottomTabBar(
                    selectedIndex = appUiState.selectedTab,
                    dark = dark,
                    onTabSelected = { index, fromSwipe -> dependencies.actions.selectTab(index, fromSwipe) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.bottomTabBarHeight),
                )
            }
        }
    }
}

}

private fun appRouteTransitions(
    initialState: NavBackStackEntry,
    targetState: NavBackStackEntry,
    tabSwipe: Boolean,
    animation: ComposeAnimationConfig,
): Pair<androidx.compose.animation.EnterTransition, androidx.compose.animation.ExitTransition> {
    val from = initialState.destination.route?.let(AppRoute::fromPage)
    val to = targetState.destination.route?.let(AppRoute::fromPage)
    if (from?.mainTabIndex != null && to?.mainTabIndex != null) {
        if (tabSwipe) {
            val forward = to.mainTabIndex > from.mainTabIndex
            return slideInHorizontally(tween(200)) { width -> if (forward) width / 8 else -width / 8 } + fadeIn(tween(180)) to
                (slideOutHorizontally(tween(160)) { width -> if (forward) -width / 8 else width / 8 } + fadeOut(tween(160)))
        }
        return (scaleIn(initialScale = .97f, animationSpec = tween(180)) + fadeIn(tween(180))) to
            (scaleOut(targetScale = 1.02f, animationSpec = tween(120)) + fadeOut(tween(120)))
    }

    // Detail-route animations stay deterministic for a given source/destination pair.
    val variant = kotlin.math.abs(((from?.ordinal ?: 0) * 31 + (to?.ordinal ?: 0)).rem(4))
    return when (variant) {
        0 -> (slideInHorizontally(tween(animation.pageEnterDurationMillis)) { it / 2 } + fadeIn(tween(animation.pageFadeInDurationMillis))) to
            (slideOutHorizontally(tween(animation.pageExitDurationMillis)) { -it / 3 } + fadeOut(tween(animation.pageFadeOutDurationMillis)))
        1 -> (androidx.compose.animation.slideInVertically(tween(animation.pageEnterDurationMillis)) { -it / 3 } + fadeIn(tween(animation.pageFadeInDurationMillis))) to
            (androidx.compose.animation.slideOutVertically(tween(animation.pageExitDurationMillis)) { it / 3 } + fadeOut(tween(animation.pageFadeOutDurationMillis)))
        2 -> (scaleIn(initialScale = .88f, animationSpec = tween(animation.pageEnterDurationMillis)) + fadeIn(tween(animation.pageFadeInDurationMillis))) to
            (scaleOut(targetScale = .94f, animationSpec = tween(animation.pageExitDurationMillis)) + fadeOut(tween(animation.pageFadeOutDurationMillis)))
        else -> (fadeIn(tween(animation.pageEnterDurationMillis)) + androidx.compose.animation.slideInVertically(tween(animation.pageEnterDurationMillis)) { it / 2 }) to
            (fadeOut(tween(animation.pageExitDurationMillis)) + androidx.compose.animation.slideOutHorizontally(tween(animation.pageExitDurationMillis)) { it / 4 })
    }
}

private fun navigateAppRoute(navController: NavHostController, target: AppRoute) {
    if (navController.currentBackStackEntry?.destination?.route == target.pageName) return
    if (target.mainTabIndex != null) {
        navController.navigate(target.pageName) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navController.navigate(target.pageName) { launchSingleTop = true }
    }
}
