package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** GPU 光学效果为可选项；启用无障碍设置或缺少前置条件时使用静态表面。 */
internal fun glassGpuAvailable(apiLevel: Int, hardwareAccelerated: Boolean, sourceReady: Boolean,
    shaderReady: Boolean, opaque: Boolean): Boolean =
    apiLevel >= 33 && hardwareAccelerated && sourceReady && shaderReady && !opaque
