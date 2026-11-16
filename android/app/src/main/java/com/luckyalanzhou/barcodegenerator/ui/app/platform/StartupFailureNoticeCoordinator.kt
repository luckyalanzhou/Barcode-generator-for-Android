package com.luckyalanzhou.barcodegenerator.ui.app.platform

import com.luckyalanzhou.barcodegenerator.R

/** 汇总设置与收藏/历史的启动读取结果，避免提示被先完成的错误吞掉。 */
internal class StartupFailureNoticeCoordinator {
    private var settingsFailed: Boolean? = null
    private var libraryFailed: Boolean? = null
    private var noticeReported = false

    @Synchronized
    fun reportSettingsLoad(failed: Boolean): Int? {
        if (settingsFailed != null) return null
        settingsFailed = failed
        return createNoticeIfReady()
    }

    @Synchronized
    fun reportLibraryLoad(failed: Boolean): Int? {
        if (libraryFailed != null) return null
        libraryFailed = failed
        return createNoticeIfReady()
    }

    private fun createNoticeIfReady(): Int? {
        val settingsDidFail = settingsFailed ?: return null
        val libraryDidFail = libraryFailed ?: return null
        if (noticeReported) return null
        noticeReported = true

        return when {
            settingsDidFail && libraryDidFail ->
                R.string.startup_all_load_failed
            settingsDidFail -> R.string.startup_settings_load_failed
            libraryDidFail -> R.string.startup_library_load_failed
            else -> null
        }
    }
}
