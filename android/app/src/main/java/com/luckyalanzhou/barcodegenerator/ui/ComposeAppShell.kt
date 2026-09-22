package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.theme.*

import com.luckyalanzhou.barcodegenerator.BarcodeViewModel
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.SettingsViewModel
import com.luckyalanzhou.barcodegenerator.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.ui.rememberComposeAnimationConfig

import com.luckyalanzhou.barcodegenerator.ui.AppRoute

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
import kotlinx.coroutines.flow.collect

/** Compose 根层所需的状态和事件边界；Activity 只在入口处组装这些依赖。 */
internal data class ComposeAppShellDependencies(
    val viewModel: BarcodeViewModel,
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
    val fireworksVisible by dependencies.viewModel.fireworksVisible.collectAsStateWithLifecycle()
    val currentRoute = appUiState.page
    val chromeVisible = currentRoute.chromeVisible
    val animation = rememberComposeAnimationConfig()

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
            if (currentRoute.mainTabIndex != null) {
                // 主 Tab 使用轻量淡入淡出；不加入位移、缩放或尺寸变化，
                // 避免收藏树、历史分组等页面切换时产生额外布局开销。
                AnimatedContent(
                    targetState = currentRoute,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(180)) togetherWith
                            fadeOut(animationSpec = tween(120)) using
                            SizeTransform(clip = false)
                    },
                    label = "mainTabFadeTransition",
                ) { targetPage ->
                    Column(Modifier.fillMaxSize()) {
                        Text(
                            text = targetPage.title,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            color = LocalAppColorScheme.current.text.primary,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            ComposeNavigationHost(dependencies, targetPage, dark)
                        }
                    }
                }
            } else AnimatedContent(
                targetState = currentRoute,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    val fromMainTab = initialState.mainTabIndex != null
                    val toMainTab = targetState.mainTabIndex != null
                    if (fromMainTab && toMainTab) {
                        // 主 Tab 使用轻量淡入与 8dp 微上移；液态玻璃选中框负责横向弹簧移动。
                        (slideInVertically(
                            animationSpec = tween(210),
                            initialOffsetY = { 8 },
                        ) + fadeIn(tween(190))) togetherWith
                            (slideOutVertically(
                                animationSpec = tween(170),
                                targetOffsetY = { -4 },
                            ) + fadeOut(tween(150))) using
                            SizeTransform(clip = false)
                    } else {
                        // 非主 Tab 页面使用稳定的伪随机选择，避免重组时真正随机导致动画跳变。
                        when (kotlin.math.abs((initialState.hashCode() * 31 + targetState.hashCode()).rem(4))) {
                            0 -> (slideInHorizontally(
                                animationSpec = tween(animation.pageEnterDurationMillis),
                                initialOffsetX = { it / 2 },
                            ) + fadeIn(tween(animation.pageFadeInDurationMillis))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(animation.pageExitDurationMillis),
                                    targetOffsetX = { -it / 3 },
                                ) + fadeOut(tween(animation.pageFadeOutDurationMillis))) using SizeTransform(clip = false)
                            1 -> (slideInVertically(
                                animationSpec = tween(animation.pageEnterDurationMillis),
                                initialOffsetY = { -it / 3 },
                            ) + fadeIn(tween(animation.pageFadeInDurationMillis))) togetherWith
                                (slideOutVertically(
                                    animationSpec = tween(animation.pageExitDurationMillis),
                                    targetOffsetY = { it / 3 },
                                ) + fadeOut(tween(animation.pageFadeOutDurationMillis))) using SizeTransform(clip = false)
                            2 -> (scaleIn(
                                initialScale = .88f,
                                animationSpec = tween(animation.pageEnterDurationMillis),
                            ) + fadeIn(tween(animation.pageFadeInDurationMillis))) togetherWith
                                (scaleOut(
                                    targetScale = .94f,
                                    animationSpec = tween(animation.pageExitDurationMillis),
                                ) + fadeOut(tween(animation.pageFadeOutDurationMillis))) using SizeTransform(clip = false)
                            else -> (fadeIn(tween(animation.pageEnterDurationMillis)) + slideInVertically(
                                animationSpec = tween(animation.pageEnterDurationMillis),
                                initialOffsetY = { it / 2 },
                            )) togetherWith
                                (fadeOut(tween(animation.pageExitDurationMillis)) + slideOutHorizontally(
                                    animationSpec = tween(animation.pageExitDurationMillis),
                                    targetOffsetX = { it / 4 },
                                )) using SizeTransform(clip = false)
                        }
                    }
                },
                label = "pageUpTransition",
            ) { targetPage ->
                val targetRoute = targetPage
                val targetChromeVisible = targetRoute.chromeVisible
                Column(Modifier.fillMaxSize()) {
                    if (targetChromeVisible) {
                        Text(
                            text = targetRoute.title,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            color = LocalAppColorScheme.current.text.primary,
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
                        .height(dimensions.bottomTabBarHeight),
                )
            }
        }
            if (fireworksVisible) {
                ComposeFireworksOverlay()
            }
    }
}
}
