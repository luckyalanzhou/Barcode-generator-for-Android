package com.luckyalanzhou.barcodegenerator.ui

/** UI 层页面路由；页面标题、Chrome 和主 Tab 映射不进入业务 ViewModel。 */
enum class AppRoute(
    val pageName: String,
    val title: String,
    val chromeVisible: Boolean,
    val mainTabIndex: Int?,
) {
    Generate("generate", "条码生成器", true, 0),
    History("history", "历史记录", true, 1),
    Favorites("favorites", "收藏", true, 2),
    Settings("settings", "设置", true, 3),
    FavoriteDetail("favoriteDetail", "收藏", false, null),
    Results("results", "", false, null),
    LanShare("lanShare", "局域网分享", false, null),
    BetaTestCenter("betaTestCenter", "Beta 测试中心", false, null),
    ;

    companion object {
        fun fromPage(pageName: String): AppRoute =
            entries.firstOrNull { it.pageName == pageName } ?: Generate
    }
}
