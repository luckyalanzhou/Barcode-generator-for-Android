package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

/** Popup 与主窗口使用屏幕坐标对齐，避免 positionInWindow 跨窗口采样错位。 */
@Composable
internal fun rememberScreenGlassBackdrop(softenBackdropColor: Boolean = false): ScreenGlassBackdrop? {
    val source = LocalGlassBackdrop.current ?: return null
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
    } ?: return null
    val origin = remember { mutableStateOf(Offset.Zero) }
    // 从 Window 复制图像；不在主窗口与 Popup 之间重放或读取 Compose GraphicsLayer。
    val snapshot = produceState<ImageBitmap?>(null, source, activity) {
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) return@produceState
        val location = IntArray(2)
        decor.getLocationOnScreen(location)
        origin.value = Offset(location[0].toFloat(), location[1].toFloat())
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        value = suspendCancellableCoroutine { continuation ->
            PixelCopy.request(activity.window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS && continuation.isActive) {
                    continuation.resume(bitmap.asImageBitmap())
                } else {
                    bitmap.recycle()
                    if (continuation.isActive) {
                        android.util.Log.w("GlassBackdrop", "Window snapshot failed: $result")
                        continuation.resume(null)
                    }
                }
            }, Handler(Looper.getMainLooper()))
        }
    }
    return remember(source, softenBackdropColor) {
        val colorFilter = if (softenBackdropColor) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(.22f) })
        } else null
        object : ScreenGlassBackdrop {
            override fun averageLuminance(bounds: Rect): Float? {
                val bitmap = snapshot.value?.asAndroidBitmap() ?: return null
                val position = origin.value
                val left = (bounds.left - position.x).toInt().coerceIn(0, bitmap.width)
                val top = (bounds.top - position.y).toInt().coerceIn(0, bitmap.height)
                val right = (bounds.right - position.x).toInt().coerceIn(0, bitmap.width)
                val bottom = (bounds.bottom - position.y).toInt().coerceIn(0, bitmap.height)
                if (right <= left || bottom <= top) return null
                var luminance = 0f
                for (row in 0 until 5) {
                    for (column in 0 until 5) {
                        val x = left + (2 * column + 1) * (right - left) / 10
                        val y = top + (2 * row + 1) * (bottom - top) / 10
                        luminance += Color(bitmap.getPixel(x, y)).luminance()
                    }
                }
                return luminance / 25f
            }

            override val isCoordinatesDependent = true
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) {
                val image = snapshot.value ?: return
                if (coordinates?.isAttached != true) return
                val offset = origin.value - coordinates.localToScreen(Offset.Zero)
                translate(offset.x, offset.y) { drawImage(image, colorFilter = colorFilter) }
            }
        }
    }
}

internal interface ScreenGlassBackdrop : Backdrop {
    fun averageLuminance(bounds: Rect): Float?
}
