package com.luckyalanzhou.barcodegenerator.domain

/** 跨层日志边界；Data 层不直接依赖 UI 或具体日志实现。 */
fun interface AppLogger {
    fun record(tag: String, message: String, error: Throwable?)
}
