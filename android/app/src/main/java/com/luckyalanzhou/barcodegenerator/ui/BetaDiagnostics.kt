package com.luckyalanzhou.barcodegenerator

/** Beta 专用入口；页面本身已经由 Compose 根路由直接绘制。 */
internal fun MainActivity.showFeatureSelfTestDialog() {
    if (BuildConfig.DEBUG_LOG_EXPORT) {
        viewModel.navigateTo("betaTestCenter")
    }
}

/** 保留旧业务入口，但不再使用反射切换页面。 */
internal fun MainActivity.renderBetaTestCenterPage() {
    if (BuildConfig.DEBUG_LOG_EXPORT) {
        viewModel.navigateTo("betaTestCenter")
    }
}
