package com.luckyalanzhou.barcodegenerator.data

import android.content.ContextWrapper
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalBarcodeFileStoreDeferredCleanupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun constructingCacheStoreDefersLegacyCleanupUntilCacheAccess() {
        val filesDirectory = temporaryFolder.newFolder("files")
        val cacheDirectory = temporaryFolder.newFolder("cache")
        val legacyImage = File(filesDirectory, "barcode-data/images/legacy.png").apply {
            parentFile?.mkdirs()
            writeText("recreatable legacy cache")
        }
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir(): File = filesDirectory
            override fun getCacheDir(): File = cacheDirectory
        }

        val store = LocalBarcodeFileStore(context)

        assertTrue("constructing the app-scoped cache must not scan/delete legacy files", legacyImage.exists())

        store.readImage("a".repeat(64))

        assertFalse("the legacy cache is reclaimed on the first cache access", legacyImage.exists())
    }
}
