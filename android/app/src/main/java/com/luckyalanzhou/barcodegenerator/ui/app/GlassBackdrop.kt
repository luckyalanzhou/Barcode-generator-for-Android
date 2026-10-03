package com.luckyalanzhou.barcodegenerator.ui.app

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy

/** Records only the page, never a glass output. Replaying the display list requires no bitmap readback. */
@Stable
internal class GlassBackdropSource(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    var ready by mutableStateOf(false)
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropSource?> { null }

@Composable
internal fun rememberGlassBackdrop(): GlassBackdropSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdropSource(layer) }
}

internal fun Modifier.recordGlassBackdrop(source: GlassBackdropSource): Modifier =
    onGloballyPositioned {
        source.origin = it.localToWindow(Offset.Zero)
        source.ready = true
    }.drawWithContent {
        source.layer.record { this@drawWithContent.drawContent() }
        drawLayer(source.layer)
    }

/** Background-only surface: callers draw crisp foreground after it. Shape and motion are read at draw time. */
@Composable
internal fun GlassBackdropSurface(
    modifier: Modifier,
    color: Color,
    opacity: Float,
    cornerDp: Float,
    blurDp: Float,
    refractionDp: () -> Float,
    capsule: (() -> TabGlassFrame)? = null,
    drawFallback: Boolean = true,
) {
    val source = LocalGlassBackdrop.current
    val policy = LocalVisualEffectsPolicy.current
    val renderer = remember {
        if (Build.VERSION.SDK_INT >= 33) BackdropRenderer.createOrNull() else null
    }
    val density = LocalDensity.current.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gpu = Build.VERSION.SDK_INT >= 33 && renderer != null &&
        LocalView.current.isHardwareAccelerated && source?.ready == true && !policy.opaqueGlass
    Canvas(modifier.onGloballyPositioned {
        origin = it.localToWindow(Offset.Zero)
        size = it.size
    }.graphicsLayer {
        if (gpu && size.width > 0 && size.height > 0) {
            renderEffect = renderer.effect(size, density, color, opacity, cornerDp, blurDp,
                if (policy.reduceMotion) 0f else refractionDp(), capsule?.invoke())
        } else renderEffect = null
    }) {
        if (gpu) {
            drawRect(color)
            val offset = source.origin - origin
            translate(offset.x, offset.y) { drawLayer(source.layer) }
        } else if (drawFallback) {
            drawRoundRect(
                color.copy(alpha = if (policy.opaqueGlass || Build.VERSION.SDK_INT < 31) 1f else opacity.coerceAtLeast(.92f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerDp * density),
            )
        }
    }
}

@RequiresApi(33)
private class BackdropRenderer(private val shader: RuntimeShader) {
    private var previous: List<Any>? = null
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    fun effect(size: IntSize, density: Float, color: Color, opacity: Float, corner: Float, blur: Float, refraction: Float, capsule: TabGlassFrame?): androidx.compose.ui.graphics.RenderEffect {
        val key = listOf(size, density, color, opacity, corner, blur, refraction, capsule ?: Unit)
        if (key == previous) cached?.let { return it }
        shader.setFloatUniform("resolution", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("bounds", capsule?.centerX ?: size.width / 2f, capsule?.centerY ?: size.height / 2f,
            capsule?.halfWidth ?: size.width / 2f, capsule?.halfHeight ?: size.height / 2f)
        shader.setFloatUniform("shape", corner * density, blur * density, refraction * density, opacity)
        shader.setFloatUniform("contact", capsule?.touchX ?: size.width / 2f, capsule?.touchY ?: 0f,
            capsule?.motion ?: 0f, capsule?.contactSpread ?: 1f)
        shader.setColorUniform("surfaceColor", color.toArgb())
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val effect = if (blur > 0f) RenderEffect.createChainEffect(lens,
            RenderEffect.createBlurEffect(blur * density, blur * density, Shader.TileMode.CLAMP)) else lens
        return effect.asComposeRenderEffect().also {
            previous = key
            cached = it
        }
    }

    companion object {
        fun createOrNull(): BackdropRenderer? = try {
            BackdropRenderer(RuntimeShader(GLASS_BACKDROP_SHADER))
        } catch (_: IllegalArgumentException) { null }
    }
}
