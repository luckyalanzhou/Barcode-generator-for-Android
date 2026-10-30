package com.luckyalanzhou.barcodegenerator

/** 配置重建时保留 ViewModel 持有的任务；仅 Activity 真正结束时执行清理。 */
internal inline fun cancelUpdateDownloadIfActivityFinishes(
    isChangingConfigurations: Boolean,
    cancelDownload: () -> Unit,
) {
    if (!isChangingConfigurations) cancelDownload()
}
