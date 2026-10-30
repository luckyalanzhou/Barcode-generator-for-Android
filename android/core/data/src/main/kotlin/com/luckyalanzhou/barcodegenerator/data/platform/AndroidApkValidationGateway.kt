package com.luckyalanzhou.barcodegenerator.data.platform

import android.content.Context
import android.content.pm.PackageManager
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import java.io.File

/** Android APK 包名和签名验证适配器。 */
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
        // 错包应在查询当前安装包签名之前直接拒绝，保持原有短路顺序。
        if (info.packageName != context.packageName) throw IllegalStateException("APK 包名与当前应用不一致")
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        val downloaded = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val installedInfo = packageManager.getPackageInfo(context.packageName, signingFlags)
        val installed = if (android.os.Build.VERSION.SDK_INT >= 28) installedInfo.signingInfo?.apkContentsSigners else {
            @Suppress("DEPRECATION")
            installedInfo.signatures
        }
        validateApkUpdateIdentity(
            expectedPackageName = context.packageName,
            currentVersionCode = currentVersionCode,
            apkPackageName = info.packageName,
            apkVersionCode = versionCode,
            apkSigners = downloaded?.map { it.toCharsString() },
            installedSigners = installed?.map { it.toCharsString() },
        )
    }
}
