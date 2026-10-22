package com.luckyalanzhou.barcodegenerator.presentation.lanshare

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
            if (rollbackError !== error) error.addSuppressed(rollbackError)
        }
        throw error
    }
}
