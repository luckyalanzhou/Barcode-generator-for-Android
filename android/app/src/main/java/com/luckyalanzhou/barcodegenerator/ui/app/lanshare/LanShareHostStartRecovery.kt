package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import kotlinx.coroutines.CancellationException

/** 建房失败时先关闭可能已部分启动的会话；只有普通异常转为可恢复失败。 */
internal fun captureRecoverableLanShareHostStart(
    startHost: () -> Unit,
    rollback: () -> Unit,
    onFailure: (Exception) -> Unit,
) {
    try {
        startHost()
    } catch (cancelled: CancellationException) {
        rollbackLanShareHostStart(rollback, cancelled)
        throw cancelled
    } catch (error: Exception) {
        rollbackLanShareHostStart(rollback, error)
        onFailure(error)
    } catch (error: Throwable) {
        rollbackLanShareHostStart(rollback, error)
        throw error
    }
}

private fun rollbackLanShareHostStart(rollback: () -> Unit, cause: Throwable) {
    try {
        rollback()
    } catch (rollbackError: Exception) {
        cause.addSuppressed(rollbackError)
    }
}
