package com.luckyalanzhou.barcodegenerator

import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Owns one-shot ActivityResult and camera output state without UI knowledge. */
class CameraRequestCoordinator {
    private val _state = MutableStateFlow(CameraCaptureState())
    val state: StateFlow<CameraCaptureState> = _state.asStateFlow()

    private var pendingExternalActivityRequest: Int? = null
    private var pendingPermissionRequest: Int? = null

    fun prepare(requestCode: Int) = _state.update { it.copy(requestCode = requestCode) }
    fun setOutput(uri: Uri?, file: File?) = _state.update { it.copy(outputUri = uri, outputFile = file) }
    fun markStarted(nowMillis: Long) = _state.update { it.copy(startedAtMillis = nowMillis) }

    fun clearOutput(): CameraCaptureState {
        val current = _state.value
        _state.update { it.copy(outputUri = null, outputFile = null, startedAtMillis = 0L) }
        return current
    }

    fun beginExternalActivity(requestCode: Int) { pendingExternalActivityRequest = requestCode }

    fun consumeExternalActivity(): Int = pendingExternalActivityRequest.also {
        pendingExternalActivityRequest = null
    } ?: 0

    fun beginPermission(requestCode: Int) { pendingPermissionRequest = requestCode }

    fun consumePermission(): Int = pendingPermissionRequest.also {
        pendingPermissionRequest = null
    } ?: 0
}
