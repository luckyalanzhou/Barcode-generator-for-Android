package com.luckyalanzhou.barcodegenerator.ui.app.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** 写入失败时回滚本次新建的系统文档，同时保留原始失败原因。 */
internal fun <T> writeCreatedDocumentWithRollback(
    write: () -> T,
    deleteCreatedDocument: () -> Boolean,
): T {
    try {
        return write()
    } catch (failure: Throwable) {
        rollbackCreatedDocumentAndThrow(failure, deleteCreatedDocument)
    }
}

/** 写入逻辑可挂起时使用；失败或取消后同步删除刚创建的系统文档。 */
internal suspend fun <T> writeCreatedDocumentWithRollbackSuspending(
    write: suspend () -> T,
    deleteCreatedDocument: () -> Boolean,
): T {
    try {
        return write()
    } catch (failure: Throwable) {
        rollbackCreatedDocumentAndThrow(failure, deleteCreatedDocument)
    }
}

private fun rollbackCreatedDocumentAndThrow(
    failure: Throwable,
    deleteCreatedDocument: () -> Boolean,
): Nothing {
    try {
        if (!deleteCreatedDocument()) {
            failure.addSuppressed(IllegalStateException("无法删除未完成的系统文档"))
        }
    } catch (cleanupFailure: Throwable) {
        if (cleanupFailure !== failure) {
            if (cleanupFailure is Error && failure !is Error) {
                cleanupFailure.addSuppressed(failure)
                throw cleanupFailure
            }
            failure.addSuppressed(cleanupFailure)
        }
    }
    throw failure
}

/** 在 IO 线程完成系统文档清理，即使发起清理的协程已取消也继续执行。 */
internal suspend fun <T> runDocumentProviderCleanup(cleanup: () -> T): T =
    withContext(NonCancellable + Dispatchers.IO) { cleanup() }

/** 仅把普通提供器异常作为尽力清理失败返回；取消与致命错误必须继续传播。 */
internal suspend fun <T> runDocumentProviderCleanupResult(cleanup: () -> T): Result<T> {
    // 将异常对象作为值跨过调度器边界，避免协程栈恢复替换原始异常实例。
    val result = withContext(NonCancellable + Dispatchers.IO) {
        try {
            Result.success(cleanup())
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
    }
    val failure = result.exceptionOrNull() ?: return result
    return when (failure) {
        is CancellationException -> throw failure
        is Error -> throw failure
        else -> result
    }
}
