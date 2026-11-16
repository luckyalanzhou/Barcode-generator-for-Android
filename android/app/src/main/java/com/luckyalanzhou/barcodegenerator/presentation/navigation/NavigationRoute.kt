package com.luckyalanzhou.barcodegenerator.presentation.navigation

import com.luckyalanzhou.barcodegenerator.R

/** 供导航状态与协调器共享的核心页面标识。 */
enum class NavigationRoute(
    val pageName: String,
    @androidx.annotation.StringRes val titleResId: Int,
    val chromeVisible: Boolean,
    val mainTabIndex: Int?,
) {
    Generate("generate", R.string.title_generate, true, 0),
    History("history", R.string.title_history, true, 1),
    Favorites("favorites", R.string.title_favorites, true, 2),
    Settings("settings", R.string.title_settings, true, 3),
    AboutUs("aboutUs", R.string.title_about, false, null),
    Licenses("licenses", R.string.title_licenses, false, null),
    Results("results", R.string.title_results, false, null),
    LanShare("lanShare", R.string.title_lan_share, false, null),
    ;

    companion object {
        fun fromPage(pageName: String): NavigationRoute =
            entries.firstOrNull { it.pageName == pageName } ?: Generate
    }
}
