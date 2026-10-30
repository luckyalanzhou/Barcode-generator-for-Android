package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import java.util.concurrent.CancellationException

/** 在打开 SAF 目标前再次确认下载协程和会话仍有效。 */
internal inline fun <T> withActiveLanShareDownloadDestination(
    ensureActive: () -> Unit,
    isSessionCurrent: () -> Boolean,
    action: () -> T,
): T {
    ensureActive()
    if (!isSessionCurrent()) throw CancellationException("局域网会话已关闭")
    return action()
}

/** 下载或写入 SAF 新建文档失败时回滚该文档，并保留原始传输异常。 */
internal inline fun <T> withLanShareDownloadRollback(
    rollback: () -> Unit,
    action: () -> T,
): T {
    try {
        return action()
    } catch (error: Throwable) {
        try {
            rollback()
        } catch (rollbackError: Throwable) {
            if (rollbackError !== error) {
                if (rollbackError is Error && error !is Error) {
                    rollbackError.addSuppressed(error)
                    throw rollbackError
                }
                error.addSuppressed(rollbackError)
            }
        }
        throw error
    }
}
