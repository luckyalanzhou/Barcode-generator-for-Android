package com.luckyalanzhou.barcodegenerator.ui.app.lanshare

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CancellationException

class LanShareAttachmentNameTest {
    @Test
    fun usesProviderDisplayNameWhenAvailable() {
        assertEquals("photo.jpg", resolveLanShareAttachmentName("附件") { "photo.jpg" })
    }

    @Test
    fun usesGenericNameWhenProviderHasNoDisplayName() {
        assertEquals("附件", resolveLanShareAttachmentName("附件") { null })
    }

    @Test
    fun usesGenericNameWhenProviderQueryThrowsOrdinaryException() {
        assertEquals("附件", resolveLanShareAttachmentName("附件") { error("provider unavailable") })
    }

    @Test(expected = AssertionError::class)
    fun propagatesFatalErrorsFromProviderQuery() {
        resolveLanShareAttachmentName("附件") { throw AssertionError("fatal") }
    }

    @Test(expected = CancellationException::class)
    fun propagatesCancellationFromProviderQuery() {
        resolveLanShareAttachmentName("附件") { throw CancellationException("cancelled") }
    }
}
