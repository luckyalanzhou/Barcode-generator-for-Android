package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Reserve the actual navigation height once, outside the page's scrollable viewport. */
internal fun pageContentBottomInset(route: AppRoute, tabBarHeight: Dp): Dp = when (route) {
    AppRoute.History, AppRoute.Favorites, AppRoute.Settings -> tabBarHeight + 8.dp
    else -> 0.dp
}
