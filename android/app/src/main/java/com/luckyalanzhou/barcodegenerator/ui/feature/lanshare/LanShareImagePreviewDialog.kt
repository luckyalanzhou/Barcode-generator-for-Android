package com.luckyalanzhou.barcodegenerator.ui.feature.lanshare

import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.luckyalanzhou.barcodegenerator.R
import com.luckyalanzhou.barcodegenerator.ui.component.iosPressFeedback
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.luckyalanzhou.barcodegenerator.ui.component.glass.resultActionEdgeChrome
import android.graphics.Bitmap
import android.os.Build
import android.view.Window
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import com.luckyalanzhou.barcodegenerator.icons.CloseSmallIcon
import com.luckyalanzhou.barcodegenerator.ui.component.glass.LocalLiquidGlassBackdrop
import com.luckyalanzhou.barcodegenerator.ui.component.glass.GlassPointerTracker
import com.luckyalanzhou.barcodegenerator.ui.component.glass.drawGlassTouchHighlight
import com.luckyalanzhou.barcodegenerator.ui.theme.LocalVisualEffectsPolicy
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/** 全屏图片预览：支持手势缩放/平移，点关闭按钮或系统返回键退出，不触发文件下载。 */
@Composable
internal fun LanShareImagePreviewDialog(
    fileName: String,
    bitmap: Bitmap,
    onDismiss: () -> Unit,
) {
    val localizedResources = LocalResources.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        val closeInteractions = remember { MutableInteractionSource() }
        val closeTouch = remember { GlassPointerTracker() }
        val closePressed by closeInteractions.collectIsPressedAsState()
        val effectsPolicy = LocalVisualEffectsPolicy.current
        val density = LocalDensity.current.density
        val closePress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (closePressed) 1f else 0f,
            animationSpec = if (effectsPolicy.reduceMotion) androidx.compose.animation.core.snap()
                else androidx.compose.animation.core.spring(dampingRatio = .72f, stiffness = 520f),
            label = "preview-close-glass-press",
        )
        val imageBackdrop = rememberLayerBackdrop()
        val useKyantGlass = Build.VERSION.SDK_INT >= 33 && !effectsPolicy.opaqueGlass
        // 高清图替换缩略图时保持手势状态和识别器，不打断已经开始的缩放。
        var transform by remember(fileName) { mutableStateOf(LanSharePreviewTransform()) }
        val currentBitmap by rememberUpdatedState(bitmap)
        val viewportSize = remember(fileName) { mutableStateOf(IntSize.Zero) }
        val gestureScope = rememberCoroutineScope()
        var zoomJob by remember(fileName) { mutableStateOf<Job?>(null) }
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        SideEffect {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            dialogWindow?.applyImagePreviewSystemBars()
        }
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides imageBackdrop) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp, top = topInset + 76.dp, bottom = bottomInset + 16.dp)
                    .onSizeChanged { viewportSize.value = it }
                    .then(if (useKyantGlass) Modifier.layerBackdrop(imageBackdrop) else Modifier)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .pointerInput(fileName) {
                            detectTapGestures(onDoubleTap = { point ->
                                zoomJob?.cancel()
                                val start = transform
                                val target = if (start.scale > 1.01f) 1f else 2.5f
                                zoomJob = gestureScope.launch {
                                    animate(start.scale, target, animationSpec =
                                        if (effectsPolicy.reduceMotion) androidx.compose.animation.core.snap()
                                        else spring(.85f, 500f)) { value, _ ->
                                        transform = transformLanSharePreview(start,
                                            IntSize(currentBitmap.width, currentBitmap.height), viewportSize.value,
                                            point, zoom = value / start.scale)
                                    }
                                }
                            })
                        }
                        .pointerInput(fileName) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                zoomJob?.cancel()
                                transform = transformLanSharePreview(transform,
                                    IntSize(currentBitmap.width, currentBitmap.height), viewportSize.value,
                                    centroid, pan, zoom)
                            }
                        }
                        .graphicsLayer {
                            scaleX = transform.scale
                            scaleY = transform.scale
                            translationX = transform.offset.x
                            translationY = transform.offset.y
                            transformOrigin = TransformOrigin.Center
                        },
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    .then(if (useKyantGlass) Modifier.drawBackdrop(
                        backdrop = imageBackdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(8f * density)
                            lens(12f * density, 24f * density)
                        },
                        highlight = { Highlight.Default.copy(alpha = .30f) },
                        shadow = { Shadow(alpha = .20f) },
                        onDrawSurface = { drawRect(Color.Black.copy(alpha = .60f)) },
                    ) else Modifier.background(
                        Color.Black.copy(alpha = if (effectsPolicy.opaqueGlass) 1f else .60f),
                        RoundedCornerShape(28.dp),
                    ))
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = fileName,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    onClick = onDismiss,
                    interactionSource = closeInteractions,
                    modifier = Modifier.size(48.dp).iosPressFeedback(closeInteractions)
                        .then(closeTouch.pointerModifier)
                        .then(if (useKyantGlass) Modifier else Modifier.resultActionEdgeChrome(
                            RoundedCornerShape(10.dp), rimBackground = Color.Black, rimAccent = Color.White,
                        )).then(if (useKyantGlass) Modifier.drawBackdrop(
                        backdrop = imageBackdrop,
                        shape = { RoundedCornerShape(10.dp) },
                        effects = {
                            vibrancy()
                            blur(6f * density)
                            // Lens height must stay within this control's 10dp corner radius.
                            lens(minOf(8f + 2f * closePress, 10f) * density, (18f + 8f * closePress) * density)
                        },
                        layerBlock = {
                            val scale = 1f + .025f * closePress
                            scaleX = scale
                            scaleY = scale
                        },
                        highlight = { Highlight.Default.copy(alpha = .24f + .48f * closePress) },
                        shadow = { Shadow(alpha = .12f + .20f * closePress) },
                        onDrawSurface = {
                            drawRect(Color.Black.copy(alpha = .58f + .06f * closePress))
                            drawGlassTouchHighlight(closeTouch.touchPosition, closePress, .16f)
                        },
                    ) else Modifier.background(
                        if (effectsPolicy.opaqueGlass) Color(0xFF303034) else Color.White.copy(alpha = .16f),
                        RoundedCornerShape(10.dp),
                    )).semantics {
                        contentDescription = localizedResources.getString(R.string.close_image_preview)
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = CloseSmallIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

private fun Window.applyImagePreviewSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowInsetsControllerCompat(this, decorView).apply {
        isAppearanceLightStatusBars = false
        isAppearanceLightNavigationBars = false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = false
    }
}
