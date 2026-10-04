package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Reserve the actual navigation height once, outside the page's scrollable viewport. */
internal fun pageContentBottomInset(route: AppRoute, tabBarHeight: Dp): Dp = when (route) {
    AppRoute.History, AppRoute.Favorites, AppRoute.Settings -> tabBarHeight + 8.dp
    else -> 0.dp
}

/** Outgoing content remains composed during a transition and must keep its navigation clearance. */
internal fun pageTransitionBottomInset(from: AppRoute, to: AppRoute, tabBarHeight: Dp): Dp =
    maxOf(pageContentBottomInset(from, tabBarHeight), pageContentBottomInset(to, tabBarHeight))
