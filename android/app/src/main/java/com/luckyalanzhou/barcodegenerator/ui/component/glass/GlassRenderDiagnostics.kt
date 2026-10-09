package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** 与实际路径前提一致的原因摘要；只说明选择路径，不能证明最终 GPU 像素正确。 */
internal fun glassRenderReason(api: Int, hardware: Boolean, ready: Boolean, hasFrame: Boolean,
    shader: Boolean, opaque: Boolean, crossWindow: Boolean, width: Int, height: Int): String = when {
    opaque -> "opaque_accessibility"
    api < 33 -> "fallback_api"
    !hardware -> "fallback_hardware"
    !shader -> "fallback_shader"
    !ready -> "fallback_source"
    crossWindow && !hasFrame -> "fallback_first_frame"
    width <= 0 || height <= 0 -> "waiting_layout"
    else -> "gpu_selected"
}
