package com.luckyalanzhou.barcodegenerator.presentation.lanshare

import com.luckyalanzhou.barcodegenerator.di.ApplicationScope
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.presentation.shared.recordSafely
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 跨 ViewModel 串行停止共享服务，避免旧页面的迟到清理关闭新页面刚启动的房间。 */
@Singleton
class LanShareGatewayStopCoordinator @Inject constructor(
    private val lanShareGateway: LanShareGateway,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val logger: AppLogger,
) {
    private val lock = Any()
    private var pendingStop: Job? = null
    private var ownerGeneration = 0L

    /** 为新 ViewModel 取得服务控制权，旧实例之后发出的清理请求将被丢弃。 */
    fun acquireOwner(): Long = synchronized(lock) {
        ++ownerGeneration
    }

    fun scheduleStop(owner: Long, clearSharedFiles: Boolean) {
        synchronized(lock) {
            if (owner != ownerGeneration) return@synchronized
            val previousStop = pendingStop
            applicationScope.launch {
                previousStop?.join()
                try {
                    lanShareGateway.stop(clearSharedFiles = clearSharedFiles)
                } catch (error: Exception) {
                    logger.recordSafely("lan_ui", "gateway stop failed", error, error)
                }
            }.also { pendingStop = it }
        }
    }

    suspend fun awaitPendingStop() {
        while (true) {
            val stopJob = synchronized(lock) { pendingStop } ?: return
            stopJob.join()
            if (synchronized(lock) { pendingStop === stopJob }) return
        }
    }
}
