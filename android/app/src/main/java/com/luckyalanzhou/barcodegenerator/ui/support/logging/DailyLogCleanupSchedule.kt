package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.time.LocalDate

/** 同一自然日只扫一次过期日志；日期变化后允许再次清理。 */
internal class DailyLogCleanupSchedule {
    private var lastCleanedDate: LocalDate? = null

    @Synchronized
    fun needsCleanup(date: LocalDate): Boolean = lastCleanedDate != date

    @Synchronized
    fun markCleaned(date: LocalDate) {
        lastCleanedDate = date
    }
}
