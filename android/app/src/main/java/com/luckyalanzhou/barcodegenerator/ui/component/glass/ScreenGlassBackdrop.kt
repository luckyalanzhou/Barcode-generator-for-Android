package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
internal fun rememberScreenGlassBackdrop(): Backdrop? {
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
    return remember(source) {
        object : Backdrop {
            override val isCoordinatesDependent = true
            override fun DrawScope.drawBackdrop(
                density: Density,
                coordinates: LayoutCoordinates?,
                layerBlock: (GraphicsLayerScope.() -> Unit)?,
            ) {
                val image = snapshot.value ?: return
                if (coordinates?.isAttached != true) return
                val offset = origin.value - coordinates.localToScreen(Offset.Zero)
                translate(offset.x, offset.y) { drawImage(image) }
            }
        }
    }
}
