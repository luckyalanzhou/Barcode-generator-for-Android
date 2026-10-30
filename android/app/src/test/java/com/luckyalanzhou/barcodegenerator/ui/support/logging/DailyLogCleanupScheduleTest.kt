package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyLogCleanupScheduleTest {
    @Test
    fun allowsOneRetentionSweepPerDateAndRechecksAfterDayChanges() {
        val schedule = DailyLogCleanupSchedule()
        val firstDay = LocalDate.of(2026, 10, 22)
        val nextDay = firstDay.plusDays(1)

        assertTrue(schedule.needsCleanup(firstDay))
        schedule.markCleaned(firstDay)
        assertFalse(schedule.needsCleanup(firstDay))
        assertTrue(schedule.needsCleanup(nextDay))
        schedule.markCleaned(nextDay)
        assertFalse(schedule.needsCleanup(nextDay))
        assertTrue(schedule.needsCleanup(firstDay))
    }
}
