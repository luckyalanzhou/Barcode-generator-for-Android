package com.luckyalanzhou.barcodegenerator.ui.app.platform

import kotlinx.coroutines.CancellationException

/** 分享面板启动失败时清理暂存备份；仅普通异常转成用户可恢复的失败反馈。 */
internal fun launchFavoriteBackupChooser(
    launch: () -> Unit,
    cleanupArtifact: () -> Unit,
    onFailure: (Exception) -> Unit,
) {
    try {
        launch()
    } catch (cancelled: CancellationException) {
        cleanupFavoriteBackupAfterLaunchFailure(cleanupArtifact, cancelled)
        throw cancelled
    } catch (error: Exception) {
        cleanupFavoriteBackupAfterLaunchFailure(cleanupArtifact, error)
        onFailure(error)
    } catch (error: Throwable) {
        cleanupFavoriteBackupAfterLaunchFailure(cleanupArtifact, error)
        throw error
    }
}

private fun cleanupFavoriteBackupAfterLaunchFailure(cleanupArtifact: () -> Unit, cause: Throwable) {
    try {
        cleanupArtifact()
    } catch (cleanupError: Exception) {
        cause.addSuppressed(cleanupError)
    }
}
