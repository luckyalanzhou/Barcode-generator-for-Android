package com.luckyalanzhou.barcodegenerator.infrastructure.barcode

import kotlinx.coroutines.CancellationException

/** 普通条码编码异常可降级为空图；取消和 JVM 致命错误必须继续传播。 */
internal fun <T : Any> captureRecoverableBarcodeEncoding(
    operation: () -> T,
    onFailure: (Exception) -> Unit,
): T? = try {
    operation()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    onFailure(error)
    null
}
