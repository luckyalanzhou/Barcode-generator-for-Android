package com.luckyalanzhou.barcodegenerator.ui.app

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.abs

/** Only moving foreground pixels are displaced; no second material, blur or tint. */
internal fun tabForegroundDisplacement(frame: TabGlassFrame, velocity: Float): Float {
    if (!velocity.isFinite() || !frame.refractionPx.isFinite()) return 0f
    val movement = (abs(velocity) / .8f).coerceIn(0f, 1f)
    return minOf(frame.refractionPx.coerceAtLeast(0f) * .55f, 1.2f * frame.density) * movement
}

internal const val TAB_FOREGROUND_LENS_SHADER = """
uniform shader content;
uniform float2 resolution;
uniform float4 capsule;
uniform float2 lens;

half4 main(float2 p) {
    if (lens.x <= 0.001) return content.eval(p);
    float radius = min(capsule.z, capsule.w);
    float2 local = p - capsule.xy;
    float2 q = abs(local) - (capsule.zw - float2(radius));
    float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    if (sd >= 0.0) return content.eval(p);
    float spine = clamp(local.x, -capsule.z + radius, capsule.z - radius);
    float2 radial = float2(local.x - spine, local.y);
    float2 normal = radial / max(length(radial), 0.001);
    float depth = -sd;
    float edge = 1.0 - smoothstep(0.0, max(radius * 0.55, 1.0), depth);
    // Zero at the boundary avoids a seam; single sampling avoids doubled glyphs.
    float boundary = smoothstep(0.0, max(lens.y * 2.0, 1.0), depth);
    float2 displacement = normal * lens.x * edge * boundary;
    float2 point = clamp(p - displacement, float2(0.5), resolution - 0.5);
    return content.eval(point);
}
"""

@RequiresApi(33)
internal class TabForegroundLensRenderer private constructor(private val shader: RuntimeShader) {
    private data class Key(val width: Float, val height: Float, val x: Float, val y: Float,
        val halfWidth: Float, val halfHeight: Float, val displacement: Float, val density: Float)
    private var previous: Key? = null
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    fun effect(frame: TabGlassFrame, displacement: Float): androidx.compose.ui.graphics.RenderEffect {
        val key = Key(frame.width, frame.height, frame.centerX, frame.centerY,
            frame.halfWidth, frame.halfHeight, displacement, frame.density)
        if (previous == key) cached?.let { return it }
        shader.setFloatUniform("resolution", frame.width, frame.height)
        shader.setFloatUniform("capsule", frame.centerX, frame.centerY, frame.halfWidth, frame.halfHeight)
        shader.setFloatUniform("lens", displacement, frame.density)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect().also {
            previous = key
            cached = it
        }
    }

    companion object {
        fun createOrNull(): TabForegroundLensRenderer? = try {
            TabForegroundLensRenderer(RuntimeShader(TAB_FOREGROUND_LENS_SHADER))
        } catch (_: IllegalArgumentException) { null }
    }
}
