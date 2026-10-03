package com.luckyalanzhou.barcodegenerator.ui.app

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb

/** Android's immutable RenderEffect captures uniforms; retain the compiled shader, refresh only the effect. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class TabGlassRenderer private constructor(private val shader: RuntimeShader) {
    private var previousFrame: TabGlassFrame? = null
    private var previousBackground: Color? = null
    private var previousAccent: Color? = null
    private var previousDark: Boolean? = null
    private var cachedEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effect(frame: TabGlassFrame, background: Color, accent: Color, dark: Boolean): androidx.compose.ui.graphics.RenderEffect {
        if (frame == previousFrame && background == previousBackground && accent == previousAccent && dark == previousDark) {
            cachedEffect?.let { return it }
        }
        shader.setFloatUniform("resolution", frame.width, frame.height)
        shader.setFloatUniform("capsule", frame.centerX, frame.centerY, frame.halfWidth, frame.halfHeight)
        shader.setFloatUniform("optics", frame.refractionPx, frame.motion, 0f, frame.density)
        shader.setFloatUniform("touchPoint", frame.touchX, frame.touchY)
        shader.setColorUniform("backgroundColor", background.toArgb())
        shader.setColorUniform("accentColor", accent.toArgb())
        shader.setFloatUniform("material", if (dark) .055f else .18f, .035f, if (dark) .16f else .22f, if (dark) .10f else .07f)
        val effect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        previousFrame = frame
        previousBackground = background
        previousAccent = accent
        previousDark = dark
        cachedEffect = effect
        return effect
    }

    companion object {
        fun createOrNull(): TabGlassRenderer? = try {
            val shader = RuntimeShader(TAB_GLASS_SHADER)
            // Validate the child binding once, so an unsupported renderer uses the compatible surface.
            RenderEffect.createRuntimeShaderEffect(shader, "content")
            TabGlassRenderer(shader)
        } catch (failure: IllegalArgumentException) {
            Log.w("TabGlass", "Liquid lens unavailable; using compatible tab material", failure)
            null
        }
    }
}
