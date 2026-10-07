package com.luckyalanzhou.barcodegenerator.ui.app

/** Shared by backdrop and foreground contrast estimation; no readback or theme mutation. */
internal const val GLASS_ADAPTIVE_TINT_SHADER = """
half3 glassAdaptiveTint(half3 base, half3 scene, float opacity) {
    float brightness = dot(float3(scene), float3(0.2126, 0.7152, 0.0722));
    // Continuous spatial response: no threshold-based light/dark switch over barcode lines.
    float environment = smoothstep(0.16, 0.74, brightness);
    // Keep a visible neutral body on flat backgrounds as well as busy content.
    // The old target sat too close to both theme backgrounds, so the capsule
    // disappeared in light mode and read as a nearly black slab in dark mode.
    half3 neutral = half3(mix(0.12, 0.86, environment));
    half3 chroma = clamp(scene - half3(brightness), half3(-0.20), half3(0.20));
    half3 target = clamp(neutral + chroma * half(0.08), half3(0.0), half3(1.0));
    // This function is used only by the translucent capsule controls. Menus
    // and opaque accessibility surfaces retain their own contrast treatment.
    float adaptation = 0.42 * (1.0 - 0.20 * clamp(opacity, 0.0, 1.0));
    return mix(base, target, half(adaptation));
}
"""
