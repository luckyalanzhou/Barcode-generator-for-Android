package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import org.junit.Assert.*
import org.junit.Test

class LanSendButtonTest {
    @Test fun emptyOrWhitespaceMessageWithoutAttachmentIsDisabled() {
        assertFalse(canSendLanContent("", null))
        assertFalse(canSendLanContent(" \n\t", null))
    }
    @Test fun textOrSelectedAttachmentCanBeSent() {
        assertTrue(canSendLanContent("hello", null))
        assertTrue(canSendLanContent("", "image.png"))
        assertTrue(canSendLanContent("caption", "image.png"))
    }
}
