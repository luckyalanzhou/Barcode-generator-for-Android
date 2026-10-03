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
    private var previousMaterial: TabGlassMaterial? = null
    private var cachedEffect: androidx.compose.ui.graphics.RenderEffect? = null

    fun effect(frame: TabGlassFrame, background: Color, accent: Color, material: TabGlassMaterial): androidx.compose.ui.graphics.RenderEffect {
        if (frame == previousFrame && background == previousBackground && accent == previousAccent && material == previousMaterial) {
            cachedEffect?.let { return it }
        }
        shader.setFloatUniform("resolution", frame.width, frame.height)
        shader.setFloatUniform("capsule", frame.centerX, frame.centerY, frame.halfWidth, frame.halfHeight)
        shader.setFloatUniform("optics", frame.refractionPx, frame.motion, frame.contactSpread, frame.density)
        shader.setFloatUniform("touchPoint", frame.touchX, frame.touchY)
        shader.setColorUniform("backgroundColor", background.toArgb())
        shader.setColorUniform("accentColor", accent.toArgb())
        shader.setFloatUniform("material", material.whiteLift, material.accentTint, material.rimLight, material.innerShadow)
        shader.setFloatUniform("edgeWidth", material.edgeWidthDp * frame.density)
        val effect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        previousFrame = frame
        previousBackground = background
        previousAccent = accent
        previousMaterial = material
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
