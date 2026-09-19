package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*

/** 调试日志导出门面；实现由 Beta/Official 源集在编译期选择。 */
internal fun MainActivity.shareDebugLog() {
    if (BuildConfig.DEBUG_LOG_EXPORT) shareDebugLogImpl()
}
