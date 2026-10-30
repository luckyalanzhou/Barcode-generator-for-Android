package com.luckyalanzhou.barcodegenerator.ui.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 将后台生成的资源安全交回调用方；调用方取消时释放尚未接收的资源。 */
internal suspend fun <T : Any> handoffCancellableResource(
    prepare: suspend () -> T?,
    release: (T) -> Unit,
): T? {
    currentCoroutineContext().ensureActive()
    val resource = withContext(NonCancellable) { prepare() }
    return try {
        currentCoroutineContext().ensureActive()
        resource
    } catch (cancelled: CancellationException) {
        try {
            resource?.let(release)
        } catch (releaseFailure: Throwable) {
            cancelled.addSuppressed(releaseFailure)
        }
        throw cancelled
    }
}
