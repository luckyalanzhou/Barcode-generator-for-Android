package com.luckyalanzhou.barcodegenerator.ui.app

/** Shared by backdrop and foreground contrast estimation; no readback or theme mutation. */
internal const val GLASS_ADAPTIVE_TINT_SHADER = """
half3 glassAdaptiveTint(half3 base, half3 scene, float opacity) {
    // Adapt to the actual sampled backdrop instead of injecting a fixed gray/white body tint.
    // Menus and opaque accessibility surfaces retain their own contrast treatment.
    float adaptation = 0.42 * (1.0 - 0.20 * clamp(opacity, 0.0, 1.0));
    return mix(base, clamp(scene, half3(0.0), half3(1.0)), half(adaptation));
}
"""
