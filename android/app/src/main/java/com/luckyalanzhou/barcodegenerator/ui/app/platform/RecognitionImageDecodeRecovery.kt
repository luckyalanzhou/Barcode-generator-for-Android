package com.luckyalanzhou.barcodegenerator.ui.app.platform

import kotlinx.coroutines.CancellationException

/** 图片解码普通异常可降级为无图；失败时释放部分结果，取消和致命错误继续传播。 */
internal fun <T : Any> captureRecoverableRecognitionImageDecode(
    operation: () -> T?,
    cleanup: () -> Unit,
    onFailure: (Exception) -> Unit,
): T? = try {
    operation()
} catch (cancelled: CancellationException) {
    cleanupAfterDecodeFailure(cleanup, cancelled)
    throw cancelled
} catch (error: Exception) {
    cleanupAfterDecodeFailure(cleanup, error)
    onFailure(error)
    null
} catch (error: Throwable) {
    cleanupAfterDecodeFailure(cleanup, error)
    throw error
}

private fun cleanupAfterDecodeFailure(cleanup: () -> Unit, cause: Throwable) {
    try {
        cleanup()
    } catch (cleanupError: Exception) {
        if (cleanupError !== cause) cause.addSuppressed(cleanupError)
    }
}
