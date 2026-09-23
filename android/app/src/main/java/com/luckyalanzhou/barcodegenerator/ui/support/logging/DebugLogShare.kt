package com.luckyalanzhou.barcodegenerator.ui.support.logging

import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.MainActivity
import com.luckyalanzhou.barcodegenerator.shareDebugLogImpl

/** 调试日志导出门面；实现由 Beta/Official 源集在编译期选择。 */
internal fun MainActivity.shareDebugLog() {
    if (BuildConfig.DEBUG_LOG_EXPORT) shareDebugLogImpl()
}
