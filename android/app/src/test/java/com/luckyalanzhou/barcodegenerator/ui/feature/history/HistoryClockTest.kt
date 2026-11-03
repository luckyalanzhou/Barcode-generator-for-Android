package com.luckyalanzhou.barcodegenerator.ui.feature.history

import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryClockTest {
    @Test
    fun refreshDelayEndsAtTheNextLocalMidnight() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = GregorianCalendar(utc).apply {
            set(2026, GregorianCalendar.NOVEMBER, 2, 23, 59, 30)
            set(GregorianCalendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals(30_000L, millisUntilNextHistoryDayChange(now, utc))
    }

    @Test
    fun refreshDelayNeverRoundsPastMidnight() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = GregorianCalendar(utc).apply {
            set(2026, GregorianCalendar.NOVEMBER, 2, 23, 59, 59)
            set(GregorianCalendar.MILLISECOND, 999)
        }.timeInMillis

        assertEquals(1L, millisUntilNextHistoryDayChange(now, utc))
    }
}
