package com.luckyalanzhou.barcodegenerator.ui.app.platform

import android.content.res.Resources
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.domain.UpdateFailure

internal fun Resources.updateFailureMessage(failure: UpdateFailure, httpStatus: Int? = null): String {
    if (failure == UpdateFailure.Http && httpStatus != null) {
        return getString(R.string.update_http_status, httpStatus)
    }
    val messageId = when (failure) {
        UpdateFailure.CheckFailed -> R.string.update_check_failed
        UpdateFailure.CatalogUnavailable -> R.string.update_catalog_unavailable
        UpdateFailure.InvalidCatalog -> R.string.update_catalog_invalid
        UpdateFailure.MissingHash -> R.string.update_hash_missing
        UpdateFailure.OversizedCatalog -> R.string.update_catalog_too_large
        UpdateFailure.Http -> R.string.update_http_error
        UpdateFailure.CacheUnavailable -> R.string.update_cache_unavailable
        UpdateFailure.OversizedApk -> R.string.update_apk_too_large
        UpdateFailure.UnsafeDownloadAddress -> R.string.update_download_address_unsafe
        UpdateFailure.InvalidRedirect -> R.string.update_redirect_invalid
        UpdateFailure.RedirectLimit -> R.string.update_redirect_limit
        UpdateFailure.EmptyApk -> R.string.update_apk_empty
        UpdateFailure.SizeMismatch -> R.string.update_apk_size_mismatch
        UpdateFailure.HashMismatch -> R.string.update_apk_hash_mismatch
        UpdateFailure.SaveFailed -> R.string.update_save_failed
        UpdateFailure.InvalidApk -> R.string.apk_verification_failed
        UpdateFailure.PackageMismatch -> R.string.update_apk_package_mismatch
        UpdateFailure.VersionNotNewer -> R.string.update_apk_not_newer
        UpdateFailure.SignatureMismatch -> R.string.update_apk_signature_mismatch
        UpdateFailure.DownloadFailed -> R.string.update_download_failure_detail
    }
    return getString(messageId)
}
