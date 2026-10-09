package com.luckyalanzhou.barcodegenerator.ui.component.glass

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
import java.util.concurrent.atomic.AtomicBoolean

/** Records only the page, never a glass output. Replaying the display list requires no bitmap readback. */
@Stable
internal class GlassBackdropSource(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    // Popup 与主页面不属于同一窗口，菜单采样使用屏幕坐标，不混用各自的窗口原点。
    var screenOrigin by mutableStateOf(Offset.Zero)
    var ready by mutableStateOf(false)
    var hasFrame by mutableStateOf(false)

    /**
     * A page transition can temporarily keep the outgoing Results page alive
     * below the incoming page. Both pages may then try to record this shared
     * layer in the same Android draw pass. RenderNode does not allow nested
     * beginRecording calls, so the inner recorder must fall back to drawing
     * directly into the recorder that is already active.
     */
    internal val recording = AtomicBoolean(false)
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropSource?> { null }

@Composable
internal fun glassBackdropAvailable(policy: com.luckyalanzhou.barcodegenerator.ui.theme.VisualEffectsPolicy, renderer: BackdropRenderer?): Boolean =
    glassGpuAvailable(Build.VERSION.SDK_INT, LocalView.current.isHardwareAccelerated,
        LocalGlassBackdrop.current?.ready == true, renderer != null, policy.opaqueGlass)

@Composable
internal fun rememberGlassBackdropRenderer(menuMaterial: Boolean = false, roundAction: Boolean = false): BackdropRenderer? {
    val factory = remember(menuMaterial, roundAction) { lazy {
        if (Build.VERSION.SDK_INT >= 33) BackdropRenderer.createOrNull(menuMaterial, roundAction) else null
    } }
    return if (LocalVisualEffectsPolicy.current.opaqueGlass) null else factory.value
}

@Composable
internal fun rememberGlassBackdrop(): GlassBackdropSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdropSource(layer) }
}

internal fun Modifier.recordGlassBackdrop(source: GlassBackdropSource): Modifier =
    onGloballyPositioned {
        source.origin = it.localToWindow(Offset.Zero)
        source.screenOrigin = it.localToScreen(Offset.Zero)
        source.ready = true
    }.drawWithContent {
        if (!source.recording.compareAndSet(false, true)) {
            // This is the nested page during an AnimatedContent transition.
            // Draw into the active outer recording instead of re-entering
            // RenderNode.beginRecording(), which crashes on Android.
            this@drawWithContent.drawContent()
            return@drawWithContent
        }
        try {
            source.layer.record { this@drawWithContent.drawContent() }
            if (!source.hasFrame) source.hasFrame = true
            drawLayer(source.layer)
        } finally {
            source.recording.set(false)
        }
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
    thicknessProgress: () -> Float = { 1f },
    renderer: BackdropRenderer? = rememberGlassBackdropRenderer(),
    screenCoordinates: Boolean = false,
) {
    val source = LocalGlassBackdrop.current
    val policy = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gpu = glassGpuAvailable(Build.VERSION.SDK_INT, LocalView.current.isHardwareAccelerated,
        source?.ready == true && (!screenCoordinates || source.hasFrame), renderer != null, policy.opaqueGlass)
    Canvas(modifier.onGloballyPositioned {
        origin = it.localToWindow(Offset.Zero)
        screenOrigin = it.localToScreen(Offset.Zero)
        size = it.size
    }.graphicsLayer {
        if (Build.VERSION.SDK_INT >= 33 && gpu && renderer != null && size.width > 0 && size.height > 0) {
            val thickness = thicknessProgress().coerceIn(0f, 1f)
            renderEffect = renderer.effect(size, density, color, opacity, cornerDp, blurDp * (.45f + .55f * thickness),
                if (policy.reduceMotion) 0f else refractionDp(), capsule?.invoke())
        } else renderEffect = null
    }) {
        if (gpu && source != null) {
            drawRect(color)
            val offset = backdropSampleOffset(source.origin, source.screenOrigin, origin, screenOrigin, screenCoordinates)
            translate(offset.x, offset.y) { drawLayer(source.layer) }
        } else if (drawFallback) {
            drawRoundRect(
                color.copy(alpha = glassFallbackOpacity(opacity, capsule != null, policy.opaqueGlass)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerDp * density),
            )
        }
    }
}

