package com.luckyalanzhou.barcodegenerator.domain

import java.io.File

/** Platform capabilities consumed by presentation without depending on Android implementations. */
data class ImagePayload(val bytes: ByteArray)

interface BarcodeDecodeGateway {
    suspend fun decode(image: ImagePayload): String?
}

interface OcrTextGateway {
    suspend fun recognize(image: ImagePayload, confusionMask: Int): List<String>
}

interface ApkDownloadGateway {
    suspend fun download(
        apkUrl: String,
        expectedSize: Long?,
        expectedSha256: String?,
        onProgress: (progress: Int, indeterminate: Boolean, status: String) -> Unit,
    ): File
}

interface ApkValidationGateway {
    fun validate(file: File)
}

sealed interface UpdateLookupResult {
    data class Available(
        val version: String,
        val downloadUrl: String,
        val expectedSize: Long?,
        val expectedSha256: String?,
    ) : UpdateLookupResult

    data object UpToDate : UpdateLookupResult
    data class Failed(val reason: String) : UpdateLookupResult
}

interface UpdateCatalogGateway {
    suspend fun check(): UpdateLookupResult
}
