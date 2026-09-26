package com.luckyalanzhou.barcodegenerator.data.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import com.luckyalanzhou.barcodegenerator.domain.isLanShareTiffName
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Creates small, browser-safe derivatives for TIFF/HEIF web previews without touching originals. */
internal class LanShareWebImagePreviewCache(private val cacheFolder: File) {
    private val cacheLock = Any()

    fun getOrCreate(source: File): File? {
        if (!source.isFile || !isTranscodableImage(source.name)) return null

        val key = cacheKey(source)
        val cached = File(cacheFolder, "$key.preview.jpg")
        synchronized(cacheLock) {
            if (cached.isFile && cached.length() > 0L) {
                cached.setLastModified(System.currentTimeMillis())
                return cached
            }
            if (cached.exists() && !cached.delete()) return null
            if (!cacheFolder.isDirectory && !cacheFolder.mkdirs()) return null

            val bitmap = decode(source) ?: return null
            val temporary = File(cacheFolder, ".$key.${System.nanoTime()}.part")
            try {
                if (!writeJpegPreview(bitmap, temporary) || temporary.length() <= 0L) return null
                if (temporary.length() > MAX_CACHE_BYTES) return null
                makeRoom(temporary.length())
                if (!temporary.renameTo(cached)) return null
                cached.setLastModified(System.currentTimeMillis())
                return cached
            } catch (_: OutOfMemoryError) {
                return null
            } catch (_: Exception) {
                return null
            } finally {
                bitmap.recycleIfNeeded()
                temporary.delete()
            }
        }
    }

    private fun decode(source: File): Bitmap? = try {
        if (isLanShareTiffName(source.name)) {
            runBlocking(Dispatchers.IO) {
                LanShareTiffPreviewDecoder.decode(source, MAX_PREVIEW_EDGE)
            }
        } else {
            decodeHeif(source)
        }
    } catch (_: LinkageError) {
        null
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }

    private fun decodeHeif(source: File): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decoded = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val width = info.size.width
                    val height = info.size.height
                    val longestEdge = maxOf(width, height)
                    if (width > 0 && height > 0 && longestEdge > MAX_PREVIEW_EDGE) {
                        val scale = MAX_PREVIEW_EDGE.toFloat() / longestEdge
                        decoder.setTargetSize(
                            (width * scale).toInt().coerceAtLeast(1),
                            (height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            }.getOrNull()
            if (decoded != null) return decoded
        }
        return decodeWithBitmapFactory(source)
    }

    private fun decodeWithBitmapFactory(source: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight).toLong() > MAX_PREVIEW_EDGE.toLong() * sampleSize) {
            sampleSize *= 2
        }
        BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }.getOrNull()

    private fun writeJpegPreview(bitmap: Bitmap, destination: File): Boolean {
        val flattened = if (bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565).also { opaque ->
                Canvas(opaque).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            }
        } else bitmap

        return try {
            FileOutputStream(destination).use { output ->
                flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output).also { output.flush() }
            }
        } finally {
            if (flattened !== bitmap) flattened.recycle()
        }
    }

    private fun makeRoom(incomingBytes: Long) {
        var currentBytes = cacheFolder.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(CACHE_SUFFIX) }
            .sumOf { it.length() }
        if (currentBytes + incomingBytes <= MAX_CACHE_BYTES) return

        cacheFolder.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(CACHE_SUFFIX) }
            .sortedBy { it.lastModified() }
            .forEach { old ->
                if (currentBytes + incomingBytes <= MAX_CACHE_BYTES) return
                val oldSize = old.length()
                if (old.delete()) currentBytes -= oldSize
            }
    }

    private fun cacheKey(source: File): String {
        val identity = "${source.canonicalPath}\u0000${source.length()}\u0000${source.lastModified()}"
        return MessageDigest.getInstance("SHA-256")
            .digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun isTranscodableImage(name: String): Boolean =
        isLanShareTiffName(name) || name.substringAfterLast('.', "").lowercase() in setOf("heic", "heif")

    private fun Bitmap.recycleIfNeeded() {
        if (!isRecycled) recycle()
    }

    private companion object {
        const val MAX_PREVIEW_EDGE = 2_048
        const val MAX_CACHE_BYTES = 128L * 1024L * 1024L
        const val CACHE_SUFFIX = ".preview.jpg"
        const val JPEG_QUALITY = 88
    }
}
