package com.luckyalanzhou.barcodegenerator

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDownloadLifecyclePolicyTest {
    @Test
    fun configurationRecreationDoesNotCancelViewModelOwnedDownload() {
        var cancellationCount = 0
        cancelUpdateDownloadIfActivityFinishes(isChangingConfigurations = true) { cancellationCount++ }

        assertEquals(0, cancellationCount)
    }

    @Test
    fun finalActivityDestructionCancelsViewModelOwnedDownload() {
        var cancellationCount = 0
        cancelUpdateDownloadIfActivityFinishes(isChangingConfigurations = false) { cancellationCount++ }

        assertEquals(1, cancellationCount)
    }
}
