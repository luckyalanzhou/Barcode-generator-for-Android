package com.luckyalanzhou.barcodegenerator.presentation.camera

/** 只允许最近一次完成的条码扫描提交结果。 */
internal class BarcodeDecodeGeneration {
    private var latestRequestId = 0L

    @Synchronized
    fun begin(): Long {
        latestRequestId = Math.incrementExact(latestRequestId)
        return latestRequestId
    }

    @Synchronized
    fun isCurrent(requestId: Long): Boolean = requestId == latestRequestId
}
