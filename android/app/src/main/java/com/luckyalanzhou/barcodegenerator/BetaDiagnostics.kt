package com.luckyalanzhou.barcodegenerator

/** 正式版只保留空门面；Beta 测试中心实现位于 beta 源集。 */
internal fun MainActivity.showFeatureSelfTestDialog() {
    if (BuildConfig.DEBUG_LOG_EXPORT) runCatching {
        page = "betaTestCenter"
        render()
    }
}

/** 正式版保持空实现；Beta 由同名源集函数绘制测试中心页面。 */
internal fun MainActivity.renderBetaTestCenterPage() {
    if (BuildConfig.DEBUG_LOG_EXPORT) runCatching {
        Class.forName("com.luckyalanzhou.barcodegenerator.BetaFeatureSelfTestKt")
            .getMethod("renderBetaTestCenterPageImpl", MainActivity::class.java)
            .invoke(null, this)
    }
}
