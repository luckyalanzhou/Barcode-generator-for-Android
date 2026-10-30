package com.luckyalanzhou.barcodegenerator.ui.feature.results

import java.util.concurrent.atomic.AtomicBoolean

/** 准备结果页导出内容时显示的界面状态。 */
internal enum class ResultExportAction { Share, Save }

/** 异步导出期间保持互斥；同步启动失败和重复完成回调都会安全释放状态。 */
internal fun startExclusiveResultExport(
    currentAction: ResultExportAction?,
    requestedAction: ResultExportAction,
    updateAction: (ResultExportAction?) -> Unit,
    start: (finish: () -> Unit) -> Unit,
): Boolean {
    if (currentAction != null) return false
    updateAction(requestedAction)

    val completed = AtomicBoolean(false)
    val finish = {
        if (completed.compareAndSet(false, true)) updateAction(null)
    }
    return try {
        start(finish)
        true
    } catch (error: Throwable) {
        finish()
        throw error
    }
}
