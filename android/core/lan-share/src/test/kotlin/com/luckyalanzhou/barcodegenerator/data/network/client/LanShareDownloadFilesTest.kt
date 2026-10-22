package com.luckyalanzhou.barcodegenerator.data.network.client

import java.io.File
import java.io.IOException
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LanShareDownloadFilesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun replacesDestinationAfterDownloadHasCompleted() {
        val destination = temporaryFolder.newFile("download.bin").apply { writeText("old") }
        val temporary = temporaryFolder.newFile("download.part").apply { writeText("new") }

        replaceDownloadedFile(temporary, destination)

        assertEquals("new", destination.readText())
        assertFalse(temporary.exists())
    }

    @Test
    fun failedReplacementKeepsExistingDestinationAndStagedDownload() {
        val destination = temporaryFolder.newFile("download.bin").apply { writeText("old") }
        val temporary = temporaryFolder.newFile("download.part").apply { writeText("new") }

        val error = runCatching {
            replaceDownloadedFile(temporary, destination) { _, _ -> throw IOException("simulated move failure") }
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals("old", destination.readText())
        assertEquals("new", temporary.readText())
    }

    @Test
    fun stagingFileUsesDestinationDirectory() {
        val destinationDirectory = temporaryFolder.newFolder("destination")
        val destination = File(destinationDirectory, "download.bin")

        val temporary = createDownloadTempFile(destination)

        assertEquals(destinationDirectory.canonicalFile, temporary.parentFile?.canonicalFile)
        assertTrue(temporary.name.endsWith(".part"))
    }
}
