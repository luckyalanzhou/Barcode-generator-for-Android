package com.luckyalanzhou.barcodegenerator.ui.app

import com.luckyalanzhou.barcodegenerator.presentation.*
import com.luckyalanzhou.barcodegenerator.presentation.generate.GenerateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.settings.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.presentation.update.UpdateViewModel
import com.luckyalanzhou.barcodegenerator.presentation.favorites.FavoritesViewModel
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.ui.theme.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.flow.collect

internal data class ComposeAppShellDependencies(
    val viewModel: BarcodeViewModel,
    val generateViewModel: GenerateViewModel,
    val settingsViewModel: SettingsViewModel,
    val lanShareViewModel: LanShareViewModel,
    val updateViewModel: UpdateViewModel,
    val favoritesViewModel: FavoritesViewModel,
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
                        viewModel = activity.viewModel,
                        generateViewModel = activity.generateViewModel,
                        settingsViewModel = activity.settingsViewModel,
                        lanShareViewModel = activity.lanShareViewModel,
                        updateViewModel = activity.updateViewModel,
                        favoritesViewModel = activity.favoritesViewModel,
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
    val appUiState by dependencies.viewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by dependencies.settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateUiState by dependencies.updateViewModel.uiState.collectAsStateWithLifecycle()
    val currentRoute = appUiState.page
    val chromeVisible = currentRoute.chromeVisible
    val animation = rememberComposeAnimationConfig()
    val pageStateHolder = rememberSaveableStateHolder()

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
    LaunchedEffect(updateUiState.dialogShowing, updateUiState.availableVersion, updateUiState.availableUrl) {
        if (updateUiState.dialogShowing && updateUiState.availableVersion != null && updateUiState.availableUrl != null) {
            dependencies.actions.showUpdateDialog(updateUiState)
        }
    }

    AppTheme(settingsUiState.style.colorScheme) {
        val dark = LocalResolvedAppAppearance.current.isDark
        val colors = LocalAppColorScheme.current
        val dimensions = LocalAppDimensions.current
        Box(Modifier.fillMaxSize().background(colors.surfaces.background)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        top = if (currentRoute == AppRoute.Results) 0.dp else dimensions.pageTopPadding,
                        bottom = dimensions.pageBottomPadding,
                    ),
            ) {
                AnimatedContent(
                    targetState = currentRoute,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    transitionSpec = {
                        if (targetState == AppRoute.Results) {
                            androidx.compose.animation.EnterTransition.None togetherWith
                                androidx.compose.animation.ExitTransition.None
                        } else {
                            val initialTabIndex = initialState.mainTabIndex
                        val targetTabIndex = targetState.mainTabIndex
                        if (initialTabIndex != null && targetTabIndex != null) {
                            if (appUiState.tabChangeFromSwipe) {
                                val forward = targetTabIndex > initialTabIndex
                                (slideInHorizontally(tween(200)) { if (forward) it / 8 else -it / 8 } + fadeIn(tween(180))) togetherWith
                                    (slideOutHorizontally(tween(160)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(160))) using SizeTransform(clip = true)
                            } else {
                                (scaleIn(initialScale = .97f, animationSpec = tween(180)) + fadeIn(tween(180))) togetherWith
                                    (scaleOut(targetScale = 1.02f, animationSpec = tween(120)) + fadeOut(tween(120))) using SizeTransform(clip = false)
                            }
                        } else {
                            when (kotlin.math.abs((initialState.hashCode() * 31 + targetState.hashCode()).rem(4))) {
                                0 -> (slideInHorizontally(tween(animation.pageEnterDurationMillis)) { it / 2 } + fadeIn(tween(animation.pageFadeInDurationMillis))) togetherWith
                                    (slideOutHorizontally(tween(animation.pageExitDurationMillis)) { -it / 3 } + fadeOut(tween(animation.pageFadeOutDurationMillis))) using SizeTransform(clip = false)
                                1 -> (slideInVertically(tween(animation.pageEnterDurationMillis)) { -it / 3 } + fadeIn(tween(animation.pageFadeInDurationMillis))) togetherWith
                                    (slideOutVertically(tween(animation.pageExitDurationMillis)) { it / 3 } + fadeOut(tween(animation.pageFadeOutDurationMillis))) using SizeTransform(clip = false)
                                2 -> (scaleIn(initialScale = .88f, animationSpec = tween(animation.pageEnterDurationMillis)) + fadeIn(tween(animation.pageFadeInDurationMillis))) togetherWith
                                    (scaleOut(targetScale = .94f, animationSpec = tween(animation.pageExitDurationMillis)) + fadeOut(tween(animation.pageExitDurationMillis))) using SizeTransform(clip = false)
                                else -> (fadeIn(tween(animation.pageEnterDurationMillis)) + slideInVertically(tween(animation.pageEnterDurationMillis)) { it / 2 }) togetherWith
                                    (fadeOut(tween(animation.pageExitDurationMillis)) + slideOutHorizontally(tween(animation.pageExitDurationMillis)) { it / 4 }) using SizeTransform(clip = false)
                            }
                        }
                        }
                    },
                    label = "pageTransition",
                ) { targetPage ->
                    Column(Modifier.fillMaxSize()) {
                        if (targetPage.chromeVisible) {
                            Text(
                                text = targetPage.title,
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                color = colors.text.primary,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = dimensions.pageHorizontalPadding)) {
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.pageHorizontalPadding).height(dimensions.bottomTabBarHeight),
                    )
                }
            }
        }
    }
}
