package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Transition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier

internal enum class AppPageTransitionKind {
    NONE,
    TAB_SELECTION,
    SECONDARY_PAGE,
}

internal enum class AppPageHostKind {
    DIRECT,
    ANIMATED,
}

internal fun appPageHostKind(targetRoute: AppRoute): AppPageHostKind =
    if (targetRoute.mainTabIndex != null) {
        AppPageHostKind.DIRECT
    } else {
        AppPageHostKind.ANIMATED
    }

/** 主 Tab 直接替换页面组合，避免切换时同时测量和绘制两张完整页面。 */
@Composable
internal fun AppPageTransitionHost(
    targetRoute: AppRoute,
    transition: Transition<AppRoute>,
    transitionSpec: AnimatedContentTransitionScope<AppRoute>.() -> ContentTransform,
    content: @Composable (AppRoute) -> Unit,
) {
    if (appPageHostKind(targetRoute) == AppPageHostKind.DIRECT) {
        Box(Modifier.fillMaxSize()) {
            key(targetRoute.pageName) { content(targetRoute) }
        }
    } else {
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = transitionSpec,
        ) { targetRoute ->
            content(targetRoute)
        }
    }
}

internal fun appPageTransitionKind(
    initialRoute: AppRoute,
    targetRoute: AppRoute,
    tabChangeFromSwipe: Boolean,
): AppPageTransitionKind = when {
    initialRoute == targetRoute -> AppPageTransitionKind.NONE
    initialRoute == AppRoute.Results || targetRoute == AppRoute.Results -> AppPageTransitionKind.NONE
    initialRoute.mainTabIndex != null && targetRoute.mainTabIndex != null ->
        // 拖动中目标页会随手指连续变化；直接切换，避免 AnimatedContent 为每个经过的 Tab
        // 同时保留进出页面并反复重定向动画。点按切页继续使用原有入场动效。
        if (tabChangeFromSwipe) AppPageTransitionKind.NONE else AppPageTransitionKind.TAB_SELECTION
    else -> AppPageTransitionKind.SECONDARY_PAGE
}
