package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 生成页保留独立操作区，其余主 Tab 列表可以绘制到底栏后方。 */
internal fun pageContentBottomInset(route: AppRoute, tabBarHeight: Dp): Dp =
    if (route == AppRoute.Generate) tabBarHeight + 8.dp else 0.dp

internal val AppRoute.drawsBehindSystemNavigation: Boolean
    get() = this == AppRoute.History || this == AppRoute.Favorites || this == AppRoute.Settings

internal fun pageListBottomPadding(route: AppRoute, tabBarHeight: Dp, bottomChromeInset: Dp = 0.dp): Dp =
    if (route.drawsBehindSystemNavigation) tabBarHeight + bottomChromeInset + 8.dp else 8.dp

/** 页面切换期间离场内容仍会参与组合，因此也必须保留导航栏所需空间。 */
internal fun pageTransitionBottomInset(from: AppRoute, to: AppRoute, tabBarHeight: Dp): Dp =
    maxOf(pageContentBottomInset(from, tabBarHeight), pageContentBottomInset(to, tabBarHeight))
