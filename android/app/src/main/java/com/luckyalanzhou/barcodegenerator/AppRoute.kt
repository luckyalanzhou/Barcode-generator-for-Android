package com.luckyalanzhou.barcodegenerator

/**
 * 应用页面的唯一路由定义。
 *
 * 页面名称、标题、是否显示底部 Tab 以及对应的主 Tab 索引集中在这里，
 * 避免 Activity、Compose 壳和 render() 各自维护一份字符串判断。
 */
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
