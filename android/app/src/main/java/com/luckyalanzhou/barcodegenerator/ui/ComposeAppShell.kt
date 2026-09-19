package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*
import com.luckyalanzhou.barcodegenerator.data.*
import com.luckyalanzhou.barcodegenerator.domain.*
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
    val rootThemeColors = barcodeThemeColors(dark)
    val background = rootThemeColors.background
    val currentRoute = appUiState.page
    val chromeVisible = currentRoute.chromeVisible
    val animation = rememberComposeAnimationConfig()

    LaunchedEffect(currentRoute) {
        dependencies.actions.syncBarcodeDisplaySettings(currentRoute == AppRoute.Results)
        if (currentRoute == AppRoute.LanShare && dependencies.lanShareViewModel.uiState.value.session == null) {
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

    val colorScheme = if (dark) barcodeDarkColorScheme(background) else barcodeLightColorScheme(background)
    val themeColors = rootThemeColors
    CompositionLocalProvider(LocalBarcodeThemeColors provides themeColors) {
        MaterialTheme(colorScheme = colorScheme) {
        Box(Modifier.fillMaxSize().background(background)) {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
            ) {
            if (currentRoute.mainTabIndex != null) {
                // 主 Tab 不再让 AnimatedContent 同时组合旧页和新页。
                // 收藏树、历史分组等页面在更新后的首次进入可能因此阻塞主线程数秒。
                // 页面即时替换，底部液态玻璃选中框和页面内部动画仍保持流畅。
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    Text(
                        text = currentRoute.title,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        color = LocalBarcodeThemeColors.current.primary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        ComposeNavigationHost(dependencies, currentRoute, dark)
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
                            color = LocalBarcodeThemeColors.current.primary,
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
}
