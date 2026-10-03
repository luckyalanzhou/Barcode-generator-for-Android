package com.luckyalanzhou.barcodegenerator.ui.feature.history

import com.luckyalanzhou.barcodegenerator.domain.CodeItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryBatchPreviewTest {
    @Test fun longTextIsNotTruncatedBeforeLayout() {
        val value = "123456789012345678901234567890"
        assertEquals(value, historyBatchPreview(listOf(CodeItem(1L, value, "Code 128-B"))))
    }

    @Test fun unicodeAndMultilineSourceAreKeptIntact() {
        val value = "产品📦编号：测试字符\nhttps://example.com/item?id=12345"
        val item = CodeItem(1L, value, "QR Code")
        val before = item.copy()
        assertEquals(value, historyBatchPreview(listOf(item)))
        assertEquals(before, item)
    }

    @Test fun onlyTheFirstItemIsPreviewedWithoutChangingBatchOrder() {
        val batch = listOf(CodeItem(2L, "首条内容", "Code 128-B"), CodeItem(1L, "第二条", "Code 128-B"))
        val before = batch.map { it.copy() }
        assertEquals("首条内容", historyBatchPreview(batch))
        assertEquals(before, batch)
    }

    @Test fun emptyOrBlankContentHasAnExplicitFallback() {
        assertEquals("无条码内容", historyBatchPreview(emptyList()))
        assertEquals("无条码内容", historyBatchPreview(listOf(CodeItem(1L, "   ", "QR Code"))))
    }
}
