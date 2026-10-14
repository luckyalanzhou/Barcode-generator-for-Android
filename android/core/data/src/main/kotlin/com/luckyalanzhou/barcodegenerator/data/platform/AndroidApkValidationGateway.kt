package com.luckyalanzhou.barcodegenerator.data.platform

import android.content.Context
import android.content.pm.PackageManager
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import java.io.File

/** Android package/signature adapter for update validation. */
class AndroidApkValidationGateway(
    private val context: Context,
    private val currentVersionCode: Long,
) : ApkValidationGateway {
    override fun validate(file: File) {
        val packageManager = context.packageManager
        val signingFlags = if (android.os.Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags)
            ?: throw IllegalStateException("无法读取 APK 信息")
        if (info.packageName != context.packageName) throw IllegalStateException("APK 包名与当前应用不一致")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        if (versionCode <= currentVersionCode) throw IllegalStateException("APK 版本不是当前版本的更高版本")
        val downloaded = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val installedInfo = packageManager.getPackageInfo(context.packageName, signingFlags)
        val installed = if (android.os.Build.VERSION.SDK_INT >= 28) installedInfo.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION")
            installedInfo.signatures
        }
        if (downloaded.isNullOrEmpty() || installed.isNullOrEmpty() ||
            downloaded.map { it.toCharsString() }.toSet() != installed.map { it.toCharsString() }.toSet()
        ) throw IllegalStateException("APK 签名与当前应用不一致")
    }
}
