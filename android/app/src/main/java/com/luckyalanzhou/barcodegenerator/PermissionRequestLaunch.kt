package com.luckyalanzhou.barcodegenerator

/** 统一处理系统权限 launcher 的同步启动失败，确保调用方可以释放待处理请求。 */
internal fun launchPermissionRequestSafely(
    launch: () -> Unit,
    onFailure: (Exception) -> Unit,
): Boolean = try {
    launch()
    true
} catch (error: Exception) {
    onFailure(error)
    false
}
