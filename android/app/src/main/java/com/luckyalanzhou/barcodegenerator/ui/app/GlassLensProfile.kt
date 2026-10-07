package com.luckyalanzhou.barcodegenerator.ui.app

/** Shared rounded-lens profile. Zero at its boundary and center, peaked inside the bevel.
 * Background and moving foreground use the same shape, not independently tuned curves.
 * This is a bounded approximation, not Apple's proprietary material implementation.
 */
internal const val GLASS_LENS_PROFILE_SHADER = """
float glassLensProfile(float depth, float radius, float density) {
    float band = max(radius * 0.85, max(density, 0.1) * 3.0);
    float t = clamp(depth / band, 0.0, 1.0);
    // Smooth derivatives at both ends prevent a hard moving cut or a second outline.
    float bell = 4.0 * t * (1.0 - t);
    return bell * bell;
}
"""
