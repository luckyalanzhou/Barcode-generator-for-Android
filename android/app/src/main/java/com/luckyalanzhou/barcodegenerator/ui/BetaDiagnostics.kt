package com.luckyalanzhou.barcodegenerator

/** Beta 专用入口；页面本身已经由 Compose 根路由直接绘制。 */
internal fun MainActivity.showFeatureSelfTestDialog() {
    if (BuildConfig.DEBUG_LOG_EXPORT) {
        viewModel.navigateTo("betaTestCenter")
    }
}
