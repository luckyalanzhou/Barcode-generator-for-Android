package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionEdgeChrome
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.domain.LanShareSession
import com.luckyalanzhou.barcodegenerator.ui.component.menu.MenuSurface
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeLanShareQrDialog(
    session: LanShareSession,
    dark: Boolean,
    browserConnected: Boolean,
    primary: Color,
    secondary: Color,
    onDismiss: () -> Unit,
    onCopyAddress: (String) -> Unit,
    createQrBitmap: (String, Int, Int, Int) -> Bitmap,
) {
    val qrSize = 260.dp
    val colors = LocalAppColorScheme.current
    val foreground = colors.barcode.qrForeground.toArgb()
    val background = colors.barcode.qrBackground.toArgb()
    val bitmap = remember(session.shareUrl, dark) {
        createQrBitmap(session.shareUrl, foreground, background, qrSize.value.toInt())
    }
    val closeInteractions = remember { MutableInteractionSource() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetHeightPx = remember { mutableFloatStateOf(0f) }
    val sheetGlassMotion = remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sheetState) {
        var expandedOffset = Float.NaN
        snapshotFlow {
            if (sheetState.currentValue == SheetValue.Expanded) sheetState.requireOffset() else Float.NaN
        }.collect { offset ->
            if (!offset.isFinite()) {
                expandedOffset = Float.NaN
                sheetGlassMotion.value = Offset.Zero
            } else {
                if (!expandedOffset.isFinite()) expandedOffset = offset
                val height = sheetHeightPx.floatValue
                val drag = if (height > 0f) ((offset - expandedOffset) / height).coerceIn(-1f, 1f) else 0f
                sheetGlassMotion.value = Offset(0f, drag)
            }
        }
    }
    val closeSheet: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        // Keep the navigation-bar inset on the same surface tint; MenuSurface draws
        // the sampled glass over the sheet content itself.
        containerColor = colors.surfaces.surface,
        contentColor = primary,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        MenuSurface(
            modifier = Modifier.fillMaxWidth().onSizeChanged { sheetHeightPx.floatValue = it.height.toFloat() },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = colors.surfaces.surface,
            dynamicBackdrop = true,
            screenSpaceBackdrop = true,
            screenBackdropCaptureKey = session.shareUrl,
            waitForScreenBackdropFrame = true,
            externalGlassMotion = sheetGlassMotion,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BottomSheetDefaults.DragHandle(color = secondary.copy(alpha = 0.45f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.waiting_for_qr_scan), color = primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (browserConnected) stringResource(R.string.device_connected) else stringResource(R.string.waiting_for_device),
                            color = if (browserConnected) colors.controls.success else secondary,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(onClick = closeSheet, interactionSource = closeInteractions,
                        modifier = Modifier.iosPressFeedback(closeInteractions).resultActionEdgeChrome()) {
                        Text(stringResource(R.string.action_off), color = secondary)
                    }
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.lan_share_qr),
                    modifier = Modifier.padding(top = 12.dp).size(qrSize),
                )
                Text(
                    stringResource(R.string.lan_share_qr_instructions),
                    modifier = Modifier.padding(top = 12.dp),
                    color = secondary,
                    fontSize = 14.sp,
                )
                session.pairingCode?.let { code ->
                    Text(
                        stringResource(R.string.lan_share_pairing_code, code),
                        modifier = Modifier.padding(top = 8.dp),
                        color = primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                    )
                }
                LanShareSecurityNotice(
                    secondary = secondary,
                    modifier = Modifier.padding(top = 10.dp),
                )
                SelectionContainer(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text(
                        session.baseUrl,
                        color = primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1,
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                    DialogAction(stringResource(R.string.share_address_copy), dark, { onCopyAddress(session.shareUrl) }, primary = true)
                }
            }
        }
    }
}
