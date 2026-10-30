package com.luckyalanzhou.barcodegenerator.data.platform

import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import kotlinx.coroutines.CancellationException

/** 诊断日志不能覆盖数据层结果；仅传播取消、中断和 JVM 致命错误。 */
internal fun AppLogger.recordSafely(
    tag: String,
    message: String,
    diagnosticError: Throwable? = null,
    primaryError: Throwable? = null,
) {
    try {
        record(tag, message, diagnosticError)
    } catch (diagnosticCancellation: CancellationException) {
        val propagated = primaryError as? CancellationException ?: diagnosticCancellation
        if (primaryError != null && primaryError !== propagated) propagated.addSuppressed(primaryError)
        if (diagnosticCancellation !== propagated) propagated.addSuppressed(diagnosticCancellation)
        throw propagated
    } catch (diagnosticInterruption: InterruptedException) {
        Thread.currentThread().interrupt()
        val propagated = when (primaryError) {
            is CancellationException, is InterruptedException -> primaryError
            else -> diagnosticInterruption
        }
        if (primaryError != null && primaryError !== propagated) propagated.addSuppressed(primaryError)
        if (diagnosticInterruption !== propagated) propagated.addSuppressed(diagnosticInterruption)
        throw propagated
    } catch (diagnosticFailure: Exception) {
        if (primaryError != null && diagnosticFailure !== primaryError) {
            primaryError.addSuppressed(diagnosticFailure)
        }
    }
}
