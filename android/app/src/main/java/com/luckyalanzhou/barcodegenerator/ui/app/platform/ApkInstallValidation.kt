package com.luckyalanzhou.barcodegenerator.ui.app.platform

import java.io.File
import java.io.IOException

/** 校验下载的 APK；拒绝的文件会清理，已通过校验的文件保留给系统安装器读取。 */
internal fun validateDownloadedApkForInstall(file: File, validate: (File) -> Unit) {
    try {
        validate(file)
    } catch (error: Throwable) {
        try {
            if (!file.delete() && file.exists()) {
                error.addSuppressed(IOException("无法删除未通过校验的 APK"))
            }
        } catch (cleanupError: Throwable) {
            if (cleanupError !== error) {
                if (cleanupError is Error && error !is Error) {
                    cleanupError.addSuppressed(error)
                    throw cleanupError
                }
                error.addSuppressed(cleanupError)
            }
        }
        throw error
    }
}
