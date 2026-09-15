package com.luckyalanzhou.barcodegenerator

/** 设置页由 ComposeSettingsUi.kt 绘制；保留入口兼容旧导航调用。 */
internal fun MainActivity.showSettings() {
    page = "settings"
    composeShellRevision.intValue++
}
