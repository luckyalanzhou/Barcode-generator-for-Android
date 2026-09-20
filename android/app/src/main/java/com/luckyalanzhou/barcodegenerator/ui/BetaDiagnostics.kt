package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.MainActivity

import com.luckyalanzhou.barcodegenerator.ui.AppRoute
/** Beta 专用入口；页面本身已经由 Compose 根路由直接绘制。 */
internal fun MainActivity.showFeatureSelfTestDialog() {
    if (BuildConfig.DEBUG_LOG_EXPORT) {
        composeAppShellActions().navigateTo(AppRoute.BetaTestCenter)
    }
}
