package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareEvent
import com.luckyalanzhou.barcodegenerator.presentation.lanshare.LanShareViewModel
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile

/** Screen layer: owns the LAN Share ViewModel and translates effects into UI callbacks. */
@Composable
internal fun LanShareScreen(
    viewModel: LanShareViewModel,
    dark: Boolean,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFiles: () -> Unit,
    onSaveFile: (LanShareFile) -> Unit,
    onNotice: (String) -> Unit,
    onCopyAddress: (String) -> Unit,
) {
    val lanState by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf("") }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LanShareEvent.Error -> onNotice(event.message)
                is LanShareEvent.Notice -> {
                    if (event.clearInput) message = ""
                    onNotice(event.message)
                }
            }
        }
    }
    LanShareContent(
        lanState = lanState,
        message = message,
        dark = dark,
        onMessageChange = { message = it },
        onSetQrVisible = viewModel::setQrVisible,
        onSend = { text ->
            val state = viewModel.uiState.value
            if (state.pendingUploadUri != null) {
                viewModel.takePendingUpload()?.let { (uri, temporaryFile) ->
                    state.session?.let { session -> viewModel.uploadFile(session, uri, temporaryFile) }
                }
            } else text.takeIf { it.isNotBlank() }?.let { value ->
                state.session?.let { session -> viewModel.uploadText(session, value) }
            }
        },
        localFile = viewModel::localFile,
        onOpenCamera = onOpenCamera,
        onOpenGallery = onOpenGallery,
        onOpenFiles = onOpenFiles,
        onSaveFile = onSaveFile,
        onCopyAddress = onCopyAddress,
    )
}
