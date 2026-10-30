package com.luckyalanzhou.barcodegenerator.domain

import java.util.concurrent.CancellationException

/** 会话关闭时通知同步下载适配器断开当前连接，并阻止后续写入。 */
class LanShareDownloadCancellation {
    private val lock = Any()
    private var cancelled = false
    private var cancellationAction: (() -> Unit)? = null

    val isCancelled: Boolean
        get() = synchronized(lock) { cancelled }

    fun cancel() {
        val action = synchronized(lock) {
            if (cancelled) {
                null
            } else {
                cancelled = true
                cancellationAction.also { cancellationAction = null }
            }
        }
        action?.invoke()
    }

    fun attach(action: () -> Unit) {
        val invokeImmediately = synchronized(lock) {
            if (cancelled) {
                true
            } else {
                check(cancellationAction == null || cancellationAction === action) {
                    "下载取消信号已绑定其他资源"
                }
                cancellationAction = action
                false
            }
        }
        if (invokeImmediately) action()
    }

    fun detach(action: () -> Unit) {
        synchronized(lock) {
            if (cancellationAction === action) cancellationAction = null
        }
    }

    fun ensureActive() {
        if (isCancelled) throw CancellationException("局域网下载已取消")
    }
}
