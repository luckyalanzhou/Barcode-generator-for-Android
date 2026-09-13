package com.luckyalanzhou.barcodegenerator

/** 调试日志导出门面；实际实现仅由 Beta 源集提供。 */
internal fun MainActivity.shareDebugLog() {
    if (BuildConfig.DEBUG_LOG_EXPORT) runCatching {
        Class.forName("com.luckyalanzhou.barcodegenerator.BetaDebugLogShareKt")
            .getMethod("shareBetaDebugLog", MainActivity::class.java)
            .invoke(null, this)
    }
}
