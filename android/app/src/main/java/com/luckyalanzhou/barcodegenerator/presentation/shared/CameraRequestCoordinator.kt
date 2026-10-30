package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.presentation.*


import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class PermissionRequestResult(val requestCode: Int, val granted: Boolean)

/** 管理一次性 ActivityResult 请求和相机输出状态，不依赖界面实现。 */
class CameraRequestCoordinator(private val savedState: SavedStateHandle) {
    private val _state = MutableStateFlow(CameraCaptureState(
        requestCode = savedState[REQUEST_CODE] ?: 43,
        outputUri = savedState.get<String>(OUTPUT_URI)?.let(Uri::parse),
        outputFile = savedState.get<String>(OUTPUT_FILE)?.let(::File),
        startedAtMillis = savedState[STARTED_AT] ?: 0L,
    ))
    val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    private var pendingExternalActivityRequest: Int? = savedState[EXTERNAL_REQUEST]
    private var pendingPermissionRequest: Int? = savedState[PERMISSION_REQUEST]
    private var pendingPermissionNames: Array<String>? = savedState[PERMISSION_NAMES]

    fun prepare(requestCode: Int) {
        savedState[REQUEST_CODE] = requestCode
        _state.update { it.copy(requestCode = requestCode) }
    }

    fun setOutput(uri: Uri?, file: File?) {
        savedState[OUTPUT_URI] = uri?.toString()
        savedState[OUTPUT_FILE] = file?.absolutePath
        _state.update { it.copy(outputUri = uri, outputFile = file) }
    }

    fun markStarted(nowMillis: Long) {
        savedState[STARTED_AT] = nowMillis
        _state.update { it.copy(startedAtMillis = nowMillis) }
    }

    fun clearOutput(): CameraCaptureState {
        val current = _state.value
        savedState.remove<String>(OUTPUT_URI)
        savedState.remove<String>(OUTPUT_FILE)
        savedState.remove<Long>(STARTED_AT)
        _state.update { it.copy(outputUri = null, outputFile = null, startedAtMillis = 0L) }
        return current
    }

    fun beginExternalActivity(requestCode: Int): Boolean {
        if (pendingExternalActivityRequest != null) return false
        pendingExternalActivityRequest = requestCode
        savedState[EXTERNAL_REQUEST] = requestCode
        return true
    }

    fun hasPendingExternalActivity(): Boolean = pendingExternalActivityRequest != null

    fun consumeExternalActivity(): Int = pendingExternalActivityRequest.also {
        pendingExternalActivityRequest = null
        savedState.remove<Int>(EXTERNAL_REQUEST)
    } ?: 0

    internal fun beginPermission(
        requestCode: Int,
        permissions: Array<String>,
        cameraRequestCode: Int? = null,
    ): Boolean {
        if (pendingPermissionRequest != null) return false
        cameraRequestCode?.let(::prepare)
        pendingPermissionRequest = requestCode
        savedState[PERMISSION_REQUEST] = requestCode
        pendingPermissionNames = permissions.copyOf()
        savedState[PERMISSION_NAMES] = pendingPermissionNames
        return true
    }

    internal fun consumePermission(result: Map<String, Boolean>): PermissionRequestResult {
        val requestCode = pendingPermissionRequest ?: 0
        val granted = pendingPermissionNames
            ?.takeIf { it.isNotEmpty() }
            ?.all { result[it] == true }
            ?: false
        pendingPermissionRequest = null
        pendingPermissionNames = null
        savedState.remove<Int>(PERMISSION_REQUEST)
        savedState.remove<Array<String>>(PERMISSION_NAMES)
        return PermissionRequestResult(requestCode, granted)
    }

    internal fun cancelPermission() {
        pendingPermissionRequest = null
        pendingPermissionNames = null
        savedState.remove<Int>(PERMISSION_REQUEST)
        savedState.remove<Array<String>>(PERMISSION_NAMES)
    }

    private companion object {
        const val REQUEST_CODE = "camera.requestCode"
        const val OUTPUT_URI = "camera.outputUri"
        const val OUTPUT_FILE = "camera.outputFile"
        const val STARTED_AT = "camera.startedAt"
        const val EXTERNAL_REQUEST = "camera.externalRequest"
        const val PERMISSION_REQUEST = "camera.permissionRequest"
        const val PERMISSION_NAMES = "camera.permissionNames"
    }
}
