package com.luckyalanzhou.barcodegenerator.ui.component.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.luckyalanzhou.barcodegenerator.BuildConfig
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 只记录页面内容，不记录玻璃效果输出；重放显示列表无需回读位图。 */
@Stable
internal class GlassBackdropSource(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
    // Popup 与主页面不属于同一窗口，菜单采样使用屏幕坐标，不混用各自的窗口原点。
    var screenOrigin by mutableStateOf(Offset.Zero)
    var ready by mutableStateOf(false)
    var hasFrame by mutableStateOf(false)

    /**
 * 页面切换期间，离开的结果页可能会短暂保留在进入页面下方。此时两个页面都可能在同一轮 Android 绘制中
 * 尝试记录这个共享图层。RenderNode 不允许嵌套调用 beginRecording，因此内部记录器必须回退为直接绘制到
 * 已处于活动状态的外层记录器中。
     */
    internal val recording = AtomicBoolean(false)
}

internal val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdropSource?> { null }

@Composable
internal fun glassBackdropAvailable(policy: com.luckyalanzhou.barcodegenerator.ui.theme.VisualEffectsPolicy, renderer: BackdropRenderer?): Boolean =
    glassGpuAvailable(Build.VERSION.SDK_INT, LocalView.current.isHardwareAccelerated,
        LocalGlassBackdrop.current?.ready == true, renderer != null, policy.opaqueGlass)

@Composable
internal fun rememberGlassBackdropRenderer(
    menuMaterial: Boolean = false,
    enabled: Boolean = true,
): BackdropRenderer? {
    val factory = remember(menuMaterial) { lazy {
        if (Build.VERSION.SDK_INT >= 33) BackdropRenderer.createOrNull(menuMaterial) else null
    } }
    return if (!enabled || LocalVisualEffectsPolicy.current.opaqueGlass) null else factory.value
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
// 当前页面嵌套在 AnimatedContent 切换过程中。
// 直接绘制到活动中的外层记录器，避免再次调用 RenderNode.beginRecording() 导致 Android 崩溃。
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

/** 仅绘制背景的表面；调用方随后绘制清晰的前景。形状和运动状态在绘制时读取。 */
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
    diagnosticName: String = "surface",
) {
    val source = LocalGlassBackdrop.current
    val policy = LocalVisualEffectsPolicy.current
    val density = LocalDensity.current.density
    var origin by remember { mutableStateOf(Offset.Zero) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val gpu = glassGpuAvailable(Build.VERSION.SDK_INT, LocalView.current.isHardwareAccelerated,
        source?.ready == true && (!screenCoordinates || source.hasFrame), renderer != null, policy.opaqueGlass)
    // 只记录路径或稳定几何变化，不读取动画参数，不逐帧写日志；磁盘写入放到 IO。
    if (BuildConfig.DEBUG_LOG_EXPORT) {
        val accelerated = LocalView.current.isHardwareAccelerated
        val reason = glassRenderReason(Build.VERSION.SDK_INT, accelerated, source?.ready == true,
            source?.hasFrame == true, renderer != null, policy.opaqueGlass, screenCoordinates, size.width, size.height)
        val role = if (Build.VERSION.SDK_INT >= 33) renderer?.diagnosticRole ?: "unavailable" else "unavailable"
        val diagnostic = "component=$diagnosticName role=$role route=$reason effectEligible=${gpu && size.width > 0 && size.height > 0} " +
            "sdk=${Build.VERSION.SDK_INT} hardware=$accelerated sourceReady=${source?.ready == true} hasFrame=${source?.hasFrame == true} " +
            "size=${size.width}x${size.height} sourceSize=${source?.layer?.size} density=$density " +
            "opaque=${policy.opaqueGlass} highContrast=${policy.highContrast} reduceMotion=${policy.reduceMotion} " +
            "screenCoordinates=$screenCoordinates opacity=$opacity cornerDp=$cornerDp blurDp=$blurDp"
        LaunchedEffect(diagnostic) { withContext(Dispatchers.IO) { DebugLog.record("glass_render", diagnostic) } }
    }
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

/** 菜单材质负责保护文字；即使没有着色器，小控件也保持预定的覆盖范围。 */
internal fun glassFallbackOpacity(opacity: Float, smallControl: Boolean, opaque: Boolean): Float =
    if (opaque) 1f else if (smallControl) opacity.coerceIn(0f, 1f) else opacity.coerceIn(.92f, 1f)

@RequiresApi(33)
internal class BackdropRenderer(private val shader: RuntimeShader, private val menuMaterial: Boolean = false) {
    val diagnosticRole: String get() = if (menuMaterial) "menu" else "tab_or_surface"
    private val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
    private val blurCache = SingleEffectCache<Float, RenderEffect>()
    private val effectCache = SingleEffectCache<Float, androidx.compose.ui.graphics.RenderEffect>()

    fun effect(size: IntSize, density: Float, color: Color, opacity: Float, corner: Float, blur: Float, refraction: Float, capsule: TabGlassFrame?): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("resolution", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("bounds", capsule?.centerX ?: size.width / 2f, capsule?.centerY ?: size.height / 2f,
            capsule?.halfWidth ?: size.width / 2f, capsule?.halfHeight ?: size.height / 2f)
        shader.setFloatUniform("shape", corner * density, blur * density, refraction * density, opacity)
        shader.setFloatUniform("contact", capsule?.touchX ?: size.width / 2f, capsule?.touchY ?: 0f,
            capsule?.motion ?: 0f, capsule?.contactSpread ?: 1f)
        shader.setFloatUniform("capsuleMode", if (capsule == null) 0f else 1f)
        shader.setFloatUniform("menuMaterial", if (menuMaterial) 1f else 0f)
        shader.setFloatUniform("pixelDensity", density)
        val optics = capsule?.let(::tabDynamicOptics)
        shader.setFloatUniform("capsuleOptics", optics?.dispersionPx ?: 0f, optics?.edgeColorStrength ?: 0f)
        shader.setColorUniform("surfaceColor", color.toArgb())
        val blurPx = if (blur > 0f) blur * density else 0f
        return effectCache.get(blurPx) {
            // 胶囊和材质通过 uniform 动态更新；模糊半径不变时复用 shader 效果图。
            // 圆按钮在 shader 内按复杂度扩散；预先全局模糊会丢失分类依据。
            val effect = if (blurPx > 0f) {
                val blurEffect = blurCache.get(blurPx) {
                    RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                }
                RenderEffect.createChainEffect(shaderEffect, blurEffect)
            } else shaderEffect
            effect.asComposeRenderEffect()
        }
    }

    companion object {
        fun createOrNull(menuMaterial: Boolean = false): BackdropRenderer? = try {
            BackdropRenderer(RuntimeShader(GLASS_BACKDROP_SHADER), menuMaterial)
        } catch (error: IllegalArgumentException) {
            DebugLog.record("glass_render", "shader_creation_failed menu=$menuMaterial sdk=${Build.VERSION.SDK_INT}", error)
            null
        }
    }
}