/** 同窗口保留原采样路径；菜单跨 Popup 时统一换算到屏幕，来源与目标整体平移不会改变采样。 */
internal fun backdropSampleOffset(sourceWindow: Offset, sourceScreen: Offset,
    targetWindow: Offset, targetScreen: Offset, screenCoordinates: Boolean): Offset =
    if (screenCoordinates) sourceScreen - targetScreen else sourceWindow - targetWindow

/** Menus protect text; small controls retain their intended coverage even without a shader. */
internal fun glassFallbackOpacity(opacity: Float, smallControl: Boolean, opaque: Boolean): Float =
    if (opaque) 1f else if (smallControl) opacity.coerceIn(0f, 1f) else opacity.coerceIn(.92f, 1f)

@RequiresApi(33)
internal class BackdropRenderer(private val shader: RuntimeShader, private val menuMaterial: Boolean = false,
    private val roundAction: Boolean = false) {
    private data class EffectKey(val size: IntSize, val density: Float, val color: Color,
        val opacity: Float, val corner: Float, val blur: Float, val refraction: Float, val capsule: TabGlassFrame?)
    private var previous: EffectKey? = null
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null
    private val blurCache = SingleEffectCache<Float, RenderEffect>()

    fun effect(size: IntSize, density: Float, color: Color, opacity: Float, corner: Float, blur: Float, refraction: Float, capsule: TabGlassFrame?): androidx.compose.ui.graphics.RenderEffect {
        val key = EffectKey(size, density, color, opacity, corner, blur, refraction, capsule)
        if (key == previous) cached?.let { return it }
        shader.setFloatUniform("resolution", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("bounds", capsule?.centerX ?: size.width / 2f, capsule?.centerY ?: size.height / 2f,
            capsule?.halfWidth ?: size.width / 2f, capsule?.halfHeight ?: size.height / 2f)
        shader.setFloatUniform("shape", corner * density, blur * density, refraction * density, opacity)
        shader.setFloatUniform("contact", capsule?.touchX ?: size.width / 2f, capsule?.touchY ?: 0f,
            capsule?.motion ?: 0f, capsule?.contactSpread ?: 1f)
        shader.setFloatUniform("capsuleMode", if (capsule == null) 0f else 1f)
        shader.setFloatUniform("menuMaterial", if (menuMaterial) 1f else 0f)
        shader.setFloatUniform("roundAction", if (roundAction) 1f else 0f)
        shader.setFloatUniform("pixelDensity", density)
        val optics = if (roundAction) null else capsule?.let(::tabDynamicOptics)
        shader.setFloatUniform("capsuleOptics", optics?.dispersionPx ?: 0f, optics?.edgeColorStrength ?: 0f)
        shader.setColorUniform("surfaceColor", color.toArgb())
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        // 圆按钮在 shader 内按复杂度扩散；预先全局模糊会丢失分类依据。
        val effect = if (blur > 0f && !roundAction) {
            val blurPx = blur * density
            val blurEffect = blurCache.get(blurPx) {
                RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            }
            RenderEffect.createChainEffect(lens, blurEffect)
        } else lens
        return effect.asComposeRenderEffect().also {
            previous = key
            cached = it
        }
    }

    companion object {
        fun createOrNull(menuMaterial: Boolean = false, roundAction: Boolean = false): BackdropRenderer? = try {
            BackdropRenderer(RuntimeShader(GLASS_BACKDROP_SHADER), menuMaterial, roundAction)
        } catch (_: IllegalArgumentException) { null }
    }
}
