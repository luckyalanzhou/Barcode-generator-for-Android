package com.luckyalanzhou.barcodegenerator.ui.app

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
uniform float atlasMode;
uniform float surfaceOpacity;
layout(color) uniform half4 surfaceColor;

half4 glyph(float2 p) {
    return content.eval(p + float2(resolution.x * atlasMode, 0.0));
}

half4 main(float2 p) {
    // The second atlas half is input only; never draw it or the background into the output.
    if (p.x >= resolution.x || p.y >= resolution.y) return half4(0.0);
    if (lens.x <= 0.001) return glyph(p);
    float radius = min(capsule.z, capsule.w);
    float2 local = p - capsule.xy;
    float2 q = abs(local) - (capsule.zw - float2(radius));
    float sd = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    if (sd >= 0.0) return glyph(p);
    float spine = clamp(local.x, -capsule.z + radius, capsule.z - radius);
    float2 radial = float2(local.x - spine, local.y);
    float2 normal = radial / max(length(radial), 0.001);
    float depth = -sd;
    float edge = 1.0 - smoothstep(0.0, max(radius * 0.55, 1.0), depth);
    // Zero at the boundary avoids a seam; single sampling avoids doubled glyphs.
    float boundary = smoothstep(0.0, max(lens.y * 2.0, 1.0), depth);
    float2 displacement = normal * lens.x * edge * boundary;
    float2 point = clamp(p - displacement, float2(0.5), resolution - 0.5);
    half4 foreground = glyph(point);
    if (atlasMode < 0.5 || foreground.a <= 0.001) return foreground;
    // Joint, bounded contrast protection. Spatial smoothing avoids reacting to a single pixel.
    float2 spread = float2(3.0 * lens.y, 0.0);
    half3 center = content.eval(point).rgb;
    half3 scene = center * 0.4;
    scene += content.eval(clamp(point + spread, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(point - spread, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(point + spread.yx, float2(0.5), resolution - 0.5)).rgb * 0.15;
    scene += content.eval(clamp(point - spread.yx, float2(0.5), resolution - 0.5)).rgb * 0.15;
    float luminance = dot(float3(scene), float3(0.2126, 0.7152, 0.0722));
    float target = dot(float3(surfaceColor.rgb), float3(0.2126, 0.7152, 0.0722));
    float detail = smoothstep(0.015, 0.35, length(float3(center - scene)));
    float protection = smoothstep(0.12, 0.75, abs(luminance - target)) * 0.24 + detail * 0.10;
    half3 glass = mix(scene, surfaceColor.rgb, half(clamp(surfaceOpacity + protection, 0.0, 1.0)));
    half3 color = foreground.rgb / foreground.a;
    float glassLuminance = dot(float3(glass), float3(0.2126, 0.7152, 0.0722));
    float glyphLuminance = dot(float3(color), float3(0.2126, 0.7152, 0.0722));
    float poorContrast = 1.0 - smoothstep(0.16, 0.46, abs(glassLuminance - glyphLuminance));
    float movement = clamp(lens.x / max(lens.y * 0.7, 0.001), 0.0, 1.0);
    half3 contrastTarget = half3(1.0 - smoothstep(0.35, 0.65, glassLuminance));
    color = mix(color, contrastTarget, half(poorContrast * movement * boundary * 0.18));
    return half4(color * foreground.a, foreground.a);
}
"""

@RequiresApi(33)
internal class TabForegroundLensRenderer private constructor(private val shader: RuntimeShader) {
    private data class Key(val width: Float, val height: Float, val x: Float, val y: Float,
        val halfWidth: Float, val halfHeight: Float, val displacement: Float, val density: Float,
        val background: Color, val opacity: Float)
    private var previous: Key? = null
    private var cached: androidx.compose.ui.graphics.RenderEffect? = null

    fun effect(frame: TabGlassFrame, displacement: Float, background: Color, opacity: Float): androidx.compose.ui.graphics.RenderEffect {
        val key = Key(frame.width, frame.height, frame.centerX, frame.centerY,
            frame.halfWidth, frame.halfHeight, displacement, frame.density, background, opacity)
        if (previous == key) cached?.let { return it }
        shader.setFloatUniform("resolution", frame.width, frame.height)
        shader.setFloatUniform("capsule", frame.centerX, frame.centerY, frame.halfWidth, frame.halfHeight)
        shader.setFloatUniform("lens", displacement, frame.density)
        shader.setFloatUniform("atlasMode", 1f)
        shader.setFloatUniform("surfaceOpacity", opacity)
        shader.setColorUniform("surfaceColor", background.toArgb())
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
