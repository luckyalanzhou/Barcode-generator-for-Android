package com.luckyalanzhou.barcodegenerator

/** 正式版只保留空门面；Beta 测试中心实现位于 beta 源集。 */
internal fun MainActivity.showFeatureSelfTestDialog() {
    if (BuildConfig.DEBUG_LOG_EXPORT) runCatching {
        Class.forName("com.luckyalanzhou.barcodegenerator.BetaFeatureSelfTestKt")
            .getMethod("showBetaFeatureSelfTest", MainActivity::class.java)
            .invoke(null, this)
    }
}
