package com.luckyalanzhou.barcodegenerator.ui.feature.results.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luckyalanzhou.barcodegenerator.ui.dialogs.ComposeGlassDialogCard
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme

/** Stateless preview content; the application host supplies the image and media actions. */
@Composable
internal fun PreviewDialogContent(
    format: String,
    text: String,
    bitmap: Bitmap,
    dark: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    ComposeGlassDialogCard(dark) {
        Text(
            text = format,
            color = LocalAppColorScheme.current.text.primary,
            fontSize = 18.sp,
        )
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = LocalAppColorScheme.current.text.secondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "条码预览",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogAction("保存图片", dark, onSave)
            DialogAction("分享图片", dark, onShare, modifier = Modifier.padding(start = 20.dp))
            DialogAction("关闭", dark, onDismiss, modifier = Modifier.padding(start = 20.dp))
        }
    }
}
