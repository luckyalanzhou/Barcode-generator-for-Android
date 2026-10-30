package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_UPLOAD_PAUSE_GRACE_MS
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LAN_SHARE_UPLOAD_STARTUP_GRACE_MS
import com.luckyalanzhou.barcodegenerator.data.network.protocol.LanShareLimits
import com.luckyalanzhou.barcodegenerator.data.network.server.lanShareUploadTotalTimeoutMillis
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareUploadDeadlineTest {
    @Test
    fun timeoutIncludesStartupAndPauseGraceAndScalesWithFileSize() {
        val graceMillis = LAN_SHARE_UPLOAD_STARTUP_GRACE_MS + LAN_SHARE_UPLOAD_PAUSE_GRACE_MS

        assertEquals(graceMillis, lanShareUploadTotalTimeoutMillis(0L))
        assertEquals(graceMillis + 1L, lanShareUploadTotalTimeoutMillis(1L))
        assertEquals(
            graceMillis + TimeUnit.SECONDS.toMillis(1),
            lanShareUploadTotalTimeoutMillis(LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND),
        )
        assertEquals(
            graceMillis + TimeUnit.SECONDS.toMillis(10),
            lanShareUploadTotalTimeoutMillis(10L * LAN_SHARE_UPLOAD_MIN_BYTES_PER_SECOND),
        )
    }

    @Test
    fun maximumFileDeadlineMatchesTheTenGiBTransferBudgetAndGrace() {
        val timeoutMillis = lanShareUploadTotalTimeoutMillis(LanShareLimits.MAX_FILE_BYTES)

        assertEquals(
            TimeUnit.HOURS.toMillis(2) + TimeUnit.MINUTES.toMillis(53) + TimeUnit.SECONDS.toMillis(10),
            timeoutMillis,
        )
    }
}
