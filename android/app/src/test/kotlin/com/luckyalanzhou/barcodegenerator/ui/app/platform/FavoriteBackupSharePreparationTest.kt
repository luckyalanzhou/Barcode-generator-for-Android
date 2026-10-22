package com.luckyalanzhou.barcodegenerator.ui.app.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FavoriteBackupSharePreparationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun cancellationRemovesPartialBackupAndPropagates() = runBlocking {
        val directory = temporaryFolder.newFolder("cancelled-share")
        val cancellation = CancellationException("activity destroyed")
        var artifact: java.io.File? = null

        val thrown = try {
            prepareFavoriteBackupShareArtifact(directory, "partial-backup.zip") { file ->
                artifact = file
                file.writeText("partial ZIP")
                throw cancellation
            }
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(cancellation, thrown)
        assertNotNull(artifact)
        assertFalse("cancelled share must not leave a partial backup in cache", artifact!!.exists())
    }

    @Test
    fun fatalErrorRemovesPartialBackupAndPropagates() = runBlocking {
        val directory = temporaryFolder.newFolder("fatal-share")
        val fatalError = AssertionError("fatal preparation error")
        var artifact: java.io.File? = null

        val thrown = try {
            prepareFavoriteBackupShareArtifact(directory, "partial-backup.zip") { file ->
                artifact = file
                file.writeText("partial ZIP")
                throw fatalError
            }
            null
        } catch (error: Throwable) {
            error
        }

        assertSame(fatalError, thrown)
        assertNotNull(artifact)
        assertFalse("fatal share preparation must not leave a partial backup in cache", artifact!!.exists())
    }

    @Test
    fun repeatedExportNameReservesDifferentFilesWithoutOverwriting() = runBlocking {
        val directory = temporaryFolder.newFolder("repeated-share")
        val first = prepareFavoriteBackupShareArtifact(directory, "favorites-20261022-091234.zip") { file ->
            file.writeText("first export")
            "first"
        }.getOrThrow()
        val second = prepareFavoriteBackupShareArtifact(directory, "favorites-20261022-091234.zip") { file ->
            file.writeText("second export")
            "second"
        }.getOrThrow()

        assertNotEquals(first.first.absolutePath, second.first.absolutePath)
        assertEquals("first export", first.first.readText())
        assertEquals("second export", second.first.readText())
    }
}
