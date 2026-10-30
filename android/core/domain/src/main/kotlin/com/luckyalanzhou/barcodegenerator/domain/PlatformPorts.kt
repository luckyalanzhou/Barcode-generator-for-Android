package com.luckyalanzhou.barcodegenerator.domain

import java.io.File

/** 供 Presentation 层使用的平台能力，不依赖 Android 实现。 */
data class ImagePayload(val bytes: ByteArray)

interface BarcodeDecodeGateway {
    suspend fun decode(image: ImagePayload): String?
}

interface OcrTextGateway {
    suspend fun recognize(image: ImagePayload, confusionMask: Int): List<String>
}

interface ApkDownloadGateway {
    /** 除取消所属协程外，还会中断正在进行的阻塞读取。 */
    fun cancel() {}
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
