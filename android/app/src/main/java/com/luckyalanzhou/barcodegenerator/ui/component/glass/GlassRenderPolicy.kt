package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** GPU optics are optional; accessibility or any missing prerequisite selects a static surface. */
internal fun glassGpuAvailable(apiLevel: Int, hardwareAccelerated: Boolean, sourceReady: Boolean,
    shaderReady: Boolean, opaque: Boolean): Boolean =
    apiLevel >= 33 && hardwareAccelerated && sourceReady && shaderReady && !opaque
