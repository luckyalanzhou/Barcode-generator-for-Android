package com.luckyalanzhou.barcodegenerator.data.network.web

/** Supplies the app appearance preference when the browser page is rendered. */
fun interface LanShareWebGlassProvider {
    fun isLiquidGlassEnabled(): Boolean

    fun getLiquidGlassStrengthPercent(): Int = 50
}

internal val defaultLanShareWebGlassProvider = LanShareWebGlassProvider { true }
