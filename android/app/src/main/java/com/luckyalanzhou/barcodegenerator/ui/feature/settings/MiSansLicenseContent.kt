package com.luckyalanzhou.barcodegenerator.ui.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.dialogs.DialogAction
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalAppColorScheme
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun MiSansLicenseContent(dark: Boolean, onBack: () -> Unit, onOpenOfficial: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val pages by produceState<List<Bitmap>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            try {
                renderMiSansLicense(context)
            } catch (_: java.io.IOException) {
                emptyList()
            } catch (_: SecurityException) {
                emptyList()
            }
        }
    }
    val loadedPages = pages
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondarySettingsHeader(stringResource(R.string.license_misans_agreement), onBack)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                loadedPages == null -> item {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                }
                loadedPages.isEmpty() -> item {
                    Text(stringResource(R.string.license_misans_read_failed), color = LocalAppColorScheme.current.text.primary)
                }
                else -> itemsIndexed(loadedPages) { index, bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.license_misans_page, index + 1, loadedPages.size),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                DialogAction(
                    text = stringResource(R.string.license_misans_official),
                    dark = dark,
                    onClick = onOpenOfficial,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

private suspend fun renderMiSansLicense(context: Context): List<Bitmap> {
    val file = File.createTempFile("misans-license-", ".pdf", context.cacheDir)
    try {
        context.resources.openRawResource(R.raw.misans_license).use { source ->
            file.outputStream().use { target -> source.copyTo(target) }
        }
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                return List(renderer.pageCount) { index ->
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(index).use { page ->
                        val width = 1080
                        val height = (width.toDouble() * page.height / page.width).roundToInt()
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                            eraseColor(android.graphics.Color.WHITE)
                            page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    } finally {
        file.delete()
    }
}
