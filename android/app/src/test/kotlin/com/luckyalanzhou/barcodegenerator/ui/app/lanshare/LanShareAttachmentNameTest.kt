package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CancellationException

class LanShareAttachmentNameTest {
    @Test
    fun usesProviderDisplayNameWhenAvailable() {
        assertEquals("photo.jpg", resolveLanShareAttachmentName { "photo.jpg" })
    }

    @Test
    fun usesGenericNameWhenProviderHasNoDisplayName() {
        assertEquals("附件", resolveLanShareAttachmentName { null })
    }

    @Test
    fun usesGenericNameWhenProviderQueryThrowsOrdinaryException() {
        assertEquals("附件", resolveLanShareAttachmentName { error("provider unavailable") })
    }

    @Test(expected = AssertionError::class)
    fun propagatesFatalErrorsFromProviderQuery() {
        resolveLanShareAttachmentName { throw AssertionError("fatal") }
    }

    @Test(expected = CancellationException::class)
    fun propagatesCancellationFromProviderQuery() {
        resolveLanShareAttachmentName { throw CancellationException("cancelled") }
    }
}
