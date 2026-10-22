package com.luckyalanzhou.barcodegenerator.ui.support.logging

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LogExportFileReservationTest {
    @Test
    fun repeatedTimestampReservesSeparateFilesWithoutOverwritingExistingExport() {
        val directory = Files.createTempDirectory("log-export-reservation-").toFile()
        try {
            val first = createUniqueLogExportFile(directory, "barcode-generator-debug-20261022-120000.log")
            first.writeText("first snapshot")

            val second = createUniqueLogExportFile(directory, "barcode-generator-debug-20261022-120000.log")
            second.writeText("second snapshot")

            assertNotEquals(first.absolutePath, second.absolutePath)
            assertEquals("first snapshot", first.readText())
            assertEquals("second snapshot", second.readText())
            assertEquals("barcode-generator-debug-20261022-120000.log", first.name)
            assertEquals("barcode-generator-debug-20261022-120000-1.log", second.name)
        } finally {
            directory.deleteRecursively()
        }
    }
}
