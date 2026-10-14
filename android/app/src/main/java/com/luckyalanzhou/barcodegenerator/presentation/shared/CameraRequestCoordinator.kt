package com.luckyalanzhou.barcodegenerator.presentation.shared

import com.luckyalanzhou.barcodegenerator.presentation.*

import com.luckyalanzhou.barcodegenerator.presentation.*

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Owns one-shot ActivityResult and camera output state without UI knowledge. */
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

    fun beginExternalActivity(requestCode: Int) {
        pendingExternalActivityRequest = requestCode
        savedState[EXTERNAL_REQUEST] = requestCode
    }

    fun consumeExternalActivity(): Int = pendingExternalActivityRequest.also {
        pendingExternalActivityRequest = null
        savedState.remove<Int>(EXTERNAL_REQUEST)
    } ?: 0

    fun beginPermission(requestCode: Int) {
        pendingPermissionRequest = requestCode
        savedState[PERMISSION_REQUEST] = requestCode
    }

    fun consumePermission(): Int = pendingPermissionRequest.also {
        pendingPermissionRequest = null
        savedState.remove<Int>(PERMISSION_REQUEST)
    } ?: 0

    private companion object {
        const val REQUEST_CODE = "camera.requestCode"
        const val OUTPUT_URI = "camera.outputUri"
        const val OUTPUT_FILE = "camera.outputFile"
        const val STARTED_AT = "camera.startedAt"
        const val EXTERNAL_REQUEST = "camera.externalRequest"
        const val PERMISSION_REQUEST = "camera.permissionRequest"
    }
}
