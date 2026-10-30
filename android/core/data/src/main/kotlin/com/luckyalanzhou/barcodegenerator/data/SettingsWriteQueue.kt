package com.luckyalanzhou.barcodegenerator.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** 设置按提交顺序保存；失败保留在 Deferred 中，不以未捕获 launch 异常终止应用。 */
internal class SettingsWriteQueue(private val scope: CoroutineScope) {
    private val lock = Any()
    private var tail: Deferred<Unit>? = null

    fun enqueue(write: suspend () -> Unit): Deferred<Unit> = synchronized(lock) {
        val previous = tail
        scope.async {
            // join 只保证顺序：一次失败不能阻止后续重试。
            previous?.join()
            write()
        }.also { next ->
            tail = next
            next.invokeOnCompletion {
                synchronized(lock) { if (tail === next) tail = null }
            }
        }
    }
}
