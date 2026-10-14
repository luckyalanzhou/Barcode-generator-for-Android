package com.luckyalanzhou.barcodegenerator.domain

import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 等待真正提交；普通 join 不会报告写入失败。取消等待不取消进程内的保存任务。 */
suspend fun Job.awaitSettingsCommit() = suspendCancellableCoroutine<Unit> { continuation ->
    val handle = invokeOnCompletion { error ->
        if (error == null) continuation.resume(Unit) else continuation.resumeWithException(error)
    }
    continuation.invokeOnCancellation { handle.dispose() }
}
