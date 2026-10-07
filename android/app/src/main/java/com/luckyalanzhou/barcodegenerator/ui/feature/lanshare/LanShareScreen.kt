package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import com.luckyalanzhou.barcodegenerator.domain.LanShareFile

/** Stateless feature screen: renders state and reports user actions through callbacks. */
@Composable
internal fun LanShareScreen(
    lanState: LanShareContentState,
    dark: Boolean,
    clearInputGeneration: Int,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFiles: () -> Unit,
    onSaveFile: (LanShareFile) -> Unit,
    onCopyAddress: (String) -> Unit,
    onSetQrVisible: (Boolean) -> Unit,
    onCancelUpload: (String) -> Unit,
    onLoadImagePreview: suspend (LanShareFile) -> Bitmap?,
    onLoadFullImagePreview: suspend (LanShareFile) -> Bitmap?,
    onSend: (String) -> Unit,
) {
    var message by remember { mutableStateOf("") }
    LaunchedEffect(clearInputGeneration) {
        if (clearInputGeneration > 0) message = ""
    }
    LanShareContent(
        lanState = lanState,
        message = message,
        dark = dark,
        onMessageChange = { message = it },
        onSetQrVisible = onSetQrVisible,
        onCancelUpload = onCancelUpload,
        onLoadImagePreview = onLoadImagePreview,
        onLoadFullImagePreview = onLoadFullImagePreview,
        onSend = onSend,
        onOpenCamera = onOpenCamera,
        onOpenGallery = onOpenGallery,
        onOpenFiles = onOpenFiles,
        onSaveFile = onSaveFile,
        onCopyAddress = onCopyAddress,
    )
}
