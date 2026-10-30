package com.luckyalanzhou.barcodegenerator.data.preview

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.ParcelFileDescriptor
import android.os.Process
import io.github.lucf15.tiffrenderer.TiffRenderMode
import io.github.lucf15.tiffrenderer.TiffRenderer
import io.github.lucf15.tiffrenderer.render
import io.github.lucf15.tiffrenderer.use
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.math.roundToInt

/**
 * 仅用于局域网分享预览的 TIFF 解码适配器。原文件不会被重写或转换；
 * 解码失败时回退到常规附件下载界面。
 */
object LanShareTiffPreviewDecoder {
    /** 允许解码手机原始分辨率照片，同时遵守解码器文档规定的硬性限制。 */
    const val MAX_SOURCE_PIXELS_64_BIT = 250_000_000L
    const val MAX_SOURCE_PIXELS_32_BIT = 64_000_000L

    suspend fun decode(file: File, maxEdge: Int): Bitmap? {
        if (!file.isFile || maxEdge <= 0) return null

        return try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            TiffRenderer(descriptor).use { renderer ->
                renderer.openPage(0).use { page ->
                    val width = page.width
                    val height = page.height
                    val pixelBudget = if (Process.is64Bit()) MAX_SOURCE_PIXELS_64_BIT else MAX_SOURCE_PIXELS_32_BIT
                    if (!isWithinPixelBudget(width, height, pixelBudget)) {
                        null
                    } else {
                        val scale = minOf(1f, maxEdge.toFloat() / maxOf(width, height))
                        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
                        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(
                                destination = bitmap,
                                transform = Matrix().apply { setScale(scale, scale) },
                                renderMode = TiffRenderMode.FOR_DISPLAY,
                            )
                            bitmap
                        } catch (failure: Exception) {
                            bitmap.recycle()
                            throw failure
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    internal fun isWithinPixelBudget(
        width: Int,
        height: Int,
        maxPixels: Long = MAX_SOURCE_PIXELS_64_BIT,
    ): Boolean = width > 0 && height > 0 && width.toLong() * height.toLong() <= maxPixels
}
