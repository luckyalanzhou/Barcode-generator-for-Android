package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.ui.dialogs.readFavoritesBackupBounded
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class FavoriteBackupInputTest {
    @Test
    fun acceptsInputExactlyAtLimit() {
        val expected = byteArrayOf(1, 2, 3, 4)

        assertArrayEquals(expected, readFavoritesBackupBounded(ByteArrayInputStream(expected), maxBytes = 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownLengthStreamAsSoonAsItExceedsLimit() {
        readFavoritesBackupBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)), maxBytes = 4)
    }
}
