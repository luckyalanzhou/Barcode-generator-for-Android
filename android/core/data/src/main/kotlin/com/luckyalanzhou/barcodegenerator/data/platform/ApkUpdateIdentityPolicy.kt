package com.luckyalanzhou.barcodegenerator.data.platform

import com.luckyalanzhou.barcodegenerator.domain.UpdateFailure
import com.luckyalanzhou.barcodegenerator.domain.UpdateValidationException

/** 校验待安装 APK 的包名、版本递增和签名证书集合。 */
internal fun validateApkUpdateIdentity(
    expectedPackageName: String,
    currentVersionCode: Long,
    apkPackageName: String?,
    apkVersionCode: Long,
    apkSigners: List<String>?,
    installedSigners: List<String>?,
) {
    if (apkPackageName != expectedPackageName) throw UpdateValidationException(UpdateFailure.PackageMismatch, "APK 包名与当前应用不一致")
    if (apkVersionCode <= currentVersionCode) throw UpdateValidationException(UpdateFailure.VersionNotNewer, "APK 版本不是当前版本的更高版本")
    if (apkSigners.isNullOrEmpty() || installedSigners.isNullOrEmpty() ||
        apkSigners.toSet() != installedSigners.toSet()
    ) throw UpdateValidationException(UpdateFailure.SignatureMismatch, "APK 签名与当前应用不一致")
}
