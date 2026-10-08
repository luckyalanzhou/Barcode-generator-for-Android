package com.luckyalanzhou.barcodegenerator.ui.app

internal enum class AppPageTransitionKind {
    NONE,
    TAB_SWIPE,
    TAB_SELECTION,
    SECONDARY_PAGE,
}

internal fun appPageTransitionKind(
    initialRoute: AppRoute,
    targetRoute: AppRoute,
    tabChangeFromSwipe: Boolean,
): AppPageTransitionKind = when {
    initialRoute == targetRoute -> AppPageTransitionKind.NONE
    initialRoute == AppRoute.Results || targetRoute == AppRoute.Results -> AppPageTransitionKind.NONE
    initialRoute.mainTabIndex != null && targetRoute.mainTabIndex != null ->
        if (tabChangeFromSwipe) AppPageTransitionKind.TAB_SWIPE else AppPageTransitionKind.TAB_SELECTION
    else -> AppPageTransitionKind.SECONDARY_PAGE
}
