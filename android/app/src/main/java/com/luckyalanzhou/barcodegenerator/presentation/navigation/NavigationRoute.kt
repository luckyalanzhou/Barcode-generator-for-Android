package com.luckyalanzhou.barcodegenerator

/** Core navigation identity shared by state and coordinators. */
enum class NavigationRoute(
    val pageName: String,
    val title: String,
    val chromeVisible: Boolean,
    val mainTabIndex: Int?,
) {
    Generate("generate", "条码生成器", true, 0),
    History("history", "历史记录", true, 1),
    Favorites("favorites", "收藏", true, 2),
    Settings("settings", "设置", true, 3),
    Results("results", "", false, null),
    LanShare("lanShare", "局域网分享", false, null),
    ;

    companion object {
        fun fromPage(pageName: String): NavigationRoute =
            entries.firstOrNull { it.pageName == pageName } ?: Generate
    }
}
