package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 所有显示主 Tab 栏的页面都在滚动视口外预留导航栏高度。 */
internal fun pageContentBottomInset(route: AppRoute, tabBarHeight: Dp): Dp =
    if (route.chromeVisible) tabBarHeight + 8.dp else 0.dp

/** 页面切换期间离场内容仍会参与组合，因此也必须保留导航栏所需空间。 */
internal fun pageTransitionBottomInset(from: AppRoute, to: AppRoute, tabBarHeight: Dp): Dp =
    maxOf(pageContentBottomInset(from, tabBarHeight), pageContentBottomInset(to, tabBarHeight))
