package com.luckyalanzhou.barcodegenerator.domain

enum class UpdateFailure {
    CheckFailed,
    CatalogUnavailable,
    InvalidCatalog,
    MissingHash,
    OversizedCatalog,
    Http,
    CacheUnavailable,
    OversizedApk,
    UnsafeDownloadAddress,
    InvalidRedirect,
    RedirectLimit,
    EmptyApk,
    SizeMismatch,
    HashMismatch,
    SaveFailed,
    InvalidApk,
    PackageMismatch,
    VersionNotNewer,
    SignatureMismatch,
    DownloadFailed,
}

interface UpdateFailureCause {
    val failure: UpdateFailure
}

class UpdateDownloadException(
    override val failure: UpdateFailure,
    diagnostic: String,
    cause: Throwable? = null,
) : IllegalArgumentException(diagnostic, cause), UpdateFailureCause

class UpdateValidationException(
    override val failure: UpdateFailure,
    diagnostic: String,
) : IllegalStateException(diagnostic), UpdateFailureCause
