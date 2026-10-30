package com.luckyalanzhou.barcodegenerator.data.preview

import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LanShareWebImagePreviewCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun doesNotPublishPreviewWhenExistingCacheCannotBeEvicted() {
        val cacheDirectory = temporaryFolder.newFolder("previews")
        val undeletableEntry = object : File(cacheDirectory, "existing.preview.jpg") {
            override fun isFile() = true
            override fun length() = 128L * 1024L * 1024L
            override fun lastModified() = 1L
            override fun delete() = false
        }
        val cacheFolder = object : File(cacheDirectory.path) {
            override fun listFiles(): Array<File> = arrayOf(undeletableEntry)
        }
        val source = temporaryFolder.newFile("source.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        val preview = LanShareWebImagePreviewCache(cacheFolder).getOrCreate(source)

        assertNull(preview)
        assertTrue(cacheDirectory.listFiles().orEmpty().none { it.name.endsWith(".preview.jpg") })
        assertTrue(cacheDirectory.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }
}
