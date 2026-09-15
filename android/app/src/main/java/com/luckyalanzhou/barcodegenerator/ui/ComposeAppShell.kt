package com.luckyalanzhou.barcodegenerator

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** 创建唯一的 Compose 根节点；业务状态仍由 MainActivity/ViewModel 保存。 */
internal fun MainActivity.buildComposeShell() {
    val activity = this
    composeShellTitle = "条码生成器"
    composeShellChromeVisible = true
    setContentView(
        ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ComposeAppShell(
                    activity = activity,
                    onTabSelected = activity::activateMainTab,
                )
            }
        }
    )
    composeShellReady = true
}

/** Compose 底部导航的统一页面回调，保持原有页面切换和返回目标逻辑。 */
internal fun MainActivity.activateMainTab(index: Int) {
    val tabPages = listOf("generate", "history", "favorites", "settings")
    if (index !in tabPages.indices) return
    val current = tabPageIndex()
    if (index != current) pendingPageTransitionDirection = if (index > current) 1 else -1
    if (index == 3) {
        openSettings()
    } else if (page != tabPages[index]) {
        if (page == "lanShare") closeLanShare()
        if (index == 0) {
            selectedFavoriteGroup = null
            resultsReturnPage = "generate"
            showingHistoryResult = false
        }
        page = tabPages[index]
        render()
    } else {
        updateTopTabSelection()
    }
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
) {
    // 读取 revision，确保旧业务 render() 更新标题或页面可见性后立即重组。
    activity.composeShellRevision.intValue
    val background = Color(activity.appBackground())
    val chromeVisible = activity.composeShellChromeVisible
    val title = activity.composeShellTitle

    Box(Modifier.fillMaxSize().background(background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
        ) {
            if (chromeVisible) {
                Text(
                    text = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    color = if (activity.isDark()) Color(0xfff2f4f8) else Color(0xff182230),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                ComposePageRoute(activity)
            }

            if (chromeVisible) {
                BarcodeComposeBottomTabBar(
                    selectedIndex = activity.composeTabSelection.intValue,
                    dark = activity.isDark(),
                    onTabSelected = onTabSelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                )
            }
        }
        if (activity.composeFireworksVisible.value) {
            ComposeFireworksOverlay()
        }
    }
}

@Composable
private fun ComposePageRoute(activity: MainActivity) {
    // render() 通过 revision 通知根 Compose 页面状态已变化；页面自身仍以业务字段为唯一数据源。
    activity.composeShellRevision.intValue
    key(activity.page) {
        when (activity.page) {
            "generate" -> {
                val initialFormat = remember(activity.page) {
                    activity.pendingGenerateFormat ?: activity.generateFormatName
                }
                LaunchedEffect(activity.page) {
                    activity.saveInputDraft()
                    activity.pendingGenerateFormat = null
                    activity.generateFormatName = initialFormat
                }
                ComposeGeneratePage(activity, initialFormat)
            }
            "history" -> {
                val entries = activity.items.filter { it.inHistory }
                    .groupBy { it.createdAt }.toList().sortedByDescending { it.first }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    HistoryComposePage(
                        entries = entries,
                        dark = activity.isDark(),
                        onClear = { activity.confirmClear(false) },
                        onOpen = { batch ->
                            activity.resultItems = batch.sortedBy { it.id }.toMutableList()
                            activity.showingHistoryResult = true
                            activity.resultsReturnPage = "history"
                            activity.page = "results"
                            activity.render()
                        },
                        onEdit = { batch ->
                            if (batch.size == 1) activity.showItemEditor(batch.first())
                            else activity.showHistoryBatchPickerCompose(batch)
                        },
                        onDelete = { batch ->
                            batch.forEach { it.inHistory = false }
                            activity.saveItems()
                            activity.render()
                        },
                        timeText = activity::formatHistoryTime,
                    )
                }
            }
            "favorites" -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) { ComposeFavoritesPage(activity) }
            }
            "favoriteDetail" -> {
                val group = activity.selectedFavoriteGroup
                if (group == null) {
                    LaunchedEffect(Unit) {
                        activity.page = "favorites"
                        activity.render()
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) { ComposeFavoriteDetailPage(activity, group) }
                }
            }
            "results" -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) { ComposeResultsPage(activity) }
            }
            "settings" -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) { ComposeSettingsPage(activity) }
            }
            "lanShare" -> {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) { ComposeLanSharePage(activity) }
            }
            // Beta 测试中心也直接作为 Compose 内容路由，不再嵌套旧 AndroidView。
            "betaTestCenter" -> {
                BetaTestCenterComposePage(activity)
            }
            else -> Box(Modifier.fillMaxSize())
        }
    }
}
