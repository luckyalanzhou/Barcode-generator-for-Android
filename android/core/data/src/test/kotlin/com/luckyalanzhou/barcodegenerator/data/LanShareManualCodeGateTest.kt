package com.luckyalanzhou.barcodegenerator.data

import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareManualCodeGate
import org.junit.Assert.assertEquals
import org.junit.Test

class LanShareManualCodeGateTest {
    @Test
    fun acceptsCaseInsensitiveCodeAndLimitsRepeatedGuessesPerAddress() {
        var time = 1_000L
        val gate = LanShareManualCodeGate("A7B2") { time }
        repeat(5) {
            assertEquals(LanShareManualCodeGate.Result.INVALID, gate.check("192.168.1.3", "X1Y2"))
        }
        assertEquals(LanShareManualCodeGate.Result.TOO_MANY_ATTEMPTS, gate.check("192.168.1.3", "a7b2"))
        assertEquals(LanShareManualCodeGate.Result.ACCEPTED, gate.check("192.168.1.4", "a7b2"))
        time += 5 * 60 * 1000L
        assertEquals(LanShareManualCodeGate.Result.ACCEPTED, gate.check("192.168.1.3", "A7B2"))
    }
}
