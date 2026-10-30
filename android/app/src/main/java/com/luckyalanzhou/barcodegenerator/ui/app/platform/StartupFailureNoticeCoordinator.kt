package com.luckyalanzhou.barcodegenerator.ui.app.platform

/** 汇总设置与收藏/历史的启动读取结果，避免提示被先完成的错误吞掉。 */
internal class StartupFailureNoticeCoordinator {
    private var settingsFailed: Boolean? = null
    private var libraryFailed: Boolean? = null
    private var noticeReported = false

    @Synchronized
    fun reportSettingsLoad(failed: Boolean): String? {
        if (settingsFailed != null) return null
        settingsFailed = failed
        return createNoticeIfReady()
    }

    @Synchronized
    fun reportLibraryLoad(failed: Boolean): String? {
        if (libraryFailed != null) return null
        libraryFailed = failed
        return createNoticeIfReady()
    }

    private fun createNoticeIfReady(): String? {
        val settingsDidFail = settingsFailed ?: return null
        val libraryDidFail = libraryFailed ?: return null
        if (noticeReported) return null
        noticeReported = true

        return when {
            settingsDidFail && libraryDidFail ->
                "设置、收藏和历史数据加载失败，已使用默认设置和默认内容启动"
            settingsDidFail -> "设置加载失败，已使用默认设置启动"
            libraryDidFail -> "收藏和历史数据加载失败，已使用默认内容启动"
            else -> null
        }
    }
}
